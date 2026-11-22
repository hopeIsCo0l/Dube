package com.sook.dubedebter

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun ExportReportAction(customers: List<Customer>, customer: Customer?, enabled: Boolean) {
    val context = LocalContext.current
    val allTitle = stringResource(R.string.export_all_title)
    val customerTitle = stringResource(R.string.export_customer_title, customer?.name.orEmpty())
    val chooserTitle = stringResource(R.string.export_report)
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var wholeLedger by rememberSaveable(customer?.id) { mutableStateOf(customer == null) }
    var images by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(enabled = enabled && !working, onClick = { failure = null; open = true }) {
            Text(stringResource(R.string.export_report))
        }
    }

    if (open) AlertDialog(
        onDismissRequest = { if (!working) open = false },
        title = { Text(stringResource(R.string.export_report)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.export_scope), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    if (customer != null) ExportChoice(
                        stringResource(R.string.export_customer, customer.name),
                        selected = !wholeLedger, enabled = !working,
                        onClick = { wholeLedger = false }
                    )
                    ExportChoice(
                        stringResource(R.string.export_everyone, customers.size),
                        selected = wholeLedger, enabled = !working,
                        onClick = { wholeLedger = true }
                    )
                }
                HorizontalDivider()
                Text(stringResource(R.string.export_format), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    ExportChoice(
                        stringResource(R.string.export_excel),
                        stringResource(R.string.export_excel_detail),
                        selected = !images, enabled = !working, onClick = { images = false }
                    )
                    ExportChoice(
                        stringResource(R.string.export_images),
                        stringResource(R.string.export_images_detail),
                        selected = images, enabled = !working, onClick = { images = true }
                    )
                }
                Text(stringResource(R.string.export_details), style = MaterialTheme.typography.bodySmall)
                if (working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.export_creating))
                }
                failure?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working && enabled, onClick = {
                // Capture the scope before suspending, including closed tabs and hidden history.
                val snapshot = if (wholeLedger || customer == null) customers.toList() else listOf(customer)
                val title = if (wholeLedger || customer == null) allTitle else customerTitle
                val imageFormat = images
                working = true
                failure = null
                scope.launch {
                    var directory: File? = null
                    var shared = false
                    try {
                        val files = withContext(Dispatchers.IO) {
                            val job = coroutineContext
                            val root = File(context.cacheDir, "reports")
                            check(root.isDirectory || root.mkdirs())
                            // Keep recent files available to receivers; remove only stale export sessions.
                            root.listFiles()?.filter {
                                it.lastModified() < System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                            }?.forEach { it.deleteRecursively() }
                            val folder = File(root, UUID.randomUUID().toString())
                            directory = folder
                            check(folder.mkdirs())
                            val report = LedgerReport(snapshot, title)
                            if (imageFormat) ImageReportWriter.write(report, folder) { job.ensureActive() }
                            else {
                                val file = File(folder, "dube-debter-ledger.xlsx")
                                file.outputStream().use { output ->
                                    ExcelReportWriter.write(report, output) { job.ensureActive() }
                                }
                                listOf(file)
                            }
                        }
                        val uris = ArrayList(files.map {
                            FileProvider.getUriForFile(context, "${context.packageName}.reports", it)
                        })
                        val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                            type = if (imageFormat) "image/png" else
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
                            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            clipData = ClipData.newUri(context.contentResolver, title, uris.first()).apply {
                                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                            }
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, chooserTitle)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        shared = true
                        open = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: ImageReportTooLarge) {
                        failure = R.string.export_image_limit
                    } catch (_: Exception) {
                        failure = R.string.export_failed
                    } finally {
                        if (!shared) withContext(NonCancellable + Dispatchers.IO) {
                            directory?.deleteRecursively()
                        }
                        working = false
                    }
                }
            }) { Text(stringResource(R.string.export_continue)) }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = { open = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ExportChoice(
    title: String,
    detail: String? = null,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().selectable(
            selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick
        ).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
