package com.sook.dubedebter

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class TabVisibilityTest {
    @Test fun hidesClosedTabsAndKeepsNewestFirst() {
        val oldest = CustomerTab(id = "old")
        val closed = CustomerTab(id = "closed", closed = true)
        val newest = CustomerTab(id = "new")
        assertEquals(listOf(newest, oldest), listOf(oldest, closed, newest).visibleTabs(false))
        assertEquals(listOf(newest, closed, oldest), listOf(oldest, closed, newest).visibleTabs(true))
    }

    @Test fun closingPreservesItemsAndTotalAndReopeningRestoresVisibility() {
        val original = CustomerTab(items = listOf(
            Item(name = "Coffee", quantity = BigDecimal("2"), price = BigDecimal("34"))
        ))
        val closed = original.copy(closed = true)
        assertEquals(original.items, closed.items)
        assertEquals(BigDecimal("68"), closed.total)
        assertTrue(listOf(closed).visibleTabs(false).isEmpty())
        assertEquals(listOf(closed), listOf(closed).visibleTabs(true))
        val reopened = closed.copy(closed = false)
        assertEquals(listOf(reopened), listOf(reopened).visibleTabs(false))
    }

    @Test fun totalsFollowVisibleTabs() {
        val open = CustomerTab(items = listOf(
            Item(name = "A", quantity = BigDecimal.ONE, price = BigDecimal("10"))
        ))
        val closed = CustomerTab(closed = true, items = listOf(
            Item(name = "B", quantity = BigDecimal.ONE, price = BigDecimal("20"))
        ))
        val tabs = listOf(open, closed)
        assertEquals(BigDecimal("10"), tabs.visibleTabs(false).sumOf { it.total })
        assertEquals(BigDecimal("30"), tabs.visibleTabs(true).sumOf { it.total })
    }
}
