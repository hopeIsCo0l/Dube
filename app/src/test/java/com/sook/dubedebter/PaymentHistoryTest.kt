package com.sook.dubedebter

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class PaymentHistoryTest {
    @Test fun includesClosedTabsAndSortsPaymentsAcrossTabs() {
        val earlier = Payment(id = "first", amount = BigDecimal("40"), recordedAt = 10)
        val latest = Payment(id = "latest", amount = BigDecimal("60"), recordedAt = 30)
        val middle = Payment(id = "middle", amount = BigDecimal("20"), recordedAt = 20)
        val customer = Customer(name = "Customer", tabs = listOf(
            CustomerTab(name = "Closed tab", closed = true, payments = listOf(earlier, latest)),
            CustomerTab(name = "Open tab", payments = listOf(middle))
        ))
        val history = customer.paymentHistory()
        assertEquals(listOf("latest", "middle", "first"), history.map { it.payment.id })
        assertEquals(listOf("Closed tab", "Open tab", "Closed tab"), history.map { it.tabName })
        assertEquals(BigDecimal("120"), history.sumOf { it.payment.amount })
    }

    @Test fun doesNotCountReusedCreditAsAnotherPayment() {
        val paid = Customer(name = "Customer", tabs = listOf(
            CustomerTab(id = "old", items = listOf(Item(name = "A", quantity = BigDecimal.ONE, price = BigDecimal("100"))))
        )).recordPayment("old", Payment(amount = BigDecimal("150")))
        val updated = paid.copy(tabs = paid.tabs + CustomerTab(id = "new"))
            .updateTabItems("new", listOf(Item(name = "B", quantity = BigDecimal.ONE, price = BigDecimal("20"))))
        assertEquals(1, updated.paymentHistory().size)
        assertEquals(BigDecimal("150"), updated.paymentHistory().sumOf { it.payment.amount })
    }

    @Test fun emptyCustomerHasEmptyHistory() {
        assertTrue(Customer(name = "New").paymentHistory().isEmpty())
    }
}
