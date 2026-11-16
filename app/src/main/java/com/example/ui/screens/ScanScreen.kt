package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ShoeItem
import com.example.ui.inventory.ScanLookupState
import com.example.ui.scanner.BarcodeCameraPreview

@Composable
fun ScanScreen(
  scanState: ScanLookupState,
  inventoryCount: Int,
  linkedBarcodeCount: Int,
  onCodeScannedOrSubmitted: (String) -> Unit,
  onClearScanResult: () -> Unit,
  onAdjustQuantity: (ShoeItem, Long) -> Unit,
  onEditItem: (ShoeItem) -> Unit,
  onLinkToSuggestedSku: (barcode: String, item: ShoeItem, discoveredModel: String?) -> Unit,
  onOpenLinkSkuPicker: (barcode: String, discoveredModel: String?) -> Unit,
  onAddNewModelFromBarcode: (barcode: String, discoveredModel: String?) -> Unit,
  onNavigateToImport: () -> Unit,
) {
  var manualCode by remember { mutableStateOf("") }
  var showCamera by remember { mutableStateOf(true) }
  val focusManager = LocalFocusManager.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Manual / Bluetooth Scanner input row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = manualCode,
          onValueChange = { manualCode = it },
          placeholder = { Text(stringResource(R.string.scan_hint)) },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (manualCode.isNotEmpty()) {
              IconButton(onClick = { manualCode = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear input")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions =
            KeyboardActions(
              onSearch = {
                focusManager.clearFocus()
                onCodeScannedOrSubmitted(manualCode)
              }
            ),
          modifier = Modifier.weight(1f).testTag("scan_manual_input"),
        )
        FilledTonalButton(
          onClick = {
            focusManager.clearFocus()
            onCodeScannedOrSubmitted(manualCode)
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(54.dp).testTag("scan_lookup_button"),
        ) {
          Text("Find")
        }
      }

      // Camera Scanner toggle header + Viewport
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Live Barcode Scanner",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
        TextButton(
          onClick = { showCamera = !showCamera },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_camera_button"),
        ) {
          Icon(
            imageVector = if (showCamera) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (showCamera) "Hide" else "Show")
        }
      }

      if (showCamera) {
        BarcodeCameraPreview(
          onBarcodeDetected = { detected ->
            manualCode = detected
            onCodeScannedOrSubmitted(detected)
          },
          modifier = Modifier.fillMaxWidth().height(230.dp),
        )
      }

      // Instant Bin Result Section
      AnimatedContent(targetState = scanState, label = "scan_result_anim") { state ->
        when (state) {
          is ScanLookupState.Idle -> {
            IdleScanSummaryCard(
              inventoryCount = inventoryCount,
              linkedBarcodeCount = linkedBarcodeCount,
              onNavigateToImport = onNavigateToImport,
            )
          }
          is ScanLookupState.Searching -> {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                Column {
                  Text(
                    text = "Looking up barcode…",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = state.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          is ScanLookupState.Matched -> {
            MatchedBinCard(
              scannedCode = state.scannedCode,
              matchSource = state.matchSource,
              item = state.item,
              onAdjustQuantity = onAdjustQuantity,
              onEditItem = onEditItem,
              onClear = onClearScanResult,
            )
          }
          is ScanLookupState.Unmatched -> {
            UnmatchedBarcodeCard(
              state = state,
              hasInventory = inventoryCount > 0,
              onLinkSuggested = { item ->
                onLinkToSuggestedSku(state.scannedCode, item, state.onlineMetadata?.title)
              },
              onOpenLinkPicker = {
                onOpenLinkSkuPicker(state.scannedCode, state.onlineMetadata?.title)
              },
              onAddNewModel = {
                onAddNewModelFromBarcode(state.scannedCode, state.onlineMetadata?.title)
              },
              onClear = onClearScanResult,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun IdleScanSummaryCard(
  inventoryCount: Int,
  linkedBarcodeCount: Int,
  onNavigateToImport: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Inventory2,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = if (inventoryCount == 0) "No Stock Loaded Yet" else "Ready to Scan",
          style = MaterialTheme.typography.titleMedium,
        )
      }
      Text(
        text =
          if (inventoryCount == 0) {
            "Import your Excel (.xlsx) or CSV stock sheet (Col 1: SKU, Col 2: Bin) or add shoe models manually to start matching barcodes."
          } else {
            "$inventoryCount SKUs loaded in cloud inventory ($linkedBarcodeCount product barcodes linked). Point the camera at a shoe box barcode or type a code above."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (inventoryCount == 0) {
        FilledTonalButton(
          onClick = onNavigateToImport,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("idle_import_cta"),
        ) {
          Text("Import Excel / CSV Stock Sheet")
        }
      }
    }
  }
}

@Composable
private fun MatchedBinCard(
  scannedCode: String,
  matchSource: String,
  item: ShoeItem,
  onAdjustQuantity: (ShoeItem, Long) -> Unit,
  onEditItem: (ShoeItem) -> Unit,
  onClear: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
    modifier = Modifier.fillMaxWidth().testTag("matched_bin_card"),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(99.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = matchSource,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
        }
        IconButton(onClick = onClear) {
          Icon(Icons.Rounded.Close, contentDescription = "Dismiss result")
        }
      }

      // Huge unmistakable BIN hero display
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = stringResource(R.string.bin_label),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = item.binNumber,
              style = MaterialTheme.typography.displaySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.testTag("matched_bin_number"),
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "SKU",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = item.sku,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }

      if (item.modelName.isNotBlank()) {
        Text(
          text = item.modelName,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Text(
        text = "Scanned code: $scannedCode",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilledTonalIconButton(
            onClick = { onAdjustQuantity(item, -1L) },
            enabled = item.quantity > 0L,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_qty_minus"),
          ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease stock")
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = item.quantity.toString(),
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "In Bin",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FilledTonalIconButton(
            onClick = { onAdjustQuantity(item, 1L) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_qty_plus"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase stock")
          }
        }

        OutlinedButton(
          onClick = { onEditItem(item) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(48.dp).testTag("scan_edit_item_button"),
        ) {
          Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.edit_item))
        }
      }
    }
  }
}

@Composable
private fun UnmatchedBarcodeCard(
  state: ScanLookupState.Unmatched,
  hasInventory: Boolean,
  onLinkSuggested: (ShoeItem) -> Unit,
  onOpenLinkPicker: () -> Unit,
  onAddNewModel: () -> Unit,
  onClear: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth().testTag("unmatched_barcode_card"),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Barcode Not Linked Yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = state.scannedCode,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(onClick = onClear) {
          Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
        }
      }

      if (state.onlineMetadata != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "ONLINE PRODUCT MATCH",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = state.onlineMetadata.title.ifBlank { state.onlineMetadata.brand },
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.onlineMetadata.model.isNotBlank()) {
              Text(
                text = "Model ref: ${state.onlineMetadata.model}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        Text(
          text = "Link this product barcode to an existing SKU from your stock sheet so future scans show its bin immediately.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (state.suggestedSkus.isNotEmpty()) {
        Text(
          text = "Suggested SKUs",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        state.suggestedSkus.forEach { suggestion ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${suggestion.sku}  •  Bin ${suggestion.binNumber}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (suggestion.modelName.isNotBlank()) {
                  Text(
                    text = suggestion.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              FilledTonalButton(
                onClick = { onLinkSuggested(suggestion) },
                shape = RoundedCornerShape(10.dp),
              ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Link")
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (hasInventory) {
          OutlinedButton(
            onClick = onOpenLinkPicker,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("pick_sku_to_link_button"),
          ) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.link_to_sku))
          }
        }
        Button(
          onClick = onAddNewModel,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("add_new_from_scan_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.add_model))
        }
      }
    }
  }
}
