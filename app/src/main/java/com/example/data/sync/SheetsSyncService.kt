package com.example.data.sync

import com.example.data.database.AppDatabase
import com.example.data.model.LabSettings
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OPTIONAL Google Sheets sync client. Completely inert unless the user enables it in
 * Settings and provides their own Google Apps Script Web App URL + secret (entered at
 * runtime, stored in the local database - NEVER compiled into the APK).
 *
 * Protocol (see docs/GOOGLE_SHEETS_SYNC_SETUP.md for the matching Apps Script):
 *   POST <syncUrl>  Content-Type: application/json
 *   {
 *     "secret": "...",
 *     "deletes": [{ "entityType": "work_order", "syncId": "..." }],
 *     "clinics": [ ... ], "patients": [ ... ], "workTypes": [ ... ],
 *     "clinicRates": [ ... ], "workOrders": [ ... ], "payments": [ ... ]
 *   }
 *   -> 200 { "status": "ok", "processed": N }
 *
 * The Apps Script upserts rows by the unique `syncId` column, so re-running a sync never
 * duplicates entries. Local data (Room) always remains the primary source of truth; the
 * app is fully usable offline with sync disabled.
 */
class SheetsSyncService(private val database: AppDatabase) {

    @JsonClass(generateAdapter = false)
    data class DeleteOp(val entityType: String, val syncId: String)

    @JsonClass(generateAdapter = false)
    data class SyncPayload(
        val secret: String,
        val deletes: List<DeleteOp>,
        val clinics: List<Any>,
        val patients: List<Any>,
        val workTypes: List<Any>,
        val clinicRates: List<Any>,
        val workOrders: List<Any>,
        val payments: List<Any>
    )

    @JsonClass(generateAdapter = false)
    data class SyncResponse(val status: String?, val processed: Int?, val message: String?)

    data class SyncResult(val success: Boolean, val message: String, val pushed: Int)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)
    private val responseAdapter = moshi.adapter(SyncResponse::class.java)

    /** Pushes all pending rows (pendingSync = 1) plus delete tombstones. */
    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        val settings: LabSettings = database.labSettingsDao().getSettingsDirect()
            ?: return@withContext SyncResult(false, "Lab settings not initialised yet", 0)
        if (!settings.syncEnabled) return@withContext SyncResult(false, "Sync is disabled in Settings", 0)
        if (settings.syncUrl.isBlank()) return@withContext SyncResult(false, "Sync URL is not configured", 0)

        val deletes = database.syncQueueDao().getAll().map { DeleteOp(it.entityType, it.entitySyncId) }
        val clinics = database.clinicDao().getPendingSyncClinics()
        val patients = database.patientDao().getPendingSyncPatients()
        val workTypes = database.workTypeDao().getPendingSyncWorkTypes()
        val clinicRates = database.clinicRateDao().getPendingSyncRates()
        val workOrders = database.workOrderDao().getPendingSyncWorkOrders()
        val payments = database.paymentDao().getPendingSyncPayments()

        val total = deletes.size + clinics.size + patients.size + workTypes.size +
            clinicRates.size + workOrders.size + payments.size
        if (total == 0) return@withContext SyncResult(true, "Everything already synced", 0)

        val payload = SyncPayload(
            secret = settings.syncSecret,
            deletes = deletes,
            clinics = clinics,
            patients = patients,
            workTypes = workTypes,
            clinicRates = clinicRates,
            workOrders = workOrders,
            payments = payments
        )

        try {
            val request = Request.Builder()
                .url(settings.syncUrl)
                .post(payloadAdapter.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SyncResult(false, "Server responded HTTP ${response.code}", 0)
                }
                val body = response.body?.string().orEmpty()
                val parsed = try { responseAdapter.fromJson(body) } catch (e: Exception) { null }
                if (parsed?.status == "ok") {
                    // Success: clear tombstones + pending flags, stamp lastSyncAt
                    database.syncQueueDao().clearAll()
                    database.clinicDao().clearPendingSyncFlag()
                    database.patientDao().clearPendingSyncFlag()
                    database.workTypeDao().clearPendingSyncFlag()
                    database.clinicRateDao().clearPendingSyncFlag()
                    database.workOrderDao().clearPendingSyncFlag()
                    database.paymentDao().clearPendingSyncFlag()
                    database.labSettingsDao().saveSettings(
                        settings.copy(lastSyncAt = System.currentTimeMillis())
                    )
                    SyncResult(true, "Synced $total change(s) to Google Sheets", total)
                } else {
                    SyncResult(false, parsed?.message ?: "Unexpected server response", 0)
                }
            }
        } catch (e: Exception) {
            SyncResult(false, "Network error: ${e.message ?: e.javaClass.simpleName}", 0)
        }
    }

    /** Forces a full re-push: marks every local row pending, then syncs. */
    suspend fun fullPush(): SyncResult = withContext(Dispatchers.IO) {
        val settings = database.labSettingsDao().getSettingsDirect()
            ?: return@withContext SyncResult(false, "Lab settings not initialised yet", 0)
        database.clinicDao().markAllPendingSync()
        database.patientDao().markAllPendingSync()
        database.workTypeDao().markAllPendingSync()
        database.clinicRateDao().markAllPendingSync()
        database.workOrderDao().markAllPendingSync()
        database.paymentDao().markAllPendingSync()
        syncNow()
    }
}
