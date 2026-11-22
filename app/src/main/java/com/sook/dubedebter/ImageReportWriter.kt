package com.sook.dubedebter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

internal class ImageReportTooLarge : Exception()

/** One bitmap at a time; long text wraps and continues on the next numbered page. */
internal object ImageReportWriter {
    fun write(report: LedgerReport, directory: File, checkCancelled: () -> Unit = {}): List<File> {
        val files = mutableListOf<File>()
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 32, 46)
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val headingPaint = TextPaint(bodyPaint).apply {
            textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        var bitmap: Bitmap? = null
        var canvas: Canvas? = null
        var y = 0
        var section = "Summary"
        var page = 0
        fun finishPage() {
            bitmap?.let { image ->
                try {
                    val file = File(directory, "ledger-page-${page.toString().padStart(3, '0')}.png")
                    file.outputStream().use { require(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    files += file
                } finally {
                    image.recycle()
                    bitmap = null
                }
            }
        }
        fun newPage() {
            checkCancelled()
            finishPage()
            if (++page > 100) throw ImageReportTooLarge()
            val image = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
            bitmap = image
            canvas = Canvas(image).apply {
                drawColor(Color.WHITE)
                drawRect(0f, 0f, 1200f, 118f, Paint().apply { color = Color.rgb(73, 55, 119) })
                val title = Paint(headingPaint).apply { color = Color.WHITE; textSize = 34f }
                drawText("Dube Debter  |  $section", 56f, 74f, title)
                val footer = Paint(bodyPaint).apply { textSize = 20f; color = Color.DKGRAY }
                drawText("Generated ${report.generated}  •  Page $page", 56f, 1556f, footer)
            }
            y = 150
        }
        fun paragraph(text: String, heading: Boolean = false) {
            checkCancelled()
            val layout = StaticLayout.Builder.obtain(
                text, 0, text.length, if (heading) headingPaint else bodyPaint, 1088
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(5f, 1f).build()
            for (line in 0 until layout.lineCount) {
                checkCancelled()
                val top = layout.getLineTop(line)
                val bottom = layout.getLineBottom(line)
                val height = bottom - top
                if (bitmap == null || y + height > 1490) newPage()
                canvas!!.apply {
                    save()
                    clipRect(56, y, 1144, y + height)
                    translate(56f, (y - top).toFloat())
                    layout.draw(this)
                    restore()
                }
                y += height
            }
            y += if (heading) 12 else 20
        }
        try {
            report.sheets.forEach { sheet ->
                section = sheet.name
                newPage()
                paragraph(report.scope, heading = true)
                var count = 0
                sheet.rows().forEach { values ->
                    if (sheet.name == "Summary") {
                        paragraph("${values[0]}: ${reportValue(values[1])}")
                    } else {
                        if (y > 1350) newPage()
                        paragraph("${sheet.name} • ${++count}", heading = true)
                        val detail = sheet.headers.zip(values).filterNot { it.first.endsWith(" ID") }
                            .joinToString("\n") { (label, value) ->
                                "$label: ${reportValue(value).ifBlank { "—" }}"
                            }
                        paragraph(detail)
                    }
                }
                if (count == 0 && sheet.name != "Summary") paragraph("No records.")
            }
            finishPage()
            return files
        } finally {
            bitmap?.recycle()
        }
    }
}
