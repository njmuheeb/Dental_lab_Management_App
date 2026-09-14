package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "clinics",
    indices = [Index(value = ["clinicCode"], unique = true), Index(value = ["syncId"], unique = true)]
)
data class Clinic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clinicCode: String,
    val name: String,
    val dentistName: String,
    val phone: String = "",
    val whatsapp: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Sync-ready fields (offline-first: only used when optional Google Sheets sync is enabled)
    val syncId: String = UUID.randomUUID().toString(),
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "patients",
    indices = [Index(value = ["patientCode"]), Index(value = ["syncId"], unique = true)]
)
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientCode: String,
    val name: String,
    val phone: String = "",
    val age: Int? = null,
    val gender: String = "Not Specified",
    val clinicId: Long,
    val dentistName: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "work_types",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class WorkType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String, // Crown & Bridge, Dentures, Implants, Orthodontics, Repairs, Custom
    val description: String = "",
    val pricingModel: String = "UNIT_BASED", // "UNIT_BASED" or "FIXED_PRICE"
    val defaultRate: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "clinic_rates",
    indices = [Index(value = ["clinicId", "workTypeId"], unique = true), Index(value = ["syncId"], unique = true)]
)
data class ClinicRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clinicId: Long,
    val workTypeId: Long,
    val pricingModel: String = "UNIT_BASED",
    val customRate: Double,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "work_orders",
    indices = [
        Index(value = ["jobNumber"], unique = true),
        Index(value = ["clinicId"]),
        Index(value = ["status"]),
        Index(value = ["syncId"], unique = true)
    ]
)
data class WorkOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobNumber: String, // e.g. "NDL-2026-0001"
    val entryDate: Long = System.currentTimeMillis(),
    val expectedDeliveryDate: Long = System.currentTimeMillis() + (5L * 24 * 60 * 60 * 1000),
    val actualDeliveryDate: Long? = null,
    val clinicId: Long,
    val clinicName: String,
    val dentistName: String = "",
    val patientId: Long,
    val patientName: String,
    val caseNumber: String = "",
    val workTypeId: Long,
    val workTypeName: String,
    val pricingModel: String, // Snapshot at order creation ("UNIT_BASED" or "FIXED_PRICE")
    val selectedTeeth: String = "", // Comma-separated FDI tooth numbers e.g. "11,12,21"
    val shade: String = "A2",
    val material: String = "Multilayer Zirconia",
    val units: Int = 1, // When UNIT_BASED: count. When FIXED_PRICE: 1 (not used for calculation)
    val rate: Double, // Historical snapshot rate
    val totalAmount: Double, // Calculated: Unit-based -> units * rate, Fixed -> rate
    // NOTE: no case-wise paid amount. Clinics pay monthly at the ACCOUNT level (see Payment);
    // outstanding balances are computed per clinic, never per individual work entry.
    val status: String = "Received", // "Received", "Pending", "In Progress", "Ready", "Completed", "Delivered", "Cancelled"
    val notes: String = "",
    val specialInstructions: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val pendingSync: Boolean = true
)

/**
 * Clinic-account level payment. Clinics settle monthly with combined amounts, NOT per case.
 * A payment is never tied to an individual work order, so combined payments are never
 * (incorrectly) distributed across individual cases. Outstanding balance per clinic =
 * (billed work) - (sum of these payments).
 */
@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["clinicId"]),
        Index(value = ["syncId"], unique = true)
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clinicId: Long,
    val amount: Double,
    val paymentDate: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Cash", // "Cash", "UPI", "Bank Transfer", "Card", "Cheque", "Other"
    val referenceNumber: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val pendingSync: Boolean = true
)

/**
 * Patient warranty card for a specific work order (e.g. "Zirconia Crown - 10 Years
 * Warranty"), or a standalone manually-entered card (workOrderId = null). The card is
 * editable and printable front/back as a CR80-style (Aadhaar-like, 85.6 x 54 mm) PDF.
 */
@Entity(
    tableName = "warranty_cards",
    indices = [
        Index(value = ["workOrderId"], unique = true),
        Index(value = ["syncId"], unique = true)
    ]
)
data class WarrantyCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workOrderId: Long? = null, // null = standalone manually-created card
    val workOrderNumber: String = "", // snapshot of work_orders.jobNumber ("" for standalone cards)
    val clinicId: Long,
    val clinicName: String = "",   // snapshot of the clinic name at card creation
    val patientId: Long,
    val labName: String,
    val labAddress: String = "",
    val labPhone: String = "",
    val patientName: String,
    val patientAddress: String = "",
    val patientPhone: String = "",
    val workType: String = "",
    val material: String = "",
    val shade: String = "",
    val selectedTeeth: String = "",    // raw FDI list e.g. "12,13,21,47" (drives the quadrant diagram)
    val toothNumbers: String = "",   // quadrant notation e.g. "UR: 1, 2 | LL: 6"
    val consultantDoctor: String = "",
    val deliveryDate: Long = System.currentTimeMillis(),
    val warrantyYears: Int = 10,
    val warrantyExpiryDate: Long = 0L, // calculated from deliveryDate + warrantyYears on save
    val cardNumber: String = "",
    val terms: String = "",
    val careInstructions: String = "", // printed on the card back ("care recommendations")
    val notes: String = "",            // internal note, kept on record but NOT printed on the card
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val pendingSync: Boolean = true
)

@Entity(tableName = "lab_settings")
data class LabSettings(
    @PrimaryKey val id: Int = 1,
    val labName: String = "DENTAL LAB MANAGEMENT",
    val phone: String = "+91 98765 43210",
    val whatsapp: String = "+91 98765 43210",
    val email: String = "",
    val address: String = "Building 4, Healthcare Complex, S.V. Road",
    val city: String = "Mumbai, Maharashtra",
    val currencySymbol: String = "₹",
    val defaultTurnaroundDays: Int = 5,
    val defaultWarrantyYears: Int = 10,
    val defaultPaymentMethod: String = "UPI",
    val themeMode: String = "System",
    val updatedAt: Long = System.currentTimeMillis(),
    // Optional Google Sheets sync configuration (user-entered at runtime; never compiled into the APK)
    val syncEnabled: Boolean = false,
    val syncUrl: String = "",
    val syncSecret: String = "",
    val lastSyncAt: Long = 0
)
