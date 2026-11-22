package com.sook.dubedebter

import java.math.BigDecimal
import java.util.UUID

internal data class Item(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: BigDecimal,
    val price: BigDecimal
) {
    val total: BigDecimal get() = quantity * price
}

internal data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val amount: BigDecimal,
    val recordedAt: Long = System.currentTimeMillis()
)

internal data class CustomerTab(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Tab 1",
    val openedAt: Long? = System.currentTimeMillis(),
    val closed: Boolean = false,
    val items: List<Item> = emptyList(),
    val paid: BigDecimal = BigDecimal.ZERO,
    val payments: List<Payment> = emptyList()
) {
    val total: BigDecimal get() = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.total }
    val balance: BigDecimal get() = (total - paid).max(BigDecimal.ZERO)
}

internal fun List<CustomerTab>.visibleTabs(showAll: Boolean): List<CustomerTab> =
    asReversed().filter { showAll || !it.closed }

internal data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tabs: List<CustomerTab> = emptyList(),
    val phone: String = "",
    val memo: String = "",
    val credit: BigDecimal = BigDecimal.ZERO
) {
    val balance: BigDecimal get() = tabs.sumOf { it.balance }
}

// Set to 1 day for testing overdue alerts (change back to 30L for production)
private const val THIRTY_DAYS_MS = 1L * 24 * 60 * 60 * 1000L

internal val CustomerTab.isOverdue: Boolean
    get() = !closed && balance > BigDecimal.ZERO && openedAt != null && (System.currentTimeMillis() - openedAt >= THIRTY_DAYS_MS)

internal val Customer.hasOverdueTabs: Boolean
    get() = tabs.any { it.isOverdue }

internal data class PaymentHistoryEntry(val tabName: String, val payment: Payment)

internal fun Customer.paymentHistory(): List<PaymentHistoryEntry> =
    tabs.asReversed().flatMap { tab ->
        tab.payments.asReversed().map { PaymentHistoryEntry(tab.name, it) }
    }.sortedByDescending { it.payment.recordedAt }

/** Allocate funds only to the selected tab; surplus remains available for future items. */
internal fun Customer.recordPayment(tabId: String, payment: Payment): Customer {
    require(payment.amount >= BigDecimal.ZERO)
    if (tabs.any { tab -> tab.payments.any { it.id == payment.id } }) return this
    val tab = tabs.first { it.id == tabId }
    require(!tab.closed)
    require(payment.amount > BigDecimal.ZERO || tab.balance <= credit)
    val available = credit + payment.amount
    val applied = tab.balance.min(available)
    val updated = tab.copy(
        paid = tab.paid + applied,
        payments = if (payment.amount > BigDecimal.ZERO) tab.payments + payment else tab.payments,
        closed = (tab.balance - applied).signum() == 0
    )
    return copy(
        credit = available - applied,
        tabs = tabs.map { if (it.id == tabId) updated else it }
    )
}

/** Item edits return excess allocated funds, then apply available credit exactly once. */
internal fun Customer.updateTabItems(tabId: String, items: List<Item>): Customer {
    val tab = tabs.first { it.id == tabId }
    require(!tab.closed)
    val changed = tab.copy(items = items)
    val returned = (tab.paid - changed.total).max(BigDecimal.ZERO)
    val retained = tab.paid.min(changed.total)
    val available = credit + returned
    val applied = (changed.total - retained).min(available)
    val paid = retained + applied
    val updated = changed.copy(
        paid = paid,
        closed = items.isNotEmpty() && paid > BigDecimal.ZERO && paid >= changed.total
    )
    return copy(credit = available - applied, tabs = tabs.map { if (it.id == tabId) updated else it })
}

internal fun parsePaymentAmount(value: String): BigDecimal? =
    value.trim().takeIf { it.matches(Regex("[0-9]{1,15}(\\.[0-9]{1,2})?")) }?.toBigDecimalOrNull()
