package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ClinicDao
import com.example.data.dao.ClinicRateDao
import com.example.data.dao.LabSettingsDao
import com.example.data.dao.PatientDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.WarrantyCardDao
import com.example.data.dao.WorkOrderDao
import com.example.data.dao.WorkTypeDao
import com.example.data.model.Clinic
import com.example.data.model.ClinicRate
import com.example.data.model.LabSettings
import com.example.data.model.Patient
import com.example.data.model.Payment
import com.example.data.model.WarrantyCard
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.data.sync.SyncQueueDao
import com.example.data.sync.SyncQueueEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Clinic::class,
        Patient::class,
        WorkType::class,
        ClinicRate::class,
        WorkOrder::class,
        Payment::class,
        LabSettings::class,
        SyncQueueEntry::class,
        WarrantyCard::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clinicDao(): ClinicDao
    abstract fun patientDao(): PatientDao
    abstract fun workTypeDao(): WorkTypeDao
    abstract fun clinicRateDao(): ClinicRateDao
    abstract fun workOrderDao(): WorkOrderDao
    abstract fun paymentDao(): PaymentDao
    abstract fun labSettingsDao(): LabSettingsDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun warrantyCardDao(): WarrantyCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: adds sync-ready columns (syncId unique key, pendingSync flag, updatedAt
         * where missing) to all tables, sync configuration columns to lab_settings, and the
         * sync_queue outbox table. Additive only - no data loss.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // clinics / patients / work_orders / clinic_rates already have updatedAt
                listOf("clinics", "patients", "work_types", "clinic_rates", "work_orders", "payments").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `pendingSync` INTEGER NOT NULL DEFAULT 1")
                    // Backfill stable unique sync ids for pre-existing rows, then add the
                    // unique index Room expects from the entity definition
                    db.execSQL("UPDATE `$table` SET `syncId` = lower(hex(randomblob(16))) WHERE `syncId` = ''")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_syncId` ON `$table` (`syncId`)")
                }
                // updatedAt where it did not exist
                db.execSQL("ALTER TABLE `work_types` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `work_types` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")
                db.execSQL("ALTER TABLE `payments` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `payments` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")

                // lab_settings: sync configuration (offline-first: disabled by default)
                db.execSQL("ALTER TABLE `lab_settings` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `lab_settings` ADD COLUMN `syncEnabled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `lab_settings` ADD COLUMN `syncUrl` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `lab_settings` ADD COLUMN `syncSecret` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `lab_settings` ADD COLUMN `lastSyncAt` INTEGER NOT NULL DEFAULT 0")

                // Sync outbox table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entitySyncId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_queue_entityType_entitySyncId` ON `sync_queue` (`entityType`, `entitySyncId`)"
                )
            }
        }

        /**
         * v2 -> v3: switches to the clinic-account payment model.
         *  - payments become clinic-level (drop workOrderId / patientId); existing receipts are
         *    preserved as clinic payments
         *  - work_orders drops the per-case paidAmount cache (clinics pay monthly at the
         *    account level; balances are computed per clinic, never per case)
         * Both changes require table rebuilds (SQLite cannot DROP COLUMN on old Androids).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ---- work_orders without paidAmount ----
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_work_orders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `jobNumber` TEXT NOT NULL,
                        `entryDate` INTEGER NOT NULL,
                        `expectedDeliveryDate` INTEGER NOT NULL,
                        `actualDeliveryDate` INTEGER,
                        `clinicId` INTEGER NOT NULL,
                        `clinicName` TEXT NOT NULL,
                        `dentistName` TEXT NOT NULL,
                        `patientId` INTEGER NOT NULL,
                        `patientName` TEXT NOT NULL,
                        `caseNumber` TEXT NOT NULL,
                        `workTypeId` INTEGER NOT NULL,
                        `workTypeName` TEXT NOT NULL,
                        `pricingModel` TEXT NOT NULL,
                        `selectedTeeth` TEXT NOT NULL,
                        `shade` TEXT NOT NULL,
                        `material` TEXT NOT NULL,
                        `units` INTEGER NOT NULL,
                        `rate` REAL NOT NULL,
                        `totalAmount` REAL NOT NULL,
                        `status` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `specialInstructions` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `pendingSync` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `_new_work_orders` (`id`,`jobNumber`,`entryDate`,`expectedDeliveryDate`,`actualDeliveryDate`,
                        `clinicId`,`clinicName`,`dentistName`,`patientId`,`patientName`,`caseNumber`,`workTypeId`,`workTypeName`,
                        `pricingModel`,`selectedTeeth`,`shade`,`material`,`units`,`rate`,`totalAmount`,`status`,`notes`,
                        `specialInstructions`,`createdAt`,`updatedAt`,`syncId`,`pendingSync`)
                    SELECT `id`,`jobNumber`,`entryDate`,`expectedDeliveryDate`,`actualDeliveryDate`,
                        `clinicId`,`clinicName`,`dentistName`,`patientId`,`patientName`,`caseNumber`,`workTypeId`,`workTypeName`,
                        `pricingModel`,`selectedTeeth`,`shade`,`material`,`units`,`rate`,`totalAmount`,`status`,`notes`,
                        `specialInstructions`,`createdAt`,`updatedAt`,`syncId`,`pendingSync`
                    FROM `work_orders`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `work_orders`")
                db.execSQL("ALTER TABLE `_new_work_orders` RENAME TO `work_orders`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_orders_jobNumber` ON `work_orders` (`jobNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_orders_clinicId` ON `work_orders` (`clinicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_orders_status` ON `work_orders` (`status`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_orders_syncId` ON `work_orders` (`syncId`)")

                // ---- payments at clinic-account level ----
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `clinicId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `paymentDate` INTEGER NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `referenceNumber` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `pendingSync` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `_new_payments` (`id`,`clinicId`,`amount`,`paymentDate`,`paymentMethod`,`referenceNumber`,
                        `notes`,`createdAt`,`updatedAt`,`syncId`,`pendingSync`)
                    SELECT `id`,`clinicId`,`amount`,`paymentDate`,`paymentMethod`,`referenceNumber`,
                        `notes`,`createdAt`,`updatedAt`,`syncId`,`pendingSync`
                    FROM `payments`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `payments`")
                db.execSQL("ALTER TABLE `_new_payments` RENAME TO `payments`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_clinicId` ON `payments` (`clinicId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payments_syncId` ON `payments` (`syncId`)")
            }
        }

        /**
         * v3 -> v4: adds the warranty_cards table (patient warranty cards linked to work
         * orders). Purely additive.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `warranty_cards` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workOrderId` INTEGER NOT NULL,
                        `clinicId` INTEGER NOT NULL,
                        `patientId` INTEGER NOT NULL,
                        `labName` TEXT NOT NULL,
                        `labAddress` TEXT NOT NULL,
                        `labPhone` TEXT NOT NULL,
                        `patientName` TEXT NOT NULL,
                        `patientAddress` TEXT NOT NULL,
                        `patientPhone` TEXT NOT NULL,
                        `workType` TEXT NOT NULL,
                        `material` TEXT NOT NULL,
                        `shade` TEXT NOT NULL,
                        `toothNumbers` TEXT NOT NULL,
                        `consultantDoctor` TEXT NOT NULL,
                        `deliveryDate` INTEGER NOT NULL,
                        `warrantyYears` INTEGER NOT NULL,
                        `warrantyExpiryDate` INTEGER NOT NULL,
                        `cardNumber` TEXT NOT NULL,
                        `terms` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `pendingSync` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_warranty_cards_workOrderId` ON `warranty_cards` (`workOrderId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_warranty_cards_syncId` ON `warranty_cards` (`syncId`)")
            }
        }

        /**
         * v4 -> v5: warranty_cards.workOrderId becomes nullable so warranty cards can also
         * be created standalone (manually, without a linked work entry). Table rebuild.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_warranty_cards` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workOrderId` INTEGER,
                        `clinicId` INTEGER NOT NULL,
                        `patientId` INTEGER NOT NULL,
                        `labName` TEXT NOT NULL,
                        `labAddress` TEXT NOT NULL,
                        `labPhone` TEXT NOT NULL,
                        `patientName` TEXT NOT NULL,
                        `patientAddress` TEXT NOT NULL,
                        `patientPhone` TEXT NOT NULL,
                        `workType` TEXT NOT NULL,
                        `material` TEXT NOT NULL,
                        `shade` TEXT NOT NULL,
                        `toothNumbers` TEXT NOT NULL,
                        `consultantDoctor` TEXT NOT NULL,
                        `deliveryDate` INTEGER NOT NULL,
                        `warrantyYears` INTEGER NOT NULL,
                        `warrantyExpiryDate` INTEGER NOT NULL,
                        `cardNumber` TEXT NOT NULL,
                        `terms` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `pendingSync` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `_new_warranty_cards` (`id`,`workOrderId`,`clinicId`,`patientId`,`labName`,`labAddress`,
                        `labPhone`,`patientName`,`patientAddress`,`patientPhone`,`workType`,`material`,`shade`,
                        `toothNumbers`,`consultantDoctor`,`deliveryDate`,`warrantyYears`,`warrantyExpiryDate`,
                        `cardNumber`,`terms`,`createdAt`,`updatedAt`,`syncId`,`pendingSync`)
                    SELECT `id`,`workOrderId`,`clinicId`,`patientId`,`labName`,`labAddress`,
                        `labPhone`,`patientName`,`patientAddress`,`patientPhone`,`workType`,`material`,`shade`,
                        `toothNumbers`,`consultantDoctor`,`deliveryDate`,`warrantyYears`,`warrantyExpiryDate`,
                        `cardNumber`,`terms`,`createdAt`,`updatedAt`,`syncId`,`pendingSync`
                    FROM `warranty_cards`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `warranty_cards`")
                db.execSQL("ALTER TABLE `_new_warranty_cards` RENAME TO `warranty_cards`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_warranty_cards_workOrderId` ON `warranty_cards` (`workOrderId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_warranty_cards_syncId` ON `warranty_cards` (`syncId`)")
            }
        }

        /**
         * v5 -> v6: warranty_cards gains a clinicName snapshot column so the card keeps
         * the clinic name it was issued for (mirrors work_orders.clinicName). The old
         * default lab branding is also scrubbed from previously stored rows so it can
         * no longer surface in the UI or in generated PDFs after the rename. Additive
         * only - no other data is touched.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `warranty_cards` ADD COLUMN `clinicName` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `warranty_cards` SET `clinicName` = COALESCE(
                        (SELECT `clinics`.`name` FROM `clinics` WHERE `clinics`.`id` = `warranty_cards`.`clinicId`),
                        ''
                    )
                    """.trimIndent()
                )
                // Privacy: replace the previously seeded default lab name / email with the
                // generic public name. User-entered custom values are left untouched.
                db.execSQL("UPDATE `warranty_cards` SET `labName` = 'DENTAL LAB MANAGEMENT' WHERE `labName` = 'NAZNEEN DENTAL LAB'")
                db.execSQL("UPDATE `lab_settings` SET `labName` = 'DENTAL LAB MANAGEMENT' WHERE `labName` = 'NAZNEEN DENTAL LAB'")
                db.execSQL("UPDATE `lab_settings` SET `email` = '' WHERE `email` = 'nazneendentallab@gmail.com'")
            }
        }

        /**
         * v6 -> v7: warranty cards gain a workOrderNumber snapshot plus printed
         * care-instructions and internal notes columns (all additive, backfilled where
         * possible); lab_settings gains the configurable default warranty period.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `warranty_cards` ADD COLUMN `workOrderNumber` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `warranty_cards` SET `workOrderNumber` = COALESCE(
                        (SELECT `work_orders`.`jobNumber` FROM `work_orders` WHERE `work_orders`.`id` = `warranty_cards`.`workOrderId`),
                        ''
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `warranty_cards` ADD COLUMN `careInstructions` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `warranty_cards` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `lab_settings` ADD COLUMN `defaultWarrantyYears` INTEGER NOT NULL DEFAULT 10")
                // Give existing cards the default care recommendations so the redesigned
                // card back is complete after the upgrade.
                db.execSQL(
                    """
                    UPDATE `warranty_cards` SET `careInstructions` =
                        'Brush and floss daily around the restoration.' || CHAR(10) ||
                        'Avoid biting hard objects; use a night guard if you grind your teeth.' || CHAR(10) ||
                        'Visit your dentist every six months.'
                    WHERE `careInstructions` = ''
                    """.trimIndent()
                )
            }
        }

        /**
         * v7 -> v8: warranty cards keep the raw FDI tooth list (`selectedTeeth`) that
         * drives the four-quadrant diagram on the printed card. Additive only; older
         * rows fall back to parsing their human-readable `toothNumbers` text at render
         * time, so no SQL backfill is needed.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `warranty_cards` ADD COLUMN `selectedTeeth` TEXT NOT NULL DEFAULT ''")
            }
        }

        // Privacy: the database file no longer carries the old lab branding in its name.
        private const val DB_NAME = "dental_lab_management.db"
        private const val LEGACY_DB_NAME = "nazneen_dental_lab.db"

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // One-time migration of the legacy database file (data-preserving rename).
                // If the new file does not exist yet but the old one does, copy it (plus
                // its WAL/SHM sidecars) so existing local data survives the rename.
                val newDb = context.getDatabasePath(DB_NAME)
                if (!newDb.exists()) {
                    val oldDb = context.getDatabasePath(LEGACY_DB_NAME)
                    if (oldDb.exists()) {
                        listOf("", "-wal", "-shm").forEach { suffix ->
                            val src = java.io.File(oldDb.parentFile, LEGACY_DB_NAME + suffix)
                            if (src.exists()) {
                                src.copyTo(java.io.File(newDb.parentFile, DB_NAME + suffix), overwrite = false)
                            }
                        }
                    }
                }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        seedDemoData(database)
                    }
                }
            }
        }

        /**
         * Seeds the full demo dataset (5 clinics, ~140 patients, ~300 work orders across the
         * last 8 months with payments and mixed statuses). Used on first launch and by the
         * "Reset Demo Data" action. Callers wrap this in a transaction-friendly context.
         */
        suspend fun seedDemoData(db: AppDatabase) {
            val demo = DemoDataGenerator.generate()
            db.labSettingsDao().saveSettings(LabSettings())
            db.workTypeDao().insertAllWorkTypes(demo.workTypes)
            db.clinicDao().insertAllClinics(demo.clinics)
            db.clinicRateDao().insertAllRates(demo.clinicRates)
            db.patientDao().insertAllPatients(demo.patients)
            db.workOrderDao().insertAllWorkOrders(demo.workOrders)
            db.paymentDao().insertAllPayments(demo.payments)
        }
    }
}
