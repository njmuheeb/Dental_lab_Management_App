package com.example.export

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Dependency-free XLSX (Office Open XML spreadsheet) writer.
 *
 * Produces a genuine .xlsx file: a ZIP package containing workbook, styles and worksheet
 * parts. Features used: styled title/header/total rows (bold, fills, white-on-navy
 * headings), thin borders, zebra striping, custom column widths, right-aligned currency
 * cells with an Indian-format number style, and multi-sheet support. Opens directly in
 * Microsoft Excel, Google Sheets and LibreOffice.
 *
 * Pure Kotlin (java.util.zip + string XML) so it is fully unit-testable on the JVM.
 */
object XlsxWriter {

    /** Cell content: either text or a numeric value. */
    sealed class Cell {
        data class Text(val value: String) : Cell()
        data class Number(val value: Double) : Cell()
        data class Money(val value: Double) : Cell()

        companion object {
            fun of(value: String): Cell = Text(value)
            fun of(value: Double): Cell = Number(value)
        }
    }

    /** Row appearance. Maps to pre-built cellXfs style indices (see stylesXml). */
    enum class RowStyle { TITLE, SUBTITLE, HEADER, NORMAL, ZEBRA, TOTAL, ACCENT, MUTED }

    data class Row(val cells: List<Cell>, val style: RowStyle = RowStyle.NORMAL)

    data class Sheet(
        val name: String,
        val rows: List<Row>,
        val colWidths: List<Double> = emptyList(),
        /** When > 0, freezes the first N rows (e.g. header row stays visible while scrolling). */
        val freezeRows: Int = 0
    )

    fun build(sheets: List<Sheet>): ByteArray {
        require(sheets.isNotEmpty()) { "At least one sheet is required" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", contentTypesXml(sheets.size))
            put(zip, "_rels/.rels", rootRelsXml())
            put(zip, "xl/workbook.xml", workbookXml(sheets))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            put(zip, "xl/styles.xml", stylesXml())
            sheets.forEachIndexed { idx, sheet ->
                put(zip, "xl/worksheets/sheet${idx + 1}.xml", sheetXml(sheet))
            }
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ parts

    private fun contentTypesXml(sheetCount: Int): String {
        val overrides = (1..sheetCount).joinToString("") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>$overrides<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    }

    private fun rootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbookXml(sheets: List<Sheet>): String {
        val sheetTags = sheets.mapIndexed { idx, s ->
            """<sheet name="${esc(s.name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheetTags</sheets></workbook>"""
    }

    private fun workbookRelsXml(sheetCount: Int): String {
        val sheetRels = (1..sheetCount).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$sheetRels<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    }

    /**
     * Styles: fonts (0 normal, 1 bold, 2 bold white, 3 bold 14 navy title, 4 muted),
     * fills (0 none, 1 gray125, 2 navy 0F172A, 3 blue 0284C7, 4 zebra F1F5F9, 5 red-light FEE2E2, 6 green-light DCFCE7),
     * border 1 = thin box, numFmt 164 = Indian currency grouping.
     */
    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;₹&quot;#,##,##0.00"/></numFmts>
<fonts count="5">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
<font><b/><sz val="14"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
<font><sz val="10"/><color rgb="FF64748B"/><name val="Calibri"/></font>
</fonts>
<fills count="7">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF0F172A"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF0284C7"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFEE2E2"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFDCFCE7"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FFCBD5E1"/></left><right style="thin"><color rgb="FFCBD5E1"/></right><top style="thin"><color rgb="FFCBD5E1"/></top><bottom style="thin"><color rgb="FFCBD5E1"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="12">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>                                                     <!-- 0 normal -->
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>                                        <!-- 1 title -->
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>                                        <!-- 2 subtitle bold -->
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>                                        <!-- 3 muted -->
<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf> <!-- 4 header -->
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>                                      <!-- 5 bordered text -->
<xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1"/>                        <!-- 6 zebra text -->
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right"/></xf> <!-- 7 money -->
<xf numFmtId="164" fontId="0" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right"/></xf> <!-- 8 money zebra -->
<xf numFmtId="164" fontId="1" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right"/></xf> <!-- 9 money total -->
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>                        <!-- 10 bold text total -->
<xf numFmtId="0" fontId="1" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>          <!-- 11 balance due highlight -->
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private fun sheetXml(sheet: Sheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (sheet.freezeRows > 0) {
            val frozenCell = "A${sheet.freezeRows + 1}"
            sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="${sheet.freezeRows}" topLeftCell="$frozenCell" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        } else {
            sb.append("""<sheetViews><sheetView workbookViewId="0"/></sheetViews>""")
        }
        if (sheet.colWidths.isNotEmpty()) {
            sb.append("<cols>")
            sheet.colWidths.forEachIndexed { idx, w ->
                sb.append("""<col min="${idx + 1}" max="${idx + 1}" width="$w" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { rowIdx, row ->
            val r = rowIdx + 1
            sb.append("""<row r="$r">""")
            row.cells.forEachIndexed { colIdx, cell ->
                val ref = colName(colIdx) + r
                when (cell) {
                    is Cell.Text -> sb.append("""<c r="$ref" s="${textStyle(row.style)}" t="inlineStr"><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
                    is Cell.Number -> sb.append("""<c r="$ref" s="${numStyle(row.style)}"><v>${numStr(cell.value)}</v></c>""")
                    is Cell.Money -> sb.append("""<c r="$ref" s="${moneyStyle(row.style)}"><v>${numStr(cell.value)}</v></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    // style index mapping per RowStyle
    private fun textStyle(style: RowStyle): Int = when (style) {
        RowStyle.TITLE -> 1
        RowStyle.SUBTITLE -> 2
        RowStyle.HEADER -> 4
        RowStyle.NORMAL -> 5
        RowStyle.ZEBRA -> 6
        RowStyle.TOTAL -> 10
        RowStyle.ACCENT -> 11
        RowStyle.MUTED -> 3
    }

    private fun numStyle(style: RowStyle): Int = when (style) {
        RowStyle.ZEBRA -> 6
        RowStyle.HEADER -> 4
        else -> 5
    }

    private fun moneyStyle(style: RowStyle): Int = when (style) {
        RowStyle.ZEBRA -> 8
        RowStyle.TOTAL -> 9
        RowStyle.HEADER -> 4
        else -> 7
    }

    // ------------------------------------------------------------------ helpers

    fun colName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun numStr(value: Double): String = BigDecimal.valueOf(value).toPlainString()

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
