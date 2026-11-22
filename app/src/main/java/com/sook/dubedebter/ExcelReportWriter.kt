package com.sook.dubedebter

import java.io.OutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small, dependency-free OOXML writer. User input is always literal text, never a formula. */
internal object ExcelReportWriter {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    fun write(report: LedgerReport, output: OutputStream, checkCancelled: () -> Unit = {}) {
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, body: () -> Unit) {
                zip.putNextEntry(ZipEntry(name))
                body()
                zip.closeEntry()
            }
            fun xml(text: String) { zip.write(text.toByteArray(Charsets.UTF_8)) }
            entry("[Content_Types].xml") {
                xml("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
""")
                report.sheets.indices.forEach { i ->
                    xml("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                xml("</Types>")
            }
            entry("_rels/.rels") {
                xml("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            }
            entry("xl/workbook.xml") {
                xml("""<workbook xmlns="$NS" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                report.sheets.forEachIndexed { i, sheet ->
                    xml("""<sheet name="${escape(sheet.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                }
                xml("</sheets></workbook>")
            }
            entry("xl/_rels/workbook.xml.rels") {
                xml("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                report.sheets.indices.forEach { i ->
                    xml("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
                }
                xml("""<Relationship Id="styles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            }
            entry("xl/styles.xml") {
                xml("""<styleSheet xmlns="$NS">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF493777"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""")
            }
            report.sheets.forEachIndexed { sheetIndex, sheet ->
                checkCancelled()
                entry("xl/worksheets/sheet${sheetIndex + 1}.xml") {
                    xml("""<worksheet xmlns="$NS"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="30"/><cols>""")
                    sheet.headers.forEachIndexed { i, name ->
                        val width = if (sheet.name == "Summary" && i == 1) 100 else when {
                            name.endsWith("ID") -> 38
                            name == "Memo" -> 60
                            else -> 24
                        }
                        xml("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
                    }
                    xml("</cols><sheetData>")
                    fun row(index: Int, values: List<Any>, header: Boolean = false) {
                        require(index <= 1_048_576) { "Excel row limit exceeded" }
                        checkCancelled()
                        val lines = values.mapIndexed { i, value ->
                            val width = if (sheet.name == "Summary" && i == 1) 96
                                else if (sheet.headers[i] == "Memo") 56 else 20
                            reportValue(value).split('\n').sumOf { (it.length / width + 1) }
                        }.maxOrNull() ?: 1
                        val height = if (header) 32 else (lines * 16 + 8).coerceIn(30, 409)
                        xml("""<row r="$index" ht="$height" customHeight="1">""")
                        values.forEachIndexed { i, value ->
                            val ref = column(i) + index
                            val text = reportValue(value)
                            // Excel has 15-digit precision. Preserve larger values exactly as text.
                            val number = when (value) {
                                is BigDecimal -> value.stripTrailingZeros().precision() <= 15
                                is Int, is Long -> text.length <= 15
                                else -> false
                            }
                            if (number && !header) xml("""<c r="$ref"><v>$text</v></c>""")
                            else {
                                require(text.length <= 32767) { "Text exceeds Excel cell limit" }
                                xml("""<c r="$ref" s="${if (header) 1 else 0}" t="inlineStr"><is><t xml:space="preserve">${escape(text)}</t></is></c>""")
                            }
                        }
                        xml("</row>")
                    }
                    row(1, sheet.headers, true)
                    var lastRow = 1
                    sheet.rows().forEach { row(++lastRow, it) }
                    xml("</sheetData>")
                    if (sheet.name != "Summary") xml("""<autoFilter ref="A1:${column(sheet.headers.lastIndex)}$lastRow"/>""")
                    xml("""<pageMargins left="0.3" right="0.3" top="0.5" bottom="0.5" header="0.2" footer="0.2"/><pageSetup orientation="landscape" paperSize="9" fitToWidth="1" fitToHeight="0"/></worksheet>""")
                }
            }
        }
    }

    private fun column(index: Int): String {
        var number = index + 1
        var result = ""
        while (number > 0) {
            number--
            result = ('A' + number % 26) + result
            number /= 26
        }
        return result
    }

    private fun escape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val code = Character.codePointAt(value, index)
            index += Character.charCount(code)
            when (code) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                34 -> append("&quot;")
                39 -> append("&apos;")
                9, 10, 13 -> appendCodePoint(code)
                in 0x20..0xD7FF, in 0xE000..0xFFFD, in 0x10000..0x10FFFF -> appendCodePoint(code)
            }
        }
    }
}
