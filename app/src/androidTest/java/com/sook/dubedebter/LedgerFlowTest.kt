package com.sook.dubedebter

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sook.dubedebter.ui.theme.DubeDebterTheme
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LedgerFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customerSearchKeepsRegisterActionVisibleAndSelectsMatchingCustomer() {
        var selected: String? = null
        var registering = false
        compose.setContent {
            DubeDebterTheme {
                Surface {
                    CustomerListScreen(
                        (1..50).map { Customer(id = "$it", name = "Customer $it", phone = "0900$it") },
                        saving = false, onRegister = { registering = true }, onSelect = { selected = it }
                    )
                }
            }
        }
        compose.onNodeWithText("Register a customer").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(registering) }
        compose.onNodeWithText("Search name or phone").performTextInput("090050")
        compose.onNodeWithText("Customer 50").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("50", selected) }
        compose.onNodeWithText("Customer 1", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Register a customer").assertIsDisplayed()
    }

    @Test fun paidTabStaysVisibleAndOffersReopenInsteadOfAnotherPayment() {
        val initial = Customer(name = "Aster", tabs = listOf(CustomerTab(id = "tab", name = "Groceries",
            items = listOf(Item(name = "Coffee", quantity = BigDecimal.ONE, price = BigDecimal("100"))))))
        compose.setContent {
            var customer by remember { mutableStateOf(initial) }
            DubeDebterTheme {
                Surface {
                    TabDetailScreen(customer, customer.tabs.single(), false, onAdd = {}, onEdit = {},
                        onPayment = { customer = customer.recordPayment("tab", Payment(amount = BigDecimal("100"))) },
                        onReopen = { customer = customer.copy(tabs = customer.tabs.map { it.copy(closed = false) }) })
                }
            }
        }
        compose.onNodeWithText("Record payment").performClick()
        compose.onNodeWithText("Balance due: 0").assertIsDisplayed()
        compose.onNodeWithText("Record payment").assertDoesNotExist()
        compose.onNodeWithText("Reopen tab").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add item").assertIsDisplayed()
    }

    @Test fun detailsStayOutOfTheWayUntilRequested() {
        val customer = Customer(name = "Aster", phone = "0912345678", memo = "Deliver on Friday")
        compose.setContent {
            DubeDebterTheme {
                Surface {
                    CustomerTabsScreen(customer, false, false, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Memo: Deliver on Friday").assertDoesNotExist()
        compose.onNodeWithText("Contact details and notes").performClick()
        compose.onNodeWithText("Memo: Deliver on Friday").assertIsDisplayed()
        compose.onNodeWithText("Open a tab").assertIsDisplayed()
    }

    @Test fun exportFromCustomerCannotAccidentallySelectWholeLedger() {
        var dismissed = false
        compose.setContent {
            DubeDebterTheme {
                ExportReportDialog(
                    listOf(Customer(name = "Aster"), Customer(name = "Dawit")),
                    Customer(name = "Aster"), onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("Customer report: Aster").assertIsDisplayed()
        compose.onNodeWithText("Whole ledger", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
