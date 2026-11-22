package com.sook.dubedebter

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LedgerReportTest {
    private val customer = Customer(
        id = "c1", name = "=SUM(A1:A2) <አስቴር> & 😀", phone = "0012345", memo = "Line one\nLine two",
        credit = BigDecimal("20.1234"),
        tabs = listOf(
            CustomerTab(id = "t1", name = "Paid tab", openedAt = null, closed = true,
                items = listOf(Item(id = "i1", name = "Coffee", quantity = BigDecimal("2"), price = BigDecimal("50"))),
                paid = BigDecimal("100"), payments = listOf(Payment(id = "p1", amount = BigDecimal("120"), recordedAt = 0))),
            CustomerTab(id = "t2", name = "Open tab", openedAt = 0,
                items = listOf(Item(id = "i2", name = "Tea", quantity = BigDecimal("1.25"), price = BigDecimal("2.35"))))
        )
    )

    private fun report(entries: List<Customer> = listOf(customer)) =
        LedgerReport(entries, "Test scope", 0, TimeZone.getTimeZone("UTC"))

    private fun entries(report: LedgerReport): Map<String, ByteArray> {
        val output = ByteArrayOutputStream()
        ExcelReportWriter.write(report, output)
        return buildMap {
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes())
                }
            }
        }
    }

    @Test fun includesClosedTabsAndSeparatesCreditReceiptsAndBalances() {
        val report = report()
        val summary = report.sheets.first().rows().associate { it[0] to it[1] }
        assertEquals(BigDecimal("2.9375"), summary["Outstanding balance"])
        assertEquals(BigDecimal("20.1234"), summary["Available customer credit"])
        assertEquals(BigDecimal("120"), summary["Recorded receipts"])
        assertEquals(BigDecimal("100"), summary["Applied payments"])
        assertEquals(2, report.sheets.first { it.name == "Tabs" }.rows().count())
        assertEquals("Unknown", report.sheets.first { it.name == "Tabs" }.rows().first()[4])
        assertEquals("Closed", report.sheets.first { it.name == "Tabs" }.rows().first()[5])
    }

    @Test fun scopeDoesNotLeakOtherCustomers() {
        val other = Customer(id = "secret", name = "Other customer")
        val all = report(listOf(customer, other))
        assertEquals(2, all.sheets[1].rows().count())
        val single = entries(report())
        assertFalse(single.values.any { it.toString(Charsets.UTF_8).contains("Other customer") })
        assertEquals(1, report().sheets[1].rows().count())
    }

    @Test fun workbookIsWellFormedAndUserTextIsNotAFormula() {
        val entries = entries(report())
        assertEquals(10, entries.size)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        entries.forEach { (_, bytes) ->
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        }
        val workbook = entries.getValue("xl/workbook.xml").toString(Charsets.UTF_8)
        listOf("Summary", "Customers", "Tabs", "Items", "Payments").forEach {
            assertTrue(workbook.contains("name=\"$it\""))
        }
        val customers = factory.newDocumentBuilder().parse(ByteArrayInputStream(entries.getValue("xl/worksheets/sheet2.xml")))
        val cells = customers.getElementsByTagName("c")
        val name = (0 until cells.length).map { cells.item(it) as org.w3c.dom.Element }
            .first { it.getAttribute("r") == "B2" }
        assertEquals("inlineStr", name.getAttribute("t"))
        assertEquals(customer.name, name.textContent)
        assertEquals(0, customers.getElementsByTagName("f").length)
        assertTrue(customers.documentElement.textContent.contains("0012345"))
        assertTrue(entries.getValue("xl/worksheets/sheet4.xml").toString(Charsets.UTF_8).contains("<v>2.9375</v>"))
    }

    @Test fun largeNumbersStayExactAndEmptyLedgerIsValid() {
        val huge = customer.copy(credit = BigDecimal("123456789012345.67"))
        val xml = entries(report(listOf(huge))).getValue("xl/worksheets/sheet2.xml").toString(Charsets.UTF_8)
        assertTrue(xml.contains("""r="J2" s="0" t="inlineStr"><is><t xml:space="preserve">123456789012345.67"""))
        assertEquals(0, report(emptyList()).sheets[1].rows().count())
        assertTrue(entries(report(emptyList())).containsKey("xl/worksheets/sheet5.xml"))
    }

    @Test fun exportCooperatesWithCancellation() {
        var checks = 0
        try {
            ExcelReportWriter.write(report(), ByteArrayOutputStream()) {
                if (++checks == 4) throw java.util.concurrent.CancellationException()
            }
            fail("Expected cancellation")
        } catch (_: java.util.concurrent.CancellationException) {
            assertEquals(4, checks)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedCellFailsInsteadOfSilentlyDroppingText() {
        entries(report(listOf(customer.copy(memo = "x".repeat(32768)))))
    }
}
