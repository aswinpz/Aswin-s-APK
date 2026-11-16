package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.inventory.ImportStatus

@Composable
fun ImportScreen(
  importStatus: ImportStatus,
  savedSheetUrl: String,
  totalInventoryCount: Int,
  onPickSpreadsheet: (openStream: suspend () -> java.io.InputStream?, fileName: String?) -> Unit,
  onFetchFromUrl: (String) -> Unit,
  onConfirmImport: () -> Unit,
  onResetImportStatus: () -> Unit,
  onBuildExportCsv: () -> String,
) {
  val context = LocalContext.current
  var sheetUrlInput by remember(savedSheetUrl) { mutableStateOf(savedSheetUrl) }

  LaunchedEffect(savedSheetUrl) {
    if (sheetUrlInput.isBlank() && savedSheetUrl.isNotBlank()) {
      sheetUrlInput = savedSheetUrl
    }
  }

  val filePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        val fileName = resolveFileName(context, uri)
        onPickSpreadsheet(
          { context.contentResolver.openInputStream(uri) },
          fileName,
        )
      }
    }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Import Preview / Status Banner when active
      when (importStatus) {
        is ImportStatus.Parsing,
        is ImportStatus.Importing -> {
          Card(
            shape = RoundedCornerShape(18.dp),
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
              Text(
                text =
                  if (importStatus is ImportStatus.Parsing) "Reading spreadsheet rows…"
                  else "Syncing SKUs & bin locations to cloud…",
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
        }
        is ImportStatus.ReadyToImport -> {
          ImportPreviewCard(
            status = importStatus,
            onConfirm = onConfirmImport,
            onCancel = onResetImportStatus,
          )
        }
        is ImportStatus.Completed -> {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().testTag("import_completed_card"),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                  text = "Stock Sheet Synced",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
              Text(
                text =
                  "Added ${importStatus.summary.createdCount} new SKUs, updated ${importStatus.summary.updatedCount} existing bins" +
                    if (importStatus.summary.skippedCount > 0) " (${importStatus.summary.skippedCount} blank rows skipped)."
                    else ".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
              )
              OutlinedButton(onClick = onResetImportStatus, shape = RoundedCornerShape(10.dp)) {
                Text("Done")
              }
            }
          }
        }
        is ImportStatus.Failed -> {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = importStatus.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              OutlinedButton(onClick = onResetImportStatus) {
                Text("Dismiss")
              }
            }
          }
        }
        ImportStatus.Idle -> Unit
      }

      // Card 1: Local Excel / CSV File Upload
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
              imageVector = Icons.Outlined.UploadFile,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Import Stock Excel / CSV",
              style = MaterialTheme.typography.titleLarge,
            )
          }
          Text(
            text = "Load your warehouse stock sheet directly from your phone. Column 1 = SKU, Column 2 = Bin Number (optional Col 3/4 = Barcode & Model Name).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "COLUMN FORMAT EXAMPLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "SKU            | Bin Number\nNK-AM90-BLK-10 | A-12\nAD-UB22-WHT-09 | B-04",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          Button(
            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_excel_csv_button"),
          ) {
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.import_excel_csv))
          }
        }
      }

      // Card 2: Remote Google Sheet / CSV URL Fetch
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
              imageVector = Icons.Outlined.CloudDownload,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Sync from Sheet URL",
              style = MaterialTheme.typography.titleLarge,
            )
          }
          Text(
            text = "Paste a Google Sheets link (shared as viewer) or published CSV URL to pull live SKU and bin updates.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = sheetUrlInput,
            onValueChange = { sheetUrlInput = it },
            label = { Text("Google Sheets or CSV URL") },
            placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("sheet_url_input"),
          )
          FilledTonalButton(
            onClick = { onFetchFromUrl(sheetUrlInput) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("fetch_sheet_url_button"),
          ) {
            Text(stringResource(R.string.fetch_from_url))
          }
        }
      }

      // Card 3: Export Inventory with Linked Barcodes
      if (totalInventoryCount > 0) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Export Updated Sheet",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Share all $totalInventoryCount SKUs, updated bin locations, and linked product barcodes as CSV.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = {
                val csv = onBuildExportCsv()
                val sendIntent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "ShoeBin Inventory Export")
                    putExtra(Intent.EXTRA_TEXT, csv)
                  }
                context.startActivity(Intent.createChooser(sendIntent, "Export ShoeBin CSV"))
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_csv_button"),
            ) {
              Icon(Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.export_csv))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ImportPreviewCard(
  status: ImportStatus.ReadyToImport,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    modifier = Modifier.fillMaxWidth().testTag("import_preview_card"),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Preview: ${status.rows.size} rows from ${status.sourceLabel}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (status.skippedHeader) {
        Text(
          text = "Detected & skipped header row automatically.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      status.rows.take(5).forEach { row ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = row.sku,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Bin ${row.binNumber}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      if (status.rows.size > 5) {
        Text(
          text = "+ ${status.rows.size - 5} more rows",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onCancel,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text("Cancel")
        }
        Button(
          onClick = onConfirm,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1.4f).height(48.dp).testTag("confirm_import_button"),
        ) {
          Text("Sync ${status.rows.size} SKUs")
        }
      }
    }
  }
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String? {
  context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (nameIndex >= 0 && cursor.moveToFirst()) {
      return cursor.getString(nameIndex)
    }
  }
  return uri.lastPathSegment
}
