package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ShoeItem

@Composable
fun LinkSkuPickerDialog(
  barcode: String,
  inventory: List<ShoeItem>,
  onSelectSku: (ShoeItem) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(inventory, query) {
      val q = query.trim().lowercase()
      if (q.isEmpty()) inventory
      else
        inventory.filter {
          it.sku.lowercase().contains(q) ||
            it.binNumber.lowercase().contains(q) ||
            it.modelName.lowercase().contains(q)
        }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Link Barcode to SKU")
        Text(
          text = barcode,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search SKU, Bin, or Model…") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("link_sku_search_input"),
        )
        if (filtered.isEmpty()) {
          Text(
            text = "No matching SKUs in inventory yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(filtered, key = { it.id }) { item ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onSelectSku(item) }
                  .testTag("link_sku_option_${item.sku}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.sku,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (item.modelName.isNotBlank()) {
                      Text(
                        text = item.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                  ) {
                    Text(
                      text = item.binNumber,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
