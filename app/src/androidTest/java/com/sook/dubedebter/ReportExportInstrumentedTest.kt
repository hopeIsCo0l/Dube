package com.sook.dubedebter

import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportExportInstrumentedTest {
    @Test fun longUnicodeReportPaginatesAndSharesThroughRestrictedProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "reports/test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val customer = Customer(name = "አስቴር & Customer 😀", memo = (1..120).joinToString("\n") {
                "Detailed note $it with enough content to exercise wrapped report pages."
            }, tabs = listOf(CustomerTab(name = "Monthly tab", items = listOf(
                Item(name = "Coffee", quantity = BigDecimal("1.25"), price = BigDecimal("2.35"))
            ))))
            val files = ImageReportWriter.write(LedgerReport(listOf(customer), "Customer report"), folder)
            assertTrue(files.size > 5)
            files.forEachIndexed { i, file ->
                assertEquals("ledger-page-${(i + 1).toString().padStart(3, '0')}.png", file.name)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
                assertEquals("content", uri.scheme)
                assertEquals("image/png", context.contentResolver.getType(uri))
                context.contentResolver.openInputStream(uri).use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    assertNotNull(bitmap)
                    assertEquals(1200, bitmap.width)
                    assertEquals(1600, bitmap.height)
                    bitmap.recycle()
                }
            }
            val privateFile = File(context.filesDir, "not-a-report.txt")
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.reports", privateFile)
                fail("Provider must not expose unrelated app files")
            } catch (_: IllegalArgumentException) { }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun imageExportStopsOnCancellation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "reports/test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var checks = 0
            try {
                ImageReportWriter.write(LedgerReport(emptyList(), "Empty ledger"), folder) {
                    if (++checks > 5) throw java.util.concurrent.CancellationException()
                }
                fail("Expected cancellation")
            } catch (_: java.util.concurrent.CancellationException) { }
        } finally {
            folder.deleteRecursively()
        }
    }
}
