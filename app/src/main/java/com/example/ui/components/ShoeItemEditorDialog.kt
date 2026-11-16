package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ShoeItem
import com.example.ui.scanner.BarcodeCameraPreview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShoeItemEditorSheet(
  existingItem: ShoeItem?,
  prefillBarcode: String? = null,
  prefillModelName: String? = null,
  onDismiss: () -> Unit,
  onSave: (
    existingId: String?,
    sku: String,
    binNumber: String,
    modelName: String,
    barcodes: List<String>,
    quantity: Long,
    notes: String,
  ) -> Unit,
  onDelete: ((ShoeItem) -> Unit)? = null,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var sku by remember(existingItem) { mutableStateOf(existingItem?.sku.orEmpty()) }
  var binNumber by remember(existingItem) { mutableStateOf(existingItem?.binNumber.orEmpty()) }
  var modelName by remember(existingItem, prefillModelName) {
    mutableStateOf(existingItem?.modelName?.ifBlank { prefillModelName.orEmpty() } ?: prefillModelName.orEmpty())
  }
  var quantity by remember(existingItem) { mutableLongStateOf(existingItem?.quantity ?: 1L) }
  var notes by remember(existingItem) { mutableStateOf(existingItem?.notes.orEmpty()) }
  val barcodes = remember(existingItem, prefillBarcode) {
    mutableStateListOf<String>().apply {
      existingItem?.barcodes?.let { addAll(it) }
      if (!prefillBarcode.isNullOrBlank() && !contains(prefillBarcode.trim())) {
        add(prefillBarcode.trim())
      }
    }
  }
  var newBarcodeInput by remember { mutableStateOf("") }
  var showInlineScanner by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (existingItem == null) "Add Model to Bin" else "Edit SKU & Bin",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (existingItem != null && onDelete != null) {
          IconButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_item_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteOutline,
              contentDescription = "Delete model",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      if (validationError != null) {
        Text(
          text = validationError.orEmpty(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = sku,
          onValueChange = {
            sku = it
            validationError = null
          },
          label = { Text("SKU *") },
          placeholder = { Text("NK-AM90-BLK-10") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
          modifier = Modifier.weight(1.3f).testTag("editor_sku_input"),
        )
        OutlinedTextField(
          value = binNumber,
          onValueChange = {
            binNumber = it
            validationError = null
          },
          label = { Text("Bin # *") },
          placeholder = { Text("A-12") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
          modifier = Modifier.weight(1f).testTag("editor_bin_input"),
        )
      }

      OutlinedTextField(
        value = modelName,
        onValueChange = { modelName = it },
        label = { Text("Shoe Model / Brand") },
        placeholder = { Text("Nike Air Max 90 Black (Size 10)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("editor_model_input"),
      )

      // Stock quantity stepper
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Stock Quantity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Pairs currently in this bin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FilledTonalIconButton(
            onClick = { if (quantity > 0L) quantity-- },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_qty_minus"),
          ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease quantity")
          }
          Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          FilledTonalIconButton(
            onClick = { quantity++ },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_qty_plus"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase quantity")
          }
        }
      }

      // Linked Barcodes section
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Linked Product Barcodes",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (barcodes.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            barcodes.forEach { code ->
              InputChip(
                selected = false,
                onClick = {},
                label = {
                  Text(
                    text = code,
                    style = MaterialTheme.typography.labelMedium,
                  )
                },
                trailingIcon = {
                  IconButton(
                    onClick = { barcodes.remove(code) },
                    modifier = Modifier.size(18.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = "Remove barcode $code",
                      modifier = Modifier.size(14.dp),
                    )
                  }
                },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = newBarcodeInput,
            onValueChange = { newBarcodeInput = it },
            label = { Text("Add UPC / EAN / Barcode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.weight(1f).testTag("editor_barcode_input"),
          )
          FilledTonalIconButton(
            onClick = {
              val clean = newBarcodeInput.trim()
              if (clean.isNotEmpty() && !barcodes.contains(clean)) {
                barcodes.add(clean)
              }
              newBarcodeInput = ""
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_add_barcode_btn"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add barcode")
          }
          FilledTonalIconButton(
            onClick = { showInlineScanner = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_scan_barcode_btn"),
          ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan barcode with camera")
          }
        }
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Notes (optional)") },
        placeholder = { Text("Shelf level, color variant, size run") },
        maxLines = 2,
        modifier = Modifier.fillMaxWidth().testTag("editor_notes_input"),
      )

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).height(50.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Cancel")
        }
        Button(
          onClick = {
            if (sku.trim().isEmpty() || binNumber.trim().isEmpty()) {
              validationError = "Both SKU and Bin Number are required."
              return@Button
            }
            val pendingBarcode = newBarcodeInput.trim()
            if (pendingBarcode.isNotEmpty() && !barcodes.contains(pendingBarcode)) {
              barcodes.add(pendingBarcode)
            }
            onSave(
              existingItem?.id,
              sku.trim(),
              binNumber.trim(),
              modelName.trim(),
              barcodes.toList(),
              quantity,
              notes.trim(),
            )
            onDismiss()
          },
          modifier = Modifier.weight(1.3f).height(50.dp).testTag("editor_save_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(if (existingItem == null) "Save to Bin" else "Save Changes")
        }
      }
    }
  }

  if (showInlineScanner) {
    Dialog(onDismissRequest = { showInlineScanner = false }) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BarcodeCameraPreview(
          onBarcodeDetected = { scanned ->
            if (!barcodes.contains(scanned)) {
              barcodes.add(scanned)
            }
            showInlineScanner = false
          },
          modifier = Modifier.fillMaxWidth().height(280.dp),
        )
        OutlinedButton(
          onClick = { showInlineScanner = false },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Close Scanner")
        }
      }
    }
  }

  if (confirmDelete && existingItem != null && onDelete != null) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Delete ${existingItem.sku}?") },
      text = {
        Text("This removes ${existingItem.sku} from Bin ${existingItem.binNumber} in your cloud inventory.")
      },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            confirmDelete = false
            onDelete(existingItem)
            onDismiss()
          },
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) {
          Text("Cancel")
        }
      },
    )
  }
}
