package com.example

import com.example.export.XlsxWriter
import com.example.export.XlsxWriter.Cell
import com.example.export.XlsxWriter.Row
import com.example.export.XlsxWriter.RowStyle
import com.example.export.XlsxWriter.Sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies that the dependency-free XLSX writer produces a structurally valid Office Open
 * XML workbook that Excel accepts: correct zip parts, workbook/sheet XML, styled cells,
 * inline strings, currency number formats and - critically - ALL data rows present.
 */
class XlsxWriterTest {

    private fun buildWorkbook(rows: Int): ByteArray {
        val all = mutableListOf<Row>()
        all += Row(listOf(Cell.Text("DENTAL LAB MANAGEMENT")), RowStyle.TITLE)
        all += Row(listOf(Cell.Text("MONTHLY STATEMENT - June 2026")), RowStyle.SUBTITLE)
        all += Row((0..10).map { Cell.Text("H$it") }, RowStyle.HEADER)
        for (i in 0 until rows) {
            val style = if (i % 2 == 1) RowStyle.ZEBRA else RowStyle.NORMAL
            all += Row(
                listOf(
                    Cell.Number((i + 1).toDouble()),
                    Cell.Text("Patient $i"),
                    Cell.Money(1000.0 + i)
                ),
                style
            )
        }
        all += Row(listOf(Cell.Text("TOTAL"), Cell.Text(""), Cell.Money(1000.0 * rows)), RowStyle.TOTAL)
        return XlsxWriter.build(listOf(Sheet(name = "Statement", rows = all, colWidths = listOf(6.0, 20.0, 12.0))))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                map[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return map
    }

    @Test
    fun `workbook contains all required zip parts`() {
        val parts = unzip(buildWorkbook(5))
        assertTrue(parts.containsKey("[Content_Types].xml"))
        assertTrue(parts.containsKey("_rels/.rels"))
        assertTrue(parts.containsKey("xl/workbook.xml"))
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(parts.containsKey("xl/styles.xml"))
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
    }

    @Test
    fun `sheet xml is well-formed and contains every data row`() {
        val dataRows = 37
        val parts = unzip(buildWorkbook(dataRows))
        val sheetXml = parts.getValue("xl/worksheets/sheet1.xml")

        val doc = DocumentBuilderFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(sheetXml.toByteArray()))

        val rows = doc.getElementsByTagName("row")
        // 3 meta rows + 37 data rows + 1 total row
        assertEquals(3 + dataRows + 1, rows.length)

        val cells = doc.getElementsByTagName("c")
        // header 11 cells; each data row 3 cells; total 3; title/subtitle 1 each
        val expectedCells = 11 + dataRows * 3 + 3 + 2
        assertEquals(expectedCells, cells.length)

        // Inline string content survived
        assertTrue(sheetXml.contains("Patient 0"))
        assertTrue(sheetXml.contains("Patient ${dataRows - 1}"))
        // Money cells use the Indian currency format + right alignment (style 7/8/9)
        assertTrue(sheetXml.contains("s=\"7\"") || sheetXml.contains("s=\"8\""))
        // Header uses the coloured heading style
        assertTrue(sheetXml.contains("s=\"4\""))
        // Column widths present
        assertTrue(sheetXml.contains("<cols>"))
        assertTrue(sheetXml.contains("customWidth=\"1\""))
    }

    @Test
    fun `styles define navy and blue fills plus currency number format`() {
        val parts = unzip(buildWorkbook(3))
        val styles = parts.getValue("xl/styles.xml")
        assertTrue(styles.contains("FF0F172A"))  // navy header fill
        assertTrue(styles.contains("FF0284C7"))  // blue table heading fill
        assertTrue(styles.contains("FFF1F5F9"))  // zebra fill
        assertTrue(styles.contains("numFmtId=\"164\""))
        assertTrue(styles.contains("#,##,##0.00"))
    }

    @Test
    fun `workbook xml references the sheet by name`() {
        val parts = unzip(buildWorkbook(2))
        val workbook = parts.getValue("xl/workbook.xml")
        assertTrue(workbook.contains("name=\"Statement\""))
        assertTrue(workbook.contains("r:id=\"rId1\""))
        val rels = parts.getValue("xl/_rels/workbook.xml.rels")
        assertTrue(rels.contains("Target=\"worksheets/sheet1.xml\""))
        assertTrue(rels.contains("Target=\"styles.xml\""))
    }

    @Test
    fun `multi sheet workbooks are supported`() {
        val bytes = XlsxWriter.build(
            listOf(
                Sheet("One", listOf(Row(listOf(Cell.Text("A1")), RowStyle.NORMAL))),
                Sheet("Two", listOf(Row(listOf(Cell.Number(1.0)), RowStyle.NORMAL)))
            )
        )
        val parts = unzip(bytes)
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(parts.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(parts.getValue("xl/workbook.xml").contains("name=\"Two\""))
    }

    @Test
    fun `column names go beyond Z`() {
        assertEquals("A", XlsxWriter.colName(0))
        assertEquals("Z", XlsxWriter.colName(25))
        assertEquals("AA", XlsxWriter.colName(26))
        assertEquals("AL", XlsxWriter.colName(37))
    }

    @Test
    fun `freeze panes emit sheetViews with frozen pane`() {
        val bytes = XlsxWriter.build(
            listOf(
                Sheet(
                    "S", 
                    listOf(
                        Row(listOf(Cell.Text("H1"), Cell.Text("H2")), RowStyle.HEADER),
                        Row(listOf(Cell.Text("a"), Cell.Text("b")), RowStyle.NORMAL)
                    ),
                    freezeRows = 1
                )
            )
        )
        val parts = unzip(bytes)
        val xml = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(xml.contains("<sheetViews>"))
        assertTrue(xml.contains("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>"""))
    }

    @Test
    fun `sheets without freeze emit default sheetView`() {
        val bytes = XlsxWriter.build(
            listOf(Sheet("S", listOf(Row(listOf(Cell.Text("x")), RowStyle.NORMAL))))
        )
        val parts = unzip(bytes)
        val xml = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(xml.contains("<sheetView workbookViewId=\"0\"/>"))
        assertFalse(xml.contains("ySplit"))
    }

    @Test
    fun `xml escaping protects against broken output`() {
        val bytes = XlsxWriter.build(
            listOf(Sheet("S", listOf(Row(listOf(Cell.Text("AT&T \"quote\" <tag> & ok")), RowStyle.NORMAL))))
        )
        val parts = unzip(bytes)
        val xml = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(xml.contains("AT&amp;T &quot;quote&quot; &lt;tag&gt; &amp; ok"))
        // still parses
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))
    }
}
