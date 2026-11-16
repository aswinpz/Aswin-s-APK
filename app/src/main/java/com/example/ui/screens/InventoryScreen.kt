package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ShoeItem
import com.example.ui.inventory.InventoryFilter
import com.example.ui.inventory.UiState

@Composable
fun InventoryScreen(
  inventoryState: UiState<List<ShoeItem>>,
  filteredItems: List<ShoeItem>,
  searchQuery: String,
  filterMode: InventoryFilter,
  onSearchQueryChange: (String) -> Unit,
  onFilterChange: (InventoryFilter) -> Unit,
  onItemClick: (ShoeItem) -> Unit,
  onAddNewClick: () -> Unit,
  onNavigateToImport: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 680.dp)
        .fillMaxSize()
        .padding(horizontal = 16.dp),
    ) {
      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Search SKU, Bin #, Model, or Barcode") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(Icons.Rounded.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("inventory_search_input"),
      )

      LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(InventoryFilter.entries) { filter ->
          FilterChip(
            selected = filterMode == filter,
            onClick = { onFilterChange(filter) },
            label = { Text(filter.label) },
            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
          )
        }
      }

      when (inventoryState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
              text = inventoryState.message,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        is UiState.Success -> {
          if (inventoryState.data.isEmpty()) {
            EmptyInventoryState(
              onAddNewClick = onAddNewClick,
              onNavigateToImport = onNavigateToImport,
            )
          } else if (filteredItems.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "No shoes match your current search or filter.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(top = 4.dp, bottom = 92.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(filteredItems, key = { it.id }) { item ->
                ShoeInventoryRowCard(
                  item = item,
                  onClick = { onItemClick(item) },
                )
              }
            }
          }
        }
      }
    }

    ExtendedFloatingActionButton(
      onClick = onAddNewClick,
      icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
      text = { Text(stringResource(R.string.add_model)) },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 18.dp, bottom = 18.dp)
        .testTag("add_model_fab"),
    )
  }
}

@Composable
private fun ShoeInventoryRowCard(
  item: ShoeItem,
  onClick: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("inventory_item_${item.sku}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f).padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = item.sku,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = item.modelName.ifBlank { "Unnamed Shoe Model" },
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.QrCode2,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
              tint =
                if (item.barcodes.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.tertiary,
            )
            Text(
              text =
                if (item.barcodes.isEmpty()) "No barcode"
                else "${item.barcodes.size} barcode${if (item.barcodes.size > 1) "s" else ""}",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (item.barcodes.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.tertiary,
            )
          }
          Text(
            text = "Qty: ${item.quantity}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = "BIN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text = item.binNumber,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyInventoryState(
  onAddNewClick: () -> Unit,
  onNavigateToImport: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.Inventory2,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(48.dp),
    )
    Spacer(modifier = Modifier.height(14.dp))
    Text(
      text = "Your Shoe Bin Inventory is Empty",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Import your Excel (.xlsx) or CSV sheet of SKUs and Bin numbers, or add your first model manually.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedButton(onClick = onNavigateToImport, shape = RoundedCornerShape(12.dp)) {
        Text("Import Sheet")
      }
      Button(onClick = onAddNewClick, shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.add_model))
      }
    }
  }
}
