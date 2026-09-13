package com.example.data.database

import com.example.data.model.Clinic
import com.example.data.model.ClinicRate
import com.example.data.model.Payment
import com.example.data.model.Patient
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.data.util.MoneyUtils
import java.util.Calendar
import java.util.Random
import java.util.UUID

/**
 * Deterministic demo/sample data generator. Uses a fixed seed so every reset produces the
 * same realistic dataset: 5 clinics, ~28 patients each, 55-65 work orders per clinic spread
 * across the last 8 months (always including several full months so monthly statements,
 * previous-balance carry-forward and dashboard totals can be verified), with a mix of
 * unit-based and fixed-price work, partial/full/unpaid balances and varied delivery statuses.
 */
object DemoDataGenerator {

    private const val SEED = 20260912L

    // Clinic templates (code, name, dentist, phone, email, address, city, notes)
    private val CLINIC_TEMPLATES = listOf(
        listOf(
            "CLN-101", "Apex Dental Care & Implant Center", "Dr. Sameer Khan",
            "+91 98201 11223", "apex.dental@gmail.com", "12 Hill Road, Bandra West", "Mumbai",
            "Prefers A2 shade default, delivers on Thursdays"
        ),
        listOf(
            "CLN-102", "Smile Craft Specialty Dental Clinic", "Dr. Neha Sharma",
            "+91 98202 33445", "drneha.smilecraft@gmail.com", "504 Orion Heights, Andheri East", "Mumbai",
            "High volume aesthetic cases"
        ),
        listOf(
            "CLN-103", "Dr. Deshmukh Multispecialty Dental", "Dr. Rajesh Deshmukh",
            "+91 98203 55667", "deshmukhdental@outlook.com", "3rd Floor, Laxmi Plaza, Thane West", "Thane",
            "Fixed rate agreement on complete dentures"
        ),
        listOf(
            "CLN-104", "Pearl32 Dental Studio", "Dr. Kavita Iyer",
            "+91 98204 77881", "contact@pearl32studio.in", "Shop 7, Sea Breeze, Powai", "Mumbai",
            "Implant-heavy workflow, wants weekly pickup"
        ),
        listOf(
            "CLN-105", "Sunrise Family Dentistry", "Dr. Arjun Nair",
            "+91 98205 99112", "arjun.sunrisedental@gmail.com", "21 MG Road, Vashi", "Navi Mumbai",
            "Family practice, mostly crown & bridge"
        )
    )

    private val FIRST_NAMES = listOf(
        "Amina", "Vikram", "Farida", "Rahul", "Priya", "Sanjay", "Kavita", "Imran", "Deepa",
        "Rohit", "Sneha", "Amit", "Meera", "Faisal", "Lakshmi", "Nitin", "Pooja", "Sameer",
        "Anita", "Vivek", "Ritu", "Karan", "Nisha", "Mohit", "Sadia", "Ganesh", "Divya",
        "Rakesh", "Zoya", "Manish", "Asha", "Tarun", "Rekha", "Naved", "Shruti", "Pranav"
    )

    private val LAST_NAMES = listOf(
        "Shaikh", "Malhotra", "Bano", "Patil", "Sharma", "Sen", "Iyer", "Qureshi", "Joshi",
        "Deshpande", "Kulkarni", "Bhat", "Naik", "Reddy", "Pillai", "Verma", "Kapoor",
        "Chopra", "Banerjee", "Menon", "Saxena", "Jain", "Shetty", "Agarwal", "Nair"
    )

    private val SHADES = listOf("A1", "A2", "A3", "A3.5", "B1", "B2", "C1", "Bleach BL1", "Bleach BL2")

    private val PAYMENT_METHODS = listOf("Cash", "UPI", "Bank Transfer", "Card", "Cheque")

    private val ORDER_NOTES = listOf(
        "High aesthetic translucency requested by doctor",
        "Check occlusion clearance on lower bite",
        "Patient sensitive to metal - use nickel free",
        "Gingival contour match with adjacent teeth",
        "Urgent case, doctor needs early delivery",
        "", "", ""
    )

    fun standardWorkTypes(): List<WorkType> = listOf(
        WorkType(name = "Zirconia Crown (Monolithic)", category = "Crown & Bridge", pricingModel = "UNIT_BASED", defaultRate = 2800.0, description = "High-strength monolithic zirconia crown with aesthetic glaze"),
        WorkType(name = "Zirconia Crown (Layered Aesthetic)", category = "Crown & Bridge", pricingModel = "UNIT_BASED", defaultRate = 3500.0, description = "Anterior layered zirconia crown for superior translucency"),
        WorkType(name = "PFM Crown (Nickel Free)", category = "Crown & Bridge", pricingModel = "UNIT_BASED", defaultRate = 1800.0, description = "Porcelain-fused-to-metal crown with ceramic margin"),
        WorkType(name = "E-Max CAD Crown", category = "Crown & Bridge", pricingModel = "UNIT_BASED", defaultRate = 3200.0, description = "Lithium disilicate all-ceramic restoration"),
        WorkType(name = "Full Metal Crown", category = "Crown & Bridge", pricingModel = "UNIT_BASED", defaultRate = 1500.0, description = "Cast metal alloy crown"),
        WorkType(name = "Implant Crown (Screw Retained)", category = "Implants", pricingModel = "UNIT_BASED", defaultRate = 5000.0, description = "Custom abutment and screw-retained zirconia crown"),
        WorkType(name = "Complete Denture (Lucitone 199)", category = "Dentures", pricingModel = "FIXED_PRICE", defaultRate = 14000.0, description = "High-impact heat-cured full arch complete denture"),
        WorkType(name = "Complete Denture (Standard Acrylic)", category = "Dentures", pricingModel = "FIXED_PRICE", defaultRate = 9500.0, description = "Standard upper or lower full denture"),
        WorkType(name = "Flexible Partial Denture (Valplast)", category = "Dentures", pricingModel = "FIXED_PRICE", defaultRate = 8000.0, description = "Tissue-borne thermoplastic flexible removable partial denture"),
        WorkType(name = "Cast Partial Denture (Cobalt-Chrome)", category = "Dentures", pricingModel = "FIXED_PRICE", defaultRate = 12000.0, description = "Metal frame removable partial denture"),
        WorkType(name = "Night Guard (Hard/Soft Dual)", category = "Orthodontics", pricingModel = "FIXED_PRICE", defaultRate = 2500.0, description = "Bruxism dual laminate thermoformed night splint"),
        WorkType(name = "Essix Clear Retainer", category = "Orthodontics", pricingModel = "FIXED_PRICE", defaultRate = 1800.0, description = "Vacuum formed clear post-orthodontic retainer arch"),
        WorkType(name = "Ceramic Veneer", category = "Cosmetic", pricingModel = "UNIT_BASED", defaultRate = 3800.0, description = "Ultra-thin custom porcelain laminate veneer"),
        WorkType(name = "Denture Repair / Tooth Addition", category = "Repairs", pricingModel = "FIXED_PRICE", defaultRate = 1200.0, description = "Cold cure acrylic repair with tooth replacement")
    )

    data class DemoData(
        val workTypes: List<WorkType>,
        val clinics: List<Clinic>,
        val clinicRates: List<ClinicRate>,
        val patients: List<Patient>,
        val workOrders: List<WorkOrder>,
        val payments: List<Payment>
    )

    fun generate(nowMillis: Long = System.currentTimeMillis()): DemoData {
        val rnd = Random(SEED)
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }

        // ---- Work types (explicit ids 1..14 so cross-references are stable) ----
        val workTypes = standardWorkTypes().mapIndexed { idx, wt -> wt.copy(id = (idx + 1).toLong()) }
        val unitBasedTypes = workTypes.filter { it.pricingModel == "UNIT_BASED" }
        val fixedTypes = workTypes.filter { it.pricingModel == "FIXED_PRICE" }

        // ---- Clinics (explicit ids 1..5) ----
        val clinics = CLINIC_TEMPLATES.mapIndexed { idx, t ->
            Clinic(
                id = (idx + 1).toLong(),
                clinicCode = t[0],
                name = t[1],
                dentistName = t[2].removePrefix("Dr. "),
                phone = t[3],
                whatsapp = t[3],
                email = t[4],
                address = t[5],
                city = t[6],
                notes = t[7],
                isActive = true,
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
        }

        // ---- Clinic-specific custom rates (subset of work types per clinic) ----
        val clinicRates = mutableListOf<ClinicRate>()
        // Apex: crowns cheaper (work types 1,3,4)
        clinicRates += rate(1, 1, "UNIT_BASED", 2700.0, nowMillis)
        clinicRates += rate(1, 4, "UNIT_BASED", 3100.0, nowMillis)
        // Smile Craft: premium aesthetic (1,2,13)
        clinicRates += rate(2, 1, "UNIT_BASED", 3000.0, nowMillis)
        clinicRates += rate(2, 2, "UNIT_BASED", 3700.0, nowMillis)
        clinicRates += rate(2, 13, "UNIT_BASED", 4200.0, nowMillis)
        // Deshmukh: denture fixed deals (7,8)
        clinicRates += rate(3, 7, "FIXED_PRICE", 13500.0, nowMillis)
        clinicRates += rate(3, 8, "FIXED_PRICE", 9000.0, nowMillis)
        // Pearl32: implant deal (6)
        clinicRates += rate(4, 6, "UNIT_BASED", 4600.0, nowMillis)
        clinicRates += rate(4, 3, "UNIT_BASED", 1750.0, nowMillis)
        // Sunrise: standard crown rates (1,3,5)
        clinicRates += rate(5, 1, "UNIT_BASED", 2600.0, nowMillis)
        clinicRates += rate(5, 3, "UNIT_BASED", 1700.0, nowMillis)
        clinicRates += rate(5, 5, "UNIT_BASED", 1400.0, nowMillis)

        // ---- Patients (28 per clinic, explicit sequential ids) ----
        val patients = mutableListOf<Patient>()
        var patientSeq = 201
        var patientId = 1L
        clinics.forEachIndexed { cIdx, clinic ->
            repeat(28) { i ->
                val first = FIRST_NAMES[(cIdx * 7 + i * 3 + rnd.nextInt(3)) % FIRST_NAMES.size]
                val last = LAST_NAMES[(cIdx * 5 + i * 2 + rnd.nextInt(2)) % LAST_NAMES.size]
                patients += Patient(
                    id = patientId++,
                    patientCode = "PAT-${patientSeq++}",
                    name = "$first $last",
                    phone = "+91 9${(100000000 + rnd.nextInt(899999999))}",
                    age = 18 + rnd.nextInt(60),
                    gender = if (rnd.nextInt(2) == 0) "Male" else "Female",
                    clinicId = (cIdx + 1).toLong(),
                    dentistName = clinic.dentistName,
                    notes = "",
                    createdAt = nowMillis,
                    updatedAt = nowMillis
                )
            }
        }

        // ---- Work orders: spread over the last 8 full months + current month ----
        // Month buckets relative to today: -8, -7, ..., -1, 0 (current). This guarantees
        // several complete months (e.g. June when running in September) for statements.
        val day = 24 * 60 * 60 * 1000L
        val ordersPerClinic = 55 + rnd.nextInt(11) // 55..65
        val workOrders = mutableListOf<WorkOrder>()
        var orderId = 1L
        var jobSeqByYear = mutableMapOf<Int, Int>()
        val upperTeeth = listOf(18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28)
        val lowerTeeth = listOf(48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38)

        clinics.forEachIndexed { cIdx, clinic ->
            val clinicId = (cIdx + 1).toLong()
            val clinicPatients = patients.filter { it.clinicId == clinicId }
            val clinicRateMap = clinicRates.filter { it.clinicId == clinicId }.associate { it.workTypeId to it }
            val count = ordersPerClinic + cIdx // slight variety per clinic
            repeat(count) {
                // Pick month bucket: weight recent months slightly higher
                val monthBack = rnd.nextInt(9) // 0..8
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.MONTH, -monthBack)
                    set(Calendar.DAY_OF_MONTH, 1 + rnd.nextInt(28))
                    set(Calendar.HOUR_OF_DAY, 9 + rnd.nextInt(9))
                    set(Calendar.MINUTE, rnd.nextInt(60))
                }
                val entryDate = cal.timeInMillis
                // If the generated date is in the future (current month), clamp to today
                val safeEntry = if (entryDate > nowMillis) nowMillis - rnd.nextInt(5) * day else entryDate

                // Work type: fixed-price work ~30% of cases
                val useFixed = rnd.nextInt(10) < 3
                val workType = if (useFixed) fixedTypes[rnd.nextInt(fixedTypes.size)] else unitBasedTypes[rnd.nextInt(unitBasedTypes.size)]

                // Rate: clinic-specific override when defined, else default
                val customRate = clinicRateMap[workType.id]
                val rate = customRate?.customRate ?: workType.defaultRate

                // Teeth + units
                val teethPool = if (rnd.nextInt(2) == 0) upperTeeth else lowerTeeth
                val teethCount = if (workType.pricingModel == "UNIT_BASED") 1 + rnd.nextInt(4) else 0
                val selectedTeeth = if (teethCount > 0) {
                    val start = rnd.nextInt(teethPool.size - teethCount + 1)
                    (start until start + teethCount).map { teethPool[it] }.sorted().joinToString(",")
                } else ""
                val units = if (workType.pricingModel == "UNIT_BASED") teethCount.coerceAtLeast(1) else 1
                val total = MoneyUtils.totalFor(units, rate, workType.pricingModel)

                // Delivery + status driven by age of the order
                val expectedDelivery = safeEntry + (3L + rnd.nextInt(8)) * day
                val ageDays = ((nowMillis - safeEntry) / day).toInt()
                val status = when {
                    rnd.nextInt(20) == 0 -> "Cancelled"
                    ageDays > 45 -> if (rnd.nextInt(10) < 9) "Delivered" else "Completed"
                    ageDays > 20 -> listOf("Delivered", "Completed", "Ready")[rnd.nextInt(3)]
                    ageDays > 7 -> listOf("Ready", "In Progress", "Completed")[rnd.nextInt(3)]
                    else -> listOf("Received", "Pending", "In Progress")[rnd.nextInt(3)]
                }
                val actualDelivery: Long? = if (status == "Delivered") expectedDelivery - rnd.nextInt(2) * day else null

                val patient = clinicPatients[rnd.nextInt(clinicPatients.size)]
                val entryCal = Calendar.getInstance().apply { timeInMillis = safeEntry }
                val year = entryCal.get(Calendar.YEAR)
                val seq = (jobSeqByYear[year] ?: 0) + 1
                jobSeqByYear[year] = seq

                workOrders += WorkOrder(
                    id = orderId++,
                    jobNumber = String.format("NDL-%d-%04d", year, seq),
                    entryDate = safeEntry,
                    expectedDeliveryDate = expectedDelivery,
                    actualDeliveryDate = actualDelivery,
                    clinicId = clinicId,
                    clinicName = clinic.name,
                    dentistName = clinic.dentistName,
                    patientId = patient.id,
                    patientName = patient.name,
                    caseNumber = "CS-${800 + rnd.nextInt(199)}",
                    workTypeId = workType.id,
                    workTypeName = workType.name,
                    pricingModel = workType.pricingModel,
                    selectedTeeth = selectedTeeth,
                    shade = SHADES[rnd.nextInt(SHADES.size)],
                    material = if (workType.pricingModel == "FIXED_PRICE") "Lucitone 199 Acrylic" else "Multilayer Zirconia",
                    units = units,
                    rate = rate,
                    totalAmount = total,
                    status = status,
                    notes = ORDER_NOTES[rnd.nextInt(ORDER_NOTES.size)],
                    specialInstructions = "",
                    createdAt = safeEntry,
                    updatedAt = safeEntry
                )
            }
        }

        // ---- Monthly combined clinic-account payments ----
        // Clinics settle once or twice per month with a combined amount (never per case).
        // Per month per clinic: older months are mostly settled (60-100%), recent months
        // partially - this creates realistic previous outstanding balances.
        val payments = mutableListOf<Payment>()
        val cal = Calendar.getInstance()
        clinics.forEachIndexed { cIdx, clinic ->
            val clinicId = (cIdx + 1).toLong()
            // billable revenue per (year, monthKey)
            val revenueByMonth = workOrders
                .filter { it.clinicId == clinicId && it.status != "Cancelled" }
                .groupBy {
                    cal.timeInMillis = it.entryDate
                    Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                }
                .mapValues { (_, list) -> MoneyUtils.round2(list.sumOf { it.totalAmount }) }

            revenueByMonth.entries.sortedBy { it.key.first * 12 + it.key.second }.forEach { (yearMonth, revenue) ->
                val (year, month) = yearMonth
                val monthsAgo = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH) - (year * 12 + month)
                if (revenue <= 0.0) return@forEach

                // how much of this month gets settled
                val settleFraction = when {
                    monthsAgo >= 3 -> if (rnd.nextInt(10) < 8) 0.7 + rnd.nextInt(4) * 0.1 else 0.5  // 0.5..1.0
                    monthsAgo == 2 -> if (rnd.nextInt(10) < 6) 0.6 + rnd.nextInt(4) * 0.1 else 0.3
                    monthsAgo == 1 -> if (rnd.nextInt(10) < 5) 0.4 + rnd.nextInt(4) * 0.1 else 0.2
                    else -> if (rnd.nextInt(10) < 3) 0.2 + rnd.nextInt(3) * 0.1 else 0.0            // current month
                }
                if (settleFraction <= 0.0) return@forEach

                // 1-2 combined payments per month, dated within the month (or just after)
                val splits = if (rnd.nextInt(3) == 0) 2 else 1
                var remaining = MoneyUtils.round2(revenue * settleFraction)
                repeat(splits) { splitIdx ->
                    val amount = if (splitIdx == splits - 1) remaining
                    else MoneyUtils.round2(remaining * (0.4 + rnd.nextInt(4) * 0.1)).coerceAtMost(remaining)
                    if (amount > 0.0) {
                        val payCal = Calendar.getInstance().apply {
                            set(year, month, 1, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val maxDay = payCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        // settle near month end (or early next month for recent bills)
                        payCal.set(Calendar.DAY_OF_MONTH, (maxDay * 3 / 4 + rnd.nextInt(maxDay / 4 + 1)).coerceAtMost(maxDay))
                        var payDate = payCal.timeInMillis
                        if (payDate > nowMillis) payDate = nowMillis - rnd.nextInt(4) * day
                        payments += Payment(
                            clinicId = clinicId,
                            amount = amount,
                            paymentDate = payDate,
                            paymentMethod = PAYMENT_METHODS[rnd.nextInt(PAYMENT_METHODS.size)],
                            referenceNumber = "TXN-${100000 + rnd.nextInt(899999)}",
                            notes = "Monthly settlement",
                            createdAt = payDate,
                            updatedAt = payDate
                        )
                        remaining = MoneyUtils.round2(remaining - amount)
                    }
                }
            }
        }

        return DemoData(
            workTypes = workTypes,
            clinics = clinics,
            clinicRates = clinicRates,
            patients = patients,
            workOrders = workOrders,
            payments = payments
        )
    }

    private fun rate(clinicId: Int, workTypeId: Int, model: String, customRate: Double, now: Long) =
        ClinicRate(
            clinicId = clinicId.toLong(),
            workTypeId = workTypeId.toLong(),
            pricingModel = model,
            customRate = customRate,
            updatedAt = now,
            syncId = UUID.randomUUID().toString(),
            pendingSync = true
        )
}
