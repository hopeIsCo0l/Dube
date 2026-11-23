package com.sook.dubedebter

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

private fun BigDecimal.amount() = stripTrailingZeros().toPlainString()

@Composable
internal fun CustomerListScreen(
    customers: List<Customer>, saving: Boolean,
    onRegister: () -> Unit, onSelect: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = customers.asReversed().filter {
        it.name.contains(query.trim(), true) || it.phone.contains(query.trim())
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (customers.isNotEmpty()) OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.search_customer_hint)) },
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) {
                    Text(stringResource(R.string.clear_search_action))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)) {
            if (visible.isEmpty()) item {
                Text(stringResource(if (customers.isEmpty()) R.string.no_customers else R.string.no_search_results),
                    modifier = Modifier.padding(vertical = 20.dp))
            }
            items(visible, key = { it.id }) { customer ->
                OutlinedCard(onClick = { onSelect(customer.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(customer.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.balance_due, customer.balance.amount()),
                            style = MaterialTheme.typography.bodyLarge)
                        if (customer.phone.isNotBlank()) Text(customer.phone,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (customer.hasOverdueTabs) OverdueBadge()
                    }
                }
            }
        }
        Button(onClick = onRegister, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.register_customer))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun CustomerTabsScreen(
    customer: Customer, showAll: Boolean, saving: Boolean,
    onShowAll: (Boolean) -> Unit, onOpen: () -> Unit, onSelect: (String) -> Unit,
    onHistory: () -> Unit
) {
    val visible = customer.tabs.visibleTabs(showAll)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.balance_due, customer.balance.amount()),
                            style = MaterialTheme.typography.headlineSmall)
                        if (customer.credit.signum() > 0) Text(stringResource(R.string.customer_credit, customer.credit.amount()))
                        TextButton(onClick = onHistory) { Text(stringResource(R.string.payment_history_title)) }
                    }
                }
            }
            if (customer.phone.isNotBlank() || customer.memo.isNotBlank()) item {
                LedgerDisclosure(stringResource(R.string.customer_details), customer.id) {
                    if (customer.phone.isNotBlank()) Text(stringResource(R.string.phone_value, customer.phone))
                    if (customer.memo.isNotBlank()) Text(stringResource(R.string.memo_value, customer.memo))
                }
            }
            item {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showAll, onClick = { onShowAll(false) },
                        label = { Text(stringResource(R.string.open_tabs_filter)) })
                    FilterChip(selected = showAll, onClick = { onShowAll(true) },
                        label = { Text(stringResource(R.string.all_tabs_filter)) })
                }
            }
            if (visible.isEmpty()) item {
                Text(stringResource(if (customer.tabs.isEmpty()) R.string.no_tabs else R.string.no_open_tabs_filter))
            }
            items(visible, key = { it.id }) { tab ->
                OutlinedCard(onClick = { onSelect(tab.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tab.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.balance_due, tab.balance.amount()))
                        Text(stringResource(R.string.tab_summary, tab.items.size, tab.total.amount()),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (tab.isOverdue) OverdueBadge() else if (tab.closed) ClosedTabBadge()
                    }
                }
            }
        }
        Button(onClick = onOpen, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.open_tab))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun TabDetailScreen(
    customer: Customer, tab: CustomerTab, saving: Boolean,
    onAdd: () -> Unit, onEdit: (String) -> Unit, onPayment: () -> Unit, onReopen: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(customer.name, style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.balance_due, tab.balance.amount()),
                            style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.applied_payment, tab.paid.amount()))
                        if (customer.credit.signum() > 0) Text(stringResource(R.string.customer_credit, customer.credit.amount()))
                        if (tab.closed) ClosedTabBadge()
                        if (!tab.closed && tab.items.isNotEmpty()) TextButton(onClick = onPayment, enabled = !saving) {
                            Text(stringResource(R.string.record_payment))
                        }
                    }
                }
            }
            item {
                LedgerDisclosure(stringResource(R.string.tab_details), tab.id) {
                    Text(tab.openedLabel())
                    Text(stringResource(R.string.total_value, tab.total.amount()))
                }
            }
            if (tab.isOverdue) item { TabReminder(customer, tab) }
            item { Text(stringResource(R.string.items_heading), style = MaterialTheme.typography.titleMedium) }
            if (tab.items.isEmpty()) item {
                Text(stringResource(if (tab.closed) R.string.closed_tab_read_only else R.string.add_item_help))
            }
            items(tab.items.asReversed(), key = { it.id }) { item ->
                OutlinedCard(
                    onClick = { onEdit(item.id) }, enabled = !tab.closed && !saving, modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.quantity_value, item.quantity.amount()))
                        Text(stringResource(R.string.price_value, item.price.amount()))
                        Text(stringResource(R.string.total_value, item.total.amount()), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (tab.payments.isNotEmpty()) item {
                LedgerDisclosure(stringResource(R.string.payment_history), tab.id) {
                    tab.payments.asReversed().forEach { payment ->
                        Text(stringResource(R.string.received_amount, payment.amount.amount()),
                            style = MaterialTheme.typography.titleSmall)
                        Text(dualDateLabel(payment.recordedAt, R.string.payment_dual_date))
                        HorizontalDivider()
                    }
                }
            }
            if (tab.closed) item { Text(stringResource(R.string.closed_tab_read_only)) }
        }
        if (tab.closed) {
            OutlinedButton(onClick = onReopen, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.reopen_tab))
            }
        } else {
            Button(onClick = onAdd, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.add_item))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LedgerDisclosure(title: String, stateKey: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(title, modifier = Modifier.weight(1f))
            Text(if (expanded) "−" else "+")
        }
        if (expanded) Column(Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun TabReminder(customer: Customer, tab: CustomerTab) {
    val context = LocalContext.current
    val template = stringResource(R.string.sms_reminder_template)
    LedgerDisclosure(stringResource(R.string.overdue_badge), tab.id) {
        Text(stringResource(R.string.overdue_alert_desc))
        if (customer.phone.isNotBlank()) {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone.trim()}")))
            }) { Text(stringResource(R.string.call_customer)) }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${customer.phone.trim()}")).apply {
                    putExtra("sms_body", String.format(template, customer.name, tab.name, tab.balance.amount()))
                })
            }) { Text(stringResource(R.string.send_sms_reminder)) }
        } else Text(stringResource(R.string.no_phone_warning))
    }
}
