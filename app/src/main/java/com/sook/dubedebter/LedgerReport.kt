package com.sook.dubedebter

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class ReportSheet(
    val name: String,
    val headers: List<String>,
    val rows: () -> Sequence<List<Any>>
)

/** An immutable ledger snapshot. Export never changes balances or payment allocations. */
internal class LedgerReport(
    private val customers: List<Customer>,
    val scope: String,
    generatedAt: Long = System.currentTimeMillis(),
    private val zone: TimeZone = TimeZone.getDefault()
) {
    private fun date(timestamp: Long?): String = timestamp?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).apply { timeZone = zone }.format(Date(it))
    } ?: "Unknown"
    val generated: String = date(generatedAt)
    private val tabs get() = customers.asSequence().flatMap { customer ->
        customer.tabs.asSequence().map { customer to it }
    }

    val sheets: List<ReportSheet> = listOf(
        ReportSheet("Summary", listOf("Measure", "Value")) {
            sequenceOf(
                listOf("Report", "Dube Debter ledger"),
                listOf("Scope", scope),
                listOf("Generated", generated),
                listOf("Time zone", zone.id),
                listOf("Customers", customers.size),
                listOf("Open tabs", tabs.count { !it.second.closed }),
                listOf("Closed tabs", tabs.count { it.second.closed }),
                listOf("Item total", tabs.sumOf { it.second.total }),
                listOf("Applied payments", tabs.sumOf { it.second.paid }),
                listOf("Outstanding balance", customers.sumOf { it.balance }),
                listOf("Available customer credit", customers.sumOf { it.credit }),
                listOf("Recorded receipts", tabs.sumOf { it.second.payments.sumOf { payment -> payment.amount } }),
                listOf("Notes", "Includes all open and closed tabs. Applied payments and recorded receipts are different: legacy payments may have no receipt history. Credit is shown separately; do not subtract it again from balances. Amounts use the ledger's units; no currency conversion.")
            )
        },
        ReportSheet("Customers", listOf("Customer ID", "Customer", "Phone", "Memo", "Open tabs",
            "Closed tabs", "Item total", "Applied payments", "Outstanding balance", "Available credit", "Recorded receipts")) {
            customers.asSequence().map { c ->
                listOf(c.id, c.name, c.phone, c.memo, c.tabs.count { !it.closed }, c.tabs.count { it.closed },
                    c.tabs.sumOf { it.total }, c.tabs.sumOf { it.paid }, c.balance, c.credit,
                    c.tabs.sumOf { it.payments.sumOf { p -> p.amount } })
            }
        },
        ReportSheet("Tabs", listOf("Customer ID", "Customer", "Tab ID", "Tab", "Opened", "Status",
            "Item count", "Item total", "Applied payments", "Outstanding balance")) {
            tabs.map { (c, t) ->
                listOf(c.id, c.name, t.id, t.name, date(t.openedAt), if (t.closed) "Closed" else "Open",
                    t.items.size, t.total, t.paid, t.balance)
            }
        },
        ReportSheet("Items", listOf("Customer ID", "Customer", "Tab ID", "Tab", "Item ID",
            "Item", "Quantity", "Unit price", "Item total")) {
            tabs.flatMap { (c, t) ->
                t.items.asSequence().map { i ->
                    listOf(c.id, c.name, t.id, t.name, i.id, i.name, i.quantity, i.price, i.total)
                }
            }
        },
        ReportSheet("Payments", listOf("Customer ID", "Customer", "Tab ID", "Tab", "Payment ID",
            "Recorded", "Receipt amount")) {
            tabs.flatMap { (c, t) ->
                t.payments.asSequence().map { p ->
                    listOf(c.id, c.name, t.id, t.name, p.id, date(p.recordedAt), p.amount)
                }
            }
        }
    )
}

internal fun reportValue(value: Any): String =
    if (value is BigDecimal) value.stripTrailingZeros().toPlainString() else value.toString()
