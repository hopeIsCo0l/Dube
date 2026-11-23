package com.sook.dubedebter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sook.dubedebter.ui.theme.DubeDebterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID
import java.util.Date
import java.text.DateFormat
import java.util.GregorianCalendar
import java.util.TimeZone
import androidx.compose.ui.res.stringArrayResource
import java.util.Locale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DubeDebterTheme { LocalizedCustomerApp() } }
    }
}

@Composable
internal fun CustomerTab.openedLabel(): String =
    openedAt?.let { dualDateLabel(it, R.string.opened_dual_date) }
        ?: stringResource(R.string.unknown_date)

@Composable
internal fun dualDateLabel(timestamp: Long, labelResource: Int): String {
    val locale = LocalConfiguration.current.localesCompat()
    return timestamp.let { timestamp ->
        val zone = TimeZone.getDefault()
        val gregorian = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply {
            calendar = GregorianCalendar(zone, locale)
        }.format(Date(timestamp))
        val ethiopian = ethiopianDate(timestamp, zone)
        val months = stringArrayResource(R.array.ethiopian_months)
        val ethiopianLabel = stringResource(
            R.string.ethiopian_date_value,
            months[ethiopian.month - 1], ethiopian.day, ethiopian.year
        )
        val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply {
            timeZone = zone
        }.format(Date(timestamp))
        stringResource(labelResource, gregorian, ethiopianLabel, time)
    }
}

@Suppress("DEPRECATION")
private fun Configuration.localesCompat(): Locale =
    if (android.os.Build.VERSION.SDK_INT >= 24) locales[0] else locale

@Composable
private fun LocalizedCustomerApp() {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var language by rememberSaveable { mutableStateOf("en") }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = baseContext.getSharedPreferences("customer_ledger", 0)
        language = prefs.getString("language", "en") ?: "en"
        showOnboarding = !prefs.getBoolean("onboarding_completed", false)
        ready = true
    }
    if (!ready) return
    val locale = remember(language) { Locale.forLanguageTag(language) }
    val configuration = remember(baseConfiguration, language) {
        Configuration(baseConfiguration).apply { setLocale(locale) }
    }
    val localizedContext = remember(baseContext, configuration, language) {
        Locale.setDefault(locale)
        @Suppress("DEPRECATION")
        baseContext.resources.updateConfiguration(configuration, baseContext.resources.displayMetrics)
        baseContext.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration
    ) {
        if (showOnboarding) {
            OnboardingScreen(
                currentLanguage = language,
                onLanguageChange = { selected ->
                    language = selected
                    scope.launch(Dispatchers.IO) {
                        baseContext.getSharedPreferences("customer_ledger", 0).edit()
                            .putString("language", selected).apply()
                    }
                },
                onFinish = {
                    showOnboarding = false
                    scope.launch(Dispatchers.IO) {
                        baseContext.getSharedPreferences("customer_ledger", 0).edit()
                            .putBoolean("onboarding_completed", true).apply()
                    }
                }
            )
        } else {
            CustomerApp(
                language = language,
                onLanguageChange = { selected ->
                    language = selected
                    scope.launch(Dispatchers.IO) {
                        baseContext.getSharedPreferences("customer_ledger", 0).edit()
                            .putString("language", selected).apply()
                    }
                },
                onShowOnboarding = {
                    showOnboarding = true
                }
            )
        }
    }
}

private fun BigDecimal.display() = stripTrailingZeros().toPlainString()

private fun encode(customers: List<Customer>): String = JSONArray().apply {
    customers.forEach { customer ->
        put(JSONObject().apply {
            put("id", customer.id)
            put("name", customer.name)
            put("phone", customer.phone)
            put("memo", customer.memo)
            put("credit", customer.credit.toPlainString())
            put("tabs", JSONArray().apply {
                customer.tabs.forEach { tab ->
                    put(JSONObject().apply {
                        put("id", tab.id)
                        put("name", tab.name)
                        put("openedAt", tab.openedAt ?: JSONObject.NULL)
                        put("closed", tab.closed)
                        put("paid", tab.paid.toPlainString())
                        put("payments", JSONArray().apply {
                            tab.payments.forEach { payment ->
                                put(JSONObject().apply {
                                    put("id", payment.id)
                                    put("amount", payment.amount.toPlainString())
                                    put("recordedAt", payment.recordedAt)
                                })
                            }
                        })
                        put("items", JSONArray().apply {
                            tab.items.forEach { item ->
                                put(JSONObject().apply {
                                    put("id", item.id)
                                    put("name", item.name)
                                    put("amount", item.quantity.toPlainString())
                                    put("unitPrice", item.price.toPlainString())
                                })
                            }
                        })
                    })
                }
            })
        })
    }
}.toString()

private fun decode(value: String): List<Customer> {
    val customers = JSONArray(value)
    return List(customers.length()) { index ->
        val customer = customers.getJSONObject(index)
        fun readItems(items: JSONArray): List<Item> = List(items.length()) { row ->
            val item = items.getJSONObject(row)
            Item(item.getString("id"), item.getString("name"),
                item.getString("amount").toBigDecimal(), item.getString("unitPrice").toBigDecimal())
        }
        val tabs = if (customer.has("tabs")) {
            val storedTabs = customer.getJSONArray("tabs")
            List(storedTabs.length()) { tabIndex ->
                val tab = storedTabs.getJSONObject(tabIndex)
                val items = readItems(tab.getJSONArray("items"))
                val payments = tab.optJSONArray("payments") ?: JSONArray()
                // A legacy closed flag is not evidence that money was received.
                val paid = tab.optString("paid", "0").toBigDecimal()
                CustomerTab(
                    id = tab.getString("id"),
                    name = tab.optString("name", "").ifBlank { "Tab ${tabIndex + 1}" },
                    openedAt = if (tab.isNull("openedAt")) null else tab.getLong("openedAt"),
                    closed = tab.optBoolean("closed", false) && paid >= items.sumOf { it.total },
                    items = items,
                    paid = paid,
                    payments = List(payments.length()) { index ->
                        val payment = payments.getJSONObject(index)
                        Payment(payment.getString("id"), payment.getString("amount").toBigDecimal(),
                            payment.getLong("recordedAt"))
                    }
                )
            }
        } else {
            val oldItems = readItems(customer.getJSONArray("items"))
            if (oldItems.isEmpty()) emptyList() else listOf(
                CustomerTab(id = customer.getString("id") + "-existing", openedAt = null, items = oldItems)
            )
        }
        Customer(
            customer.getString("id"), customer.getString("name"), tabs,
            phone = customer.optString("phone", ""),
            memo = customer.optString("memo", ""),
            credit = customer.optString("credit", "0").toBigDecimal()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerApp(
    language: String,
    onLanguageChange: (String) -> Unit,
    onShowOnboarding: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyOpen by rememberSaveable(selectedId) { mutableStateOf(false) }
    var showAllTabs by rememberSaveable(selectedId) { mutableStateOf(false) }
    var paymentOpen by rememberSaveable { mutableStateOf(false) }
    var openingTab by rememberSaveable { mutableStateOf(false) }
    var register by rememberSaveable { mutableStateOf(false) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportOpen by rememberSaveable(selectedId) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    val paymentSaved = stringResource(R.string.payment_saved)
    val itemSaved = stringResource(R.string.item_saved)
    val customerSaved = stringResource(R.string.customer_saved)
    var coachMarkStep by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        try {
            val prefs = context.getSharedPreferences("customer_ledger", 0)
            customers = withContext(Dispatchers.IO) {
                val json = prefs.getString("customers", null)
                if (json == null) emptyList() else decode(json)
            }
            loaded = true
        } catch (_: Exception) {
            error = R.string.load_error
        }
    }

    fun save(updated: List<Customer>, onSuccess: () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    context.getSharedPreferences("customer_ledger", 0).edit()
                        .putString("customers", encode(updated)).commit()
                }
                if (saved) {
                    customers = updated
                    error = null
                    onSuccess()
                } else error = R.string.save_error
            } catch (_: Exception) {
                error = R.string.save_error
            } finally {
                saving = false
            }
        }
    }

    val customer = customers.firstOrNull { it.id == selectedId }
    val tab = customer?.tabs?.firstOrNull { it.id == selectedTabId }
    fun goBack() {
        if (historyOpen) historyOpen = false
        else if (selectedTabId != null) selectedTabId = null else selectedId = null
    }
    fun saveTabItems(items: List<Item>) {
        if (customer == null || tab == null || tab.closed) return
        val updated = customer.updateTabItems(tab.id, items)
        save(customers.map { if (it.id == customer.id) updated else it }) {
            editorOpen = false
            scope.launch { snackbar.showSnackbar(itemSaved) }
        }
    }

    BackHandler(customer != null && !editorOpen && !openingTab && !paymentOpen && !exportOpen && !register) { if (!saving) goBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(if (historyOpen) stringResource(R.string.payment_history_title) else tab?.name ?: customer?.name ?: stringResource(R.string.customers), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (customer != null) TextButton(enabled = !saving, onClick = { goBack() }) { Text(stringResource(R.string.back)) }
                    },
                    actions = {
                        LedgerAppMenu(
                            language = language, enabled = !saving,
                            canExport = loaded && customers.isNotEmpty(),
                            customerSelected = customer != null,
                            onExport = { exportOpen = true },
                            onLanguageChange = onLanguageChange,
                            onGuide = onShowOnboarding,
                            onTour = { coachMarkStep = 0 }
                        )
                    }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f)) {
                val screenKey = when {
                    customer == null -> "customers"
                    historyOpen -> "history:${customer.id}"
                    tab == null -> "customer:${customer.id}"
                    else -> "tab:${tab.id}"
                }
                screenStates.SaveableStateProvider(screenKey) {
            if (!loaded) {
                if (error == null) CircularProgressIndicator()
            } else if (customer == null) {
                CustomerListScreen(customers, saving,
                    onRegister = { register = true },
                    onSelect = { selectedId = it; selectedTabId = null })
            } else if (historyOpen) {
                CustomerPaymentHistory(customer)
            } else if (tab == null) {
                CustomerTabsScreen(customer, showAllTabs, saving,
                    onShowAll = { showAllTabs = it },
                    onOpen = { openingTab = true },
                    onSelect = { selectedTabId = it },
                    onHistory = { historyOpen = true })
            } else {
                TabDetailScreen(customer, tab, saving,
                    onAdd = { editingId = null; editorOpen = true },
                    onEdit = { editingId = it; editorOpen = true },
                    onPayment = { paymentOpen = true },
                    onReopen = {
                        save(customers.map { entry ->
                            if (entry.id == customer.id) entry.copy(tabs = entry.tabs.map {
                                if (it.id == tab.id) it.copy(closed = false) else it
                            }) else entry
                        }) {}
                    })
            }
                }
            }
        }
    }

    if (exportOpen) ExportReportDialog(customers, customer, onDismiss = { exportOpen = false })

    if (paymentOpen && customer != null && tab != null && !tab.closed) {
        PaymentDialog(customer, tab, saving, error, onDismiss = { paymentOpen = false }) { payment ->
            val updated = customer.recordPayment(tab.id, payment)
            save(customers.map { if (it.id == customer.id) updated else it }) {
                paymentOpen = false
                scope.launch { snackbar.showSnackbar(paymentSaved) }
            }
        }
    }

    if (openingTab && customer != null) {
        var name by rememberSaveable(customer.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!saving) openingTab = false },
            title = { Text(stringResource(R.string.open_tab)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.tab_name)) },
                        placeholder = { Text(stringResource(R.string.tab_example)) },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.date_help))
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !saving,
                    onClick = {
                        val newTab = CustomerTab(name = name.trim())
                        save(customers.map {
                            if (it.id == customer.id) it.copy(tabs = it.tabs + newTab) else it
                        }) {
                            selectedTabId = newTab.id
                            openingTab = false
                        }
                    }
                ) { Text(stringResource(if (saving) R.string.opening else R.string.open_tab_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { openingTab = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (register) {
        var name by rememberSaveable { mutableStateOf("") }
        var phone by rememberSaveable { mutableStateOf("") }
        var memo by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!saving) register = false },
            title = { Text(stringResource(R.string.register_customer)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text(stringResource(R.string.customer_name)) },
                        supportingText = { Text(stringResource(R.string.name_required)) },
                        singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        phone, { phone = it }, label = { Text(stringResource(R.string.phone)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        memo, { memo = it }, label = { Text(stringResource(R.string.memo)) },
                        minLines = 3, maxLines = 5, enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && !saving, onClick = {
                    val registered = Customer(name = name.trim(), phone = phone.trim(), memo = memo.trim())
                    save(customers + registered) {
                        register = false
                        selectedId = registered.id
                        selectedTabId = null
                        scope.launch { snackbar.showSnackbar(customerSaved) }
                    }
                }) { Text(stringResource(if (saving) R.string.saving else R.string.register)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { register = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (editorOpen && tab != null && !tab.closed) {
        val existing = tab.items.firstOrNull { it.id == editingId }
        ItemEditor(existing, saving, error, onDismiss = { editorOpen = false },
            onSave = { item ->
                val items = if (existing == null) tab.items + item
                    else tab.items.map { if (it.id == item.id) item else it }
                saveTabItems(items)
            },
            onDelete = {
                saveTabItems(tab.items.filterNot { it.id == editingId })
            })
    }

    coachMarkStep?.let { step ->
        CoachMarkOverlay(
            stepIndex = step,
            onNextStep = {
                if (step < 3) {
                    coachMarkStep = step + 1
                } else {
                    coachMarkStep = null
                    scope.launch(Dispatchers.IO) {
                        context.getSharedPreferences("customer_ledger", 0).edit()
                            .putBoolean("coach_marks_shown", true).apply()
                    }
                }
            },
            onSkipTour = {
                coachMarkStep = null
                scope.launch(Dispatchers.IO) {
                    context.getSharedPreferences("customer_ledger", 0).edit()
                        .putBoolean("coach_marks_shown", true).apply()
                }
            }
        )
    }
}
}

@Composable
private fun CustomerPaymentHistory(customer: Customer) {
    val history = remember(customer) { customer.paymentHistory() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item { Text(customer.name, style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.payments_total), style = MaterialTheme.typography.labelLarge)
                    Text(history.sumOf { it.payment.amount }.display(), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.customer_credit, customer.credit.display()))
                }
            }
        }
        if (history.isEmpty()) {
            item { Text(stringResource(R.string.no_payments)) }
        }
        itemsIndexed(history, key = { _, entry -> entry.payment.id }) { _, entry ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.received_amount, entry.payment.amount.display()),
                        style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.payment_tab_name, entry.tabName))
                    Text(dualDateLabel(entry.payment.recordedAt, R.string.payment_dual_date),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun OverdueBadge() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            stringResource(R.string.overdue_badge),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ClosedTabBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            stringResource(R.string.closed_tab),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ItemEditor(
    item: Item?,
    saving: Boolean,
    error: Int?,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var quantity by rememberSaveable(item?.id) { mutableStateOf(item?.quantity?.display().orEmpty()) }
    var price by rememberSaveable(item?.id) { mutableStateOf(item?.price?.display().orEmpty()) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    fun number(text: String): BigDecimal? =
        text.takeIf { it.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?")) }?.toBigDecimalOrNull()
    val amount = number(quantity)?.takeIf { it > BigDecimal.ZERO }
    val unitPrice = number(price)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(if (item == null) R.string.add_item else R.string.edit_item)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    name, { name = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.item_name)) }, singleLine = true, enabled = !saving
                )
                OutlinedTextField(
                    quantity, { quantity = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.quantity)) }, singleLine = true, enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = quantity.isNotEmpty() && amount == null,
                    supportingText = { Text(stringResource(R.string.quantity_help)) }
                )
                OutlinedTextField(
                    price, { price = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.unit_price)) }, singleLine = true, enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = price.isNotEmpty() && unitPrice == null,
                    supportingText = { Text(stringResource(R.string.price_help)) }
                )
                Text(
                    stringResource(R.string.total_value, if (amount != null && unitPrice != null) (amount * unitPrice).display() else "—"),
                    style = MaterialTheme.typography.titleLarge
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (item != null) TextButton(enabled = !saving, onClick = { deleting = true }) {
                    Text(stringResource(R.string.delete_item), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && amount != null && unitPrice != null && !saving,
                onClick = {
                    if (amount != null && unitPrice != null)
                        onSave(Item(item?.id ?: UUID.randomUUID().toString(), name.trim(), amount, unitPrice))
                }) { Text(stringResource(if (saving) R.string.saving else R.string.save_item)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
    if (deleting) AlertDialog(
        onDismissRequest = { if (!saving) deleting = false },
        title = { Text(stringResource(R.string.delete_question)) },
        text = { Text(stringResource(R.string.delete_message, item?.name.orEmpty())) },
        confirmButton = { TextButton(enabled = !saving, onClick = onDelete) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } }
    )
}


@Composable
private fun PaymentDialog(
    customer: Customer,
    tab: CustomerTab,
    saving: Boolean,
    error: Int?,
    onDismiss: () -> Unit,
    onRecord: (Payment) -> Unit
) {
    var amountText by rememberSaveable(tab.id) { mutableStateOf("") }
    val paymentId = rememberSaveable(tab.id) { UUID.randomUUID().toString() }
    val amount = parsePaymentAmount(amountText)
    val available = customer.credit + (amount ?: BigDecimal.ZERO)
    val remaining = (tab.balance - available).max(BigDecimal.ZERO)
    val futureCredit = (available - tab.balance).max(BigDecimal.ZERO)
    val valid = amount != null && (amount > BigDecimal.ZERO || tab.balance <= customer.credit)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.record_payment)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(tab.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.balance_due, tab.balance.display()))
                Text(stringResource(R.string.customer_credit, customer.credit.display()))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.payment_amount)) },
                    supportingText = { Text(stringResource(R.string.payment_help)) },
                    isError = amountText.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                if (valid) {
                    Text(stringResource(R.string.remaining_after_payment, remaining.display()))
                    Text(stringResource(R.string.credit_after_payment, futureCredit.display()))
                    Text(stringResource(if (remaining.signum() == 0) R.string.payment_closes_tab else R.string.payment_leaves_open))
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !saving, onClick = {
                amount?.let { onRecord(Payment(id = paymentId, amount = it)) }
            }) { Text(stringResource(if (saving) R.string.saving else R.string.record_payment)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
