package com.sook.dubedebter

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun LedgerAppMenu(
    language: String,
    enabled: Boolean,
    canExport: Boolean,
    customerSelected: Boolean,
    onExport: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onGuide: () -> Unit,
    onTour: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var languages by remember { mutableStateOf(false) }
    val more = stringResource(R.string.more_options)
    val labels = linkedMapOf("en" to "English", "am" to "አማርኛ", "stv" to "ስልጤኛ", "sgw" to "ጉራጌኛ")
    fun close() { expanded = false; languages = false }
    Box {
        IconButton(enabled = enabled, onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = more }) {
            Text("⋮", style = MaterialTheme.typography.headlineMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { close() }) {
            if (languages) {
                DropdownMenuItem(text = { Text(stringResource(R.string.back)) }, onClick = { languages = false })
                labels.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { RadioButton(selected = code == language, onClick = null) },
                        onClick = { close(); onLanguageChange(code) }
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(if (customerSelected) R.string.export_customer_action else R.string.export_ledger_action)) },
                    enabled = canExport,
                    onClick = { close(); onExport() }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.language_setting, labels[language] ?: "English")) },
                    onClick = { languages = true }
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.quick_guide)) },
                    onClick = { close(); onGuide() })
                DropdownMenuItem(text = { Text(stringResource(R.string.feature_tour)) },
                    onClick = { close(); onTour() })
            }
        }
    }
}
