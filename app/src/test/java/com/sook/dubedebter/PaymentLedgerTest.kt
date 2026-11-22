package com.sook.dubedebter

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class PaymentLedgerTest {
    private fun item(total: String) = Item(name = "Item", quantity = BigDecimal.ONE, price = total.toBigDecimal())
    private fun customer(total: String = "100") =
        Customer(name = "Customer", tabs = listOf(CustomerTab(id = "tab", items = listOf(item(total)))))
    private fun Customer.pay(amount: String, id: String = java.util.UUID.randomUUID().toString()) =
        recordPayment("tab", Payment(id = id, amount = amount.toBigDecimal()))
    private fun money(expected: String, actual: BigDecimal) =
        assertEquals(0, expected.toBigDecimal().compareTo(actual))

    @Test fun partialThenFullPaymentClosesOnlyWhenSettled() {
        val partial = customer().pay("40")
        assertFalse(partial.tabs.single().closed)
        money("60", partial.balance)
        val settled = partial.pay("60")
        assertTrue(settled.tabs.single().closed)
        money("0", settled.balance)
        assertEquals(2, settled.tabs.single().payments.size)
    }

    @Test fun surplusIsAppliedToFutureItemsOnlyOnce() {
        val paid = customer().pay("150")
        money("50", paid.credit)
        val future = paid.copy(tabs = paid.tabs + CustomerTab(id = "future"))
            .updateTabItems("future", listOf(item("80")))
        money("0", future.credit)
        money("30", future.tabs.last().balance)
        val edited = future.updateTabItems("future", listOf(item("90")))
        money("40", edited.tabs.last().balance)
        money("50", edited.tabs.last().paid)
    }

    @Test fun creditFullyCoversFutureTabAndClosesIt() {
        val paid = customer().pay("150")
        val future = paid.copy(tabs = paid.tabs + CustomerTab(id = "future"))
            .updateTabItems("future", listOf(item("20")))
        assertTrue(future.tabs.last().closed)
        money("30", future.credit)
        money("0", future.balance)
    }

    @Test fun itemReductionReturnsAllocatedPaymentAsCredit() {
        val partial = customer().pay("60")
        val reduced = partial.updateTabItems("tab", listOf(item("40")))
        money("20", reduced.credit)
        money("40", reduced.tabs.single().paid)
        assertTrue(reduced.tabs.single().closed)
    }

    @Test fun deletingAllItemsPreservesMoneyAsCredit() {
        val deleted = customer().pay("60").updateTabItems("tab", emptyList())
        money("60", deleted.credit)
        money("0", deleted.tabs.single().paid)
        money("0", deleted.balance)
    }

    @Test fun duplicatePaymentCannotChargeTwice() {
        val first = customer().pay("25", "same-payment")
        assertEquals(first, first.pay("25", "same-payment"))
    }

    @Test fun customerAndOtherTabBalancesStaySeparate() {
        val other = CustomerTab(id = "other", items = listOf(item("80")))
        val original = customer().copy(tabs = customer().tabs + other)
        val updated = original.pay("120")
        assertEquals(other, updated.tabs.last())
        money("20", updated.credit)
        money("0", customer().credit)
    }

    @Test fun decimalPaymentsStayExact() {
        val result = customer("0.30").pay("0.10").pay("0.20")
        money("0", result.balance)
        assertTrue(result.tabs.single().closed)
    }

    @Test fun zeroCanCloseAnEmptyTab() {
        assertTrue(customer("0").pay("0").tabs.single().closed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCannotCloseUnpaidTab() { customer().pay("0") }

    @Test(expected = IllegalArgumentException::class)
    fun negativePaymentRejected() { customer().pay("-1") }

    @Test fun invalidAmountsRejected() {
        listOf("", "-1", "abc", "1e3", "1.234", "NaN").forEach {
            assertNull(parsePaymentAmount(it))
        }
        money("12.50", parsePaymentAmount("12.50")!!)
    }

    @Test fun reopeningDoesNotLosePayments() {
        val paid = customer().pay("100")
        val reopened = paid.copy(tabs = paid.tabs.map { it.copy(closed = false) })
        val updated = reopened.updateTabItems("tab", listOf(item("120")))
        money("20", updated.balance)
        assertEquals(paid.tabs.single().payments, updated.tabs.single().payments)
    }
}
