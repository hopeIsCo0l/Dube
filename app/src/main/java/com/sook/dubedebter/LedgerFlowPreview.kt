package com.sook.dubedebter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sook.dubedebter.ui.theme.DubeDebterTheme
import java.math.BigDecimal

@Preview(showBackground = true, widthDp = 360, heightDp = 680)
@Preview(showBackground = true, widthDp = 360, heightDp = 680, fontScale = 1.5f)
@Composable
private fun TabFlowPreview() {
    val tab = CustomerTab(name = "Weekly groceries",
        items = listOf(
            Item(name = "Coffee", quantity = BigDecimal("2"), price = BigDecimal("450")),
            Item(name = "Sugar", quantity = BigDecimal("1"), price = BigDecimal("120"))
        ), paid = BigDecimal("300"))
    DubeDebterTheme(dynamicColor = false) {
        Surface {
            Box(Modifier.padding(16.dp)) {
                TabDetailScreen(Customer(name = "Aster Bekele", tabs = listOf(tab)), tab, false, {}, {}, {}, {})
            }
        }
    }
}
