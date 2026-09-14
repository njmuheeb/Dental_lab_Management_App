package com.example

import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.model.WarrantyCard
import com.example.data.repository.DentalLabRepository
import com.example.data.util.ToothFormat
import com.example.export.WarrantyCardPdfExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layout-level verification of the warranty card PDF. The exporter computes its full
 * geometry through [WarrantyCardPdfExporter.pageLayouts], which the PDF writer renders
 * verbatim, so these tests prove the invariants of the exported document without a
 * device (PdfDocument itself cannot run under Robolectric - see WarrantyCardTest):
 *
 *  - EXACTLY two pages: page 1 = front, page 2 = back
 *  - pages are CR80/ID-1 card size (85.60 x 53.98 mm = 243 x 153 pt), never A4/Letter
 *  - the four-quadrant tooth diagram: vertical + horizontal cross through the exact
 *    center, FDI teeth shown as single digits 1-8 in the CORRECT quadrant (patient
 *    front view: UR top-left, UL top-right, LR bottom-left, LL bottom-right), empty
 *    quadrants stay empty
 *  - every text run, box, outline, line and tooth glyph is fully inside its page
 *  - all required front-side fields are rendered
 *  - the back side shows the COMPLETE terms, care recommendations, warranty period
 *    and the policy disclaimer (no dropped lines)
 *  - long names/addresses/words wrap safely instead of being cut off
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WarrantyCardPdfLayoutTest {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private fun card(
        patientName: String = "Amina Shaikh",
        patientAddress: String = "12 Hill Road, Bandra West, Mumbai",
        patientPhone: String = "+91 91111 11111",
        selectedTeeth: String = "12,13,14,21,47",
        toothNumbers: String = ToothFormat.formatLong(selectedTeeth),
        consultantDoctor: String = "Dr. Sameer Khan",
        workType: String = "Zirconia Crown (Monolithic)",
        material: String = "Multilayer Zirconia",
        shade: String = "A2",
        warrantyYears: Int = 10,
        terms: String = DentalLabRepository.DEFAULT_WARRANTY_TERMS,
        careInstructions: String = DentalLabRepository.DEFAULT_CARE_INSTRUCTIONS,
        labName: String = "Dental Lab Management",
        labAddress: String = "Building 4, Healthcare Complex, S.V. Road, Mumbai",
        labPhone: String = "+91 98765 43210",
        cardNumber: String = "WC-2026-0001",
        workOrderNumber: String = "NDL-2026-0042"
    ): WarrantyCard = WarrantyCard(
        workOrderId = 1L,
        workOrderNumber = workOrderNumber,
        clinicId = 1L,
        clinicName = "Apex Dental",
        patientId = 1L,
        labName = labName,
        labAddress = labAddress,
        labPhone = labPhone,
        patientName = patientName,
        patientAddress = patientAddress,
        patientPhone = patientPhone,
        workType = workType,
        material = material,
        shade = shade,
        selectedTeeth = selectedTeeth,
        toothNumbers = toothNumbers,
        consultantDoctor = consultantDoctor,
        deliveryDate = 1_760_000_000_000L,
        warrantyYears = warrantyYears,
        warrantyExpiryDate = DentalLabRepository.warrantyExpiry(1_760_000_000_000L, warrantyYears),
        cardNumber = cardNumber,
        terms = terms,
        careInstructions = careInstructions
    )

    private fun measure(text: String, size: Float, bold: Boolean): Float {
        val p = Paint().apply {
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.DEFAULT
        }
        return p.measureText(text)
    }

    private fun paintFor(size: Float, bold: Boolean): Paint = Paint().apply {
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.DEFAULT
    }

    /** Asserts every shape of the page lies fully inside the page bounds. */
    private fun assertAllShapesInsidePage(page: WarrantyCardPdfExporter.CardPage, pageName: String) {
        assertTrue("$pageName has no text", page.texts.isNotEmpty())
        page.texts.forEach { t ->
            val w = measure(t.text, t.size, t.bold)
            val p = paintFor(t.size, t.bold)
            val fm = p.fontMetrics
            assertTrue(
                "$pageName text '${t.text}' starts before the left edge (x=${t.x})",
                t.x >= -0.01f
            )
            assertTrue(
                "$pageName text '${t.text}' overflows the right edge (x=${t.x}, width=$w, page=${page.width})",
                t.x + w <= page.width + 0.5f
            )
            assertTrue(
                "$pageName text '${t.text}' is cut off at the top (top=${t.y + fm.ascent})",
                t.y + fm.ascent >= -0.01f
            )
            assertTrue(
                "$pageName text '${t.text}' is cut off at the bottom (bottom=${t.y + fm.descent}, page height=${page.height})",
                t.y + fm.descent <= page.height + 0.01f
            )
        }
        page.boxes.forEach { b ->
            assertTrue("$pageName box outside page: $b", b.left >= -0.01f && b.right <= page.width + 0.01f)
            assertTrue("$pageName box outside page: $b", b.top >= -0.01f && b.bottom <= page.height + 0.01f)
        }
        page.outlines.forEach { o ->
            assertTrue("$pageName outline outside page: $o", o.left >= -0.01f && o.right <= page.width + 0.01f)
            assertTrue("$pageName outline outside page: $o", o.top >= -0.01f && o.bottom <= page.height + 0.01f)
        }
        page.lines.forEach { l ->
            assertTrue("$pageName line outside page: $l", l.x1 >= -0.01f && l.x2 <= page.width + 0.01f)
            assertTrue("$pageName line outside page: $l", l.y1 >= -0.01f && l.y2 <= page.height + 0.01f)
        }
        page.tooths.forEach { t ->
            assertTrue("$pageName tooth outside page: $t", t.cx - t.width / 2f >= -0.01f && t.cx + t.width / 2f <= page.width + 0.01f)
            assertTrue("$pageName tooth outside page: $t", t.cy - t.height / 2f >= -0.01f && t.cy + t.height / 2f <= page.height + 0.01f)
        }
    }

    private fun frontText(c: WarrantyCard) = WarrantyCardPdfExporter.frontLayout(c)
        .texts.joinToString(" ") { it.text }

    private fun backText(c: WarrantyCard) = WarrantyCardPdfExporter.backLayout(c)
        .texts.joinToString(" ") { it.text }

    /** Digit texts (7.5pt bold) located inside a rectangle of the tooth diagram. */
    private fun digitsIn(
        page: WarrantyCardPdfExporter.CardPage,
        left: Float, top: Float, right: Float, bottom: Float
    ): List<String> =
        page.texts
            .filter { it.size == 7.5f && it.bold && it.x >= left && it.x < right && it.y >= top && it.y < bottom }
            .map { it.text }

    // ------------------------------------------------------------ pages

    @Test
    fun `pdf has exactly two pages - front then back`() {
        val pages = WarrantyCardPdfExporter.pageLayouts(card())
        assertEquals(2, pages.size)
        assertEquals(WarrantyCardPdfExporter.PAGE_COUNT, pages.size)
        // Front carries the tooth diagram + warranty strip; back carries the terms
        assertTrue(pages[0].texts.any { it.text == "TOOTH NUMBER(S)" })
        assertTrue(pages[0].texts.any { it.text == "WARRANTY PERIOD: 10 YEARS" })
        assertTrue(pages[1].texts.any { it.text == "WARRANTY TERMS & CONDITIONS" })
    }

    @Test
    fun `pages are CR80 card sized - never A4 or Letter`() {
        val pages = WarrantyCardPdfExporter.pageLayouts(card())
        pages.forEach { page ->
            assertEquals(WarrantyCardPdfExporter.CARD_WIDTH_PT, page.width, 0.01f)
            assertEquals(WarrantyCardPdfExporter.CARD_HEIGHT_PT, page.height, 0.01f)
        }
        // CR80: 85.60mm x 53.98mm = 3.375in x 2.125in = 243 x 153 pt at 72dpi
        assertEquals(243f, WarrantyCardPdfExporter.CARD_WIDTH_PT, 0.01f)
        assertEquals(153f, WarrantyCardPdfExporter.CARD_HEIGHT_PT, 0.01f)
        // Explicitly NOT a document-size page
        assertFalse(WarrantyCardPdfExporter.CARD_WIDTH_PT == 595f) // A4 width
        assertFalse(WarrantyCardPdfExporter.CARD_HEIGHT_PT == 842f) // A4 height
    }

    // ------------------------------------------------------------ four-quadrant tooth diagram

    @Test
    fun `diagram maps FDI teeth to the correct quadrants - reference case`() {
        // Selected: 12,13,14 (Upper Right), 21 (Upper Left), 47 (Lower Right)
        val front = WarrantyCardPdfExporter.frontLayout(card())

        // All four quadrant labels are drawn
        listOf("UR", "UL", "LR", "LL").forEach { q ->
            assertTrue("missing quadrant label $q", front.texts.any { it.text == q && it.size == 3f })
        }

        // Diagram box: x 140..231, y 41..117 -> cells UR(140..185.5, 41..79),
        // UL(185.5..231, 41..79), LR(140..185.5, 79..117), LL(185.5..231, 79..117)
        assertEquals(listOf("2", "3", "4"), digitsIn(front, 139f, 40f, 186f, 79f)) // UR
        assertEquals(listOf("1"), digitsIn(front, 186f, 40f, 232f, 79f))           // UL
        assertEquals(listOf("7"), digitsIn(front, 139f, 79f, 186f, 117f))          // LR
        assertTrue("lower-left quadrant must stay empty", digitsIn(front, 186f, 79f, 232f, 117f).isEmpty()) // LL

        // The cross: one vertical line at the exact horizontal center of the diagram,
        // one horizontal line at the exact vertical center (they intersect at the center)
        val vertical = front.lines.first { it.x1 == it.x2 && kotlin.math.abs(it.x1 - 185.5f) < 0.01f }
        val horizontal = front.lines.first { it.y1 == it.y2 && kotlin.math.abs(it.y1 - 79f) < 0.01f }
        assertTrue(vertical.y1 < horizontal.y1 && vertical.y2 > horizontal.y1) // vertical spans the horizontal line
        assertTrue(horizontal.x1 < vertical.x1 && horizontal.x2 > vertical.x1) // horizontal spans the vertical line
    }

    @Test
    fun `a full quadrant wraps eight digits into two rows inside its cell`() {
        val front = WarrantyCardPdfExporter.frontLayout(card(selectedTeeth = (11..18).joinToString(",")))
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            digitsIn(front, 139f, 40f, 186f, 79f)
        )
        // Exactly two rows (baselines 62 and 73), both inside the UR cell
        val rows = front.texts
            .filter { it.size == 7.5f && it.bold && it.x < 186f && it.y > 41f && it.y < 79f }
            .map { it.y }
            .distinct()
            .sorted()
        assertEquals(listOf(62f, 73f), rows)
        assertAllShapesInsidePage(front, "Front")
    }

    @Test
    fun `legacy cards without stored FDI fall back to the quadrant text`() {
        val c = card(
            selectedTeeth = "",
            toothNumbers = "Upper Right: 2, 3, 4 | Upper Left: 1 | Lower Right: 7"
        )
        assertEquals("12,13,14,21,47", WarrantyCardPdfExporter.effectiveTeeth(c))
        val front = WarrantyCardPdfExporter.frontLayout(c)
        assertEquals(listOf("2", "3", "4"), digitsIn(front, 139f, 40f, 186f, 79f))
        assertEquals(listOf("1"), digitsIn(front, 186f, 40f, 232f, 79f))
        assertEquals(listOf("7"), digitsIn(front, 139f, 79f, 186f, 117f))
        assertTrue(digitsIn(front, 186f, 79f, 232f, 117f).isEmpty())
    }

    @Test
    fun `no teeth selected leaves all quadrants empty`() {
        val front = WarrantyCardPdfExporter.frontLayout(card(selectedTeeth = "", toothNumbers = ""))
        assertTrue(digitsIn(front, 139f, 40f, 186f, 79f).isEmpty())
        assertTrue(digitsIn(front, 186f, 40f, 232f, 79f).isEmpty())
        assertTrue(digitsIn(front, 139f, 79f, 186f, 117f).isEmpty())
        assertTrue(digitsIn(front, 186f, 79f, 232f, 117f).isEmpty())
        assertAllShapesInsidePage(front, "Front")
    }

    // ------------------------------------------------------------ bounds (no clipping / no missing text)

    @Test
    fun `all front shapes stay inside the card with typical data`() {
        assertAllShapesInsidePage(WarrantyCardPdfExporter.frontLayout(card()), "Front")
    }

    @Test
    fun `all back shapes stay inside the card with typical data`() {
        assertAllShapesInsidePage(WarrantyCardPdfExporter.backLayout(card()), "Back")
    }

    @Test
    fun `long patient name and address wrap and stay inside the card`() {
        val c = card(
            patientName = "Mohammed Abdul Rahman Qureshi Al-Balushi the Third Junior",
            patientAddress = "Flat 1407, Tower B, Skyline Residency Complex, Linking Road, Khar West, Mumbai 400052",
            consultantDoctor = "Dr. Verylongconsultantmiddlename Verylongfamilyname",
            selectedTeeth = "11,12,13,14,15,16,17,18,21,22,23,24,25,26,27,28,31,38,41,48"
        )
        val front = WarrantyCardPdfExporter.frontLayout(c)
        assertAllShapesInsidePage(front, "Front")
        // The name wraps over two lines rather than being cut off after a few chars
        val nameLines = front.texts.filter { it.size == 6.2f && it.bold }
        assertTrue("patient name should use up to 2 lines", nameLines.isNotEmpty())
    }

    @Test
    fun `a single unbreakable long word never overflows the card width`() {
        val c = card(patientName = "A".repeat(120), patientAddress = "B".repeat(150))
        assertAllShapesInsidePage(WarrantyCardPdfExporter.frontLayout(c), "Front")
        assertAllShapesInsidePage(WarrantyCardPdfExporter.backLayout(c), "Back")
    }

    @Test
    fun `missing optional fields keep the layout valid`() {
        val c = card(
            patientAddress = "",
            patientPhone = "",
            selectedTeeth = "",
            toothNumbers = "",
            consultantDoctor = "",
            shade = "",
            material = "",
            workOrderNumber = "",
            labAddress = "",
            labPhone = "",
            careInstructions = ""
        )
        val front = WarrantyCardPdfExporter.frontLayout(c)
        assertAllShapesInsidePage(front, "Front")
        assertAllShapesInsidePage(WarrantyCardPdfExporter.backLayout(c), "Back")
        // Blank fields render as an explicit dash, never empty holes
        assertTrue(front.texts.any { it.text == "-" })
    }

    @Test
    fun `different warranty durations render with correct labels`() {
        listOf(1, 2, 3, 5, 7, 10, 15, 20).forEach { years ->
            val c = card(warrantyYears = years)
            val expected = if (years == 1) "WARRANTY PERIOD: 1 YEAR" else "WARRANTY PERIOD: $years YEARS"
            assertTrue("front strip missing for $years years", frontText(c).contains(expected))
            assertTrue("back strip missing for $years years", backText(c).contains(expected))
            assertAllShapesInsidePage(WarrantyCardPdfExporter.frontLayout(c), "Front")
            assertAllShapesInsidePage(WarrantyCardPdfExporter.backLayout(c), "Back")
        }
    }

    // ------------------------------------------------------------ front content completeness

    @Test
    fun `front shows every required field`() {
        val c = card()
        val text = frontText(c)
        // Branding header
        assertTrue(text.contains("Dental Lab Management"))           // lab/application title
        assertTrue(text.contains(WarrantyCardPdfExporter.TAGLINE))   // subtitle
        assertTrue(text.contains("WARRANTY CARD"))
        assertTrue(text.contains("WC-2026-0001"))                    // card number
        // Information fields (left column)
        assertTrue(text.contains("DATE"))
        assertTrue(text.contains(dateFmt.format(Date(c.deliveryDate))))
        assertTrue(text.contains("CASE NO."))
        assertTrue(text.contains("NDL-2026-0042"))
        assertTrue(text.contains("PATIENT NAME"))
        assertTrue(text.contains(c.patientName))
        assertTrue(text.contains("ADDRESS"))
        assertTrue(text.contains("12 Hill Road"))                    // wrapped address
        assertTrue(text.contains("CONTACT NO."))
        assertTrue(text.contains(c.patientPhone))
        assertTrue(text.contains("DENTIST NAME"))
        assertTrue(text.contains(c.consultantDoctor))
        assertTrue(text.contains("TYPE OF WORK"))
        assertTrue(text.contains(c.workType))
        assertTrue(text.contains(c.material))
        // Tooth diagram section + warranty strip
        assertTrue(text.contains("TOOTH NUMBER(S)"))
        assertTrue(text.contains("WARRANTY PERIOD: 10 YEARS"))
        assertTrue(
            text.contains(
                "Valid from ${dateFmt.format(Date(c.deliveryDate))} to ${dateFmt.format(Date(c.warrantyExpiryDate))}"
            )
        )
    }

    @Test
    fun `front keeps the phone visible even when the name needs two lines`() {
        val c = card(patientName = "Mohammed Abdul Rahman Qureshi Al-Balushi the Third Junior")
        val text = frontText(c)
        assertTrue(text.contains(c.patientPhone))
    }

    // ------------------------------------------------------------ back content completeness

    @Test
    fun `back shows header, warranty period, complete terms, care, contact and disclaimer`() {
        val c = card()
        val text = backText(c)
        assertTrue(text.contains("Dental Lab Management"))           // header brand
        assertTrue(text.contains("WC-2026-0001"))                    // card number
        assertTrue(text.contains("WARRANTY PERIOD: 10 YEARS"))
        assertTrue(
            text.contains(
                "Valid from ${dateFmt.format(Date(c.deliveryDate))} to ${dateFmt.format(Date(c.warrantyExpiryDate))}"
            )
        )
        assertTrue(text.contains("WARRANTY TERMS & CONDITIONS"))
        DentalLabRepository.DEFAULT_WARRANTY_TERMS.split('\n').forEach { line ->
            val normalized = line.trim()
            if (normalized.isNotEmpty()) {
                assertTrue("back is missing terms line: $normalized", text.contains(normalized))
            }
        }
        assertTrue(text.contains("CARE RECOMMENDATIONS"))
        DentalLabRepository.DEFAULT_CARE_INSTRUCTIONS.split('\n').forEach { line ->
            val normalized = line.trim()
            if (normalized.isNotEmpty()) {
                assertTrue("back is missing care line: $normalized", text.contains("• $normalized"))
            }
        }
        assertTrue(text.contains("Contact: ${c.labPhone}"))
        assertTrue(text.contains("laboratory's actual policy"))
        assertTrue(text.contains("professional dental advice"))
    }

    @Test
    fun `back uses the neutral support line when no lab contact is configured`() {
        val c = card(labPhone = "", labAddress = "")
        val text = backText(c)
        assertTrue(text.contains("For support, contact your prescribing dental clinic."))
        assertFalse(text.contains("Contact:"))
    }

    @Test
    fun `back auto-fits longer custom terms without dropping lines`() {
        // 9 numbered lines (~100 chars each) is a realistic long-terms case: the terms
        // area is ~42pt tall, so the block must SHRINK the font to fit all lines.
        val custom = (1..9).joinToString("\n") {
            "$it. Extended warranty condition line number $it describing additional coverage rules and exclusions."
        }
        val c = card(terms = custom)
        val text = backText(c)
        (1..9).forEach { n ->
            assertTrue(
                "back is missing custom terms line $n",
                text.contains("Extended warranty condition line number $n")
            )
        }
        assertAllShapesInsidePage(WarrantyCardPdfExporter.backLayout(c), "Back")
    }

    // ------------------------------------------------------------ tooth glyph

    @Test
    fun `tooth geometry control points stay inside the bounding box`() {
        val (start, segments) = WarrantyCardPdfExporter.toothGeometry(121.5f, 88f, 66f, 70f)
        val left = 121.5f - 33f
        val right = 121.5f + 33f
        val top = 88f - 35f
        val bottom = 88f + 35f
        val points = mutableListOf(start)
        segments.forEach { seg ->
            points.add(floatArrayOf(seg[0], seg[1]))
            points.add(floatArrayOf(seg[2], seg[3]))
            points.add(floatArrayOf(seg[4], seg[5]))
        }
        points.forEach { p ->
            assertTrue("tooth x=${p[0]} outside [$left, $right]", p[0] >= left - 0.01f && p[0] <= right + 0.01f)
            assertTrue("tooth y=${p[1]} outside [$top, $bottom]", p[1] >= top - 0.01f && p[1] <= bottom + 0.01f)
        }
    }

    // ------------------------------------------------------------ text helpers

    @Test
    fun `wrap never produces lines wider than the max width`() {
        val texts = listOf(
            "Short",
            "Amina Shaikh",
            "Very Long Patient Name With Many Words That Cannot Fit On One Single Line At All",
            "A".repeat(300),
            "Mixed " + "B".repeat(200) + " trailing words after the long token"
        )
        texts.forEach { t ->
            val lines = WarrantyCardPdfExporter.wrap(t, 171f, 8f, bold = true, maxLines = 2)
            assertTrue("wrap produced no lines for '$t'", lines.isNotEmpty())
            lines.forEach { line ->
                assertTrue(
                    "wrap line '$line' is wider than 171pt",
                    measure(line, 8f, bold = true) <= 171.5f
                )
            }
        }
    }

    @Test
    fun `ellipsize result always fits the max width`() {
        val t = "Extremely Long Laboratory Name That Definitely Cannot Fit Anywhere"
        val result = WarrantyCardPdfExporter.ellipsize(t, 60f, 6f, bold = true)
        assertTrue(result.endsWith("..."))
        assertTrue(measure(result, 6f, bold = true) <= 60.5f)
    }
}
