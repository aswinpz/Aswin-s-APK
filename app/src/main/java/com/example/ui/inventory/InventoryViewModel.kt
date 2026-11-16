package com.example.ui.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.importexport.ParsedSheetResult
import com.example.data.importexport.SpreadsheetParser
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.ImportSummary
import com.example.data.model.ShoeItem
import com.example.data.model.StockImportRow
import com.example.data.remote.BarcodeLookupService
import com.example.data.remote.OnlineProductMetadata
import com.example.data.repository.ShoeInventoryRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

sealed interface ScanLookupState {
  data object Idle : ScanLookupState

  data class Searching(val code: String) : ScanLookupState

  data class Matched(
    val scannedCode: String,
    val item: ShoeItem,
    val matchSource: String,
  ) : ScanLookupState

  data class Unmatched(
    val scannedCode: String,
    val onlineMetadata: OnlineProductMetadata?,
    val suggestedSkus: List<ShoeItem>,
  ) : ScanLookupState
}

enum class InventoryFilter(val label: String) {
  ALL("All"),
  UNLINKED("No Barcode"),
  LOW_STOCK("Low Stock"),
}

sealed interface ImportStatus {
  data object Idle : ImportStatus

  data object Parsing : ImportStatus

  data class ReadyToImport(
    val sourceLabel: String,
    val rows: List<StockImportRow>,
    val skippedHeader: Boolean,
  ) : ImportStatus

  data object Importing : ImportStatus

  data class Completed(val summary: ImportSummary) : ImportStatus

  data class Failed(val message: String) : ImportStatus
}

class InventoryViewModel(
  private val repository: ShoeInventoryRepository,
  private val lookupService: BarcodeLookupService,
  private val prefsRepository: UserPreferencesRepository,
  private val currentUserId: String,
) : ViewModel() {

  val inventoryState: StateFlow<UiState<List<ShoeItem>>> =
    repository
      .observeInventory()
      .map<List<ShoeItem>, UiState<List<ShoeItem>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing shoe inventory for $currentUserId", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load inventory"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _filterMode = MutableStateFlow(InventoryFilter.ALL)
  val filterMode: StateFlow<InventoryFilter> = _filterMode.asStateFlow()

  val filteredItems: StateFlow<List<ShoeItem>> =
    combine(inventoryState, _searchQuery, _filterMode) { state, query, filter ->
        val items = (state as? UiState.Success)?.data.orEmpty()
        val q = query.trim().lowercase()
        items
          .filter { item ->
            val matchesFilter =
              when (filter) {
                InventoryFilter.ALL -> true
                InventoryFilter.UNLINKED -> item.barcodes.isEmpty()
                InventoryFilter.LOW_STOCK -> item.quantity <= 2L
              }
            if (!matchesFilter) return@filter false
            if (q.isEmpty()) return@filter true
            item.sku.lowercase().contains(q) ||
              item.binNumber.lowercase().contains(q) ||
              item.modelName.lowercase().contains(q) ||
              item.barcodes.any { it.lowercase().contains(q) }
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  private val _scanState = MutableStateFlow<ScanLookupState>(ScanLookupState.Idle)
  val scanState: StateFlow<ScanLookupState> = _scanState.asStateFlow()

  private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
  val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

  val savedSheetUrl: StateFlow<String> =
    prefsRepository.sheetSyncUrl.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = "",
    )

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  fun clearActionMessage() {
    _actionMessage.value = null
  }

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun updateFilterMode(filter: InventoryFilter) {
    _filterMode.value = filter
  }

  fun clearScanResult() {
    _scanState.value = ScanLookupState.Idle
  }

  fun onCodeScannedOrSubmitted(rawCode: String) {
    val code = rawCode.trim()
    if (code.isEmpty()) return

    val currentList = (inventoryState.value as? UiState.Success)?.data.orEmpty()
    val exactBarcodeMatch =
      currentList.firstOrNull { item ->
        item.barcodes.any { it.equals(code, ignoreCase = true) }
      }
    if (exactBarcodeMatch != null) {
      _scanState.value =
        ScanLookupState.Matched(
          scannedCode = code,
          item = exactBarcodeMatch,
          matchSource = "Linked Barcode",
        )
      return
    }

    val exactSkuMatch =
      currentList.firstOrNull { item -> item.sku.equals(code, ignoreCase = true) }
    if (exactSkuMatch != null) {
      _scanState.value =
        ScanLookupState.Matched(
          scannedCode = code,
          item = exactSkuMatch,
          matchSource = "Direct SKU Match",
        )
      return
    }

    viewModelScope.launch {
      _scanState.value = ScanLookupState.Searching(code)
      val onlineMeta = lookupService.lookupOnlineProduct(code)
      val latestList = (inventoryState.value as? UiState.Success)?.data ?: currentList
      val suggestions = lookupService.suggestMatchingItems(code, onlineMeta, latestList)
      _scanState.value =
        ScanLookupState.Unmatched(
          scannedCode = code,
          onlineMetadata = onlineMeta,
          suggestedSkus = suggestions,
        )
    }
  }

  fun refreshMatchedItemIfNeeded(items: List<ShoeItem>) {
    val current = _scanState.value
    if (current is ScanLookupState.Matched) {
      val updated = items.firstOrNull { it.id == current.item.id }
      if (updated != null && updated != current.item) {
        _scanState.value = current.copy(item = updated)
      }
    }
  }

  fun linkBarcodeToSku(barcode: String, item: ShoeItem, discoveredModelName: String? = null) {
    viewModelScope.launch {
      val nextBarcodes = (item.barcodes + barcode.trim()).distinct().take(20)
      val nextModelName =
        item.modelName.ifBlank { discoveredModelName?.trim().orEmpty() }.take(120)
      val result =
        repository.updateItem(
          itemId = item.id,
          sku = item.sku,
          binNumber = item.binNumber,
          modelName = nextModelName,
          barcodes = nextBarcodes,
          quantity = item.quantity,
          notes = item.notes,
        )
      result
        .onSuccess {
          val updated = item.copy(barcodes = nextBarcodes, modelName = nextModelName)
          _scanState.value =
            ScanLookupState.Matched(
              scannedCode = barcode.trim(),
              item = updated,
              matchSource = "Barcode Linked",
            )
          _actionMessage.value = "Linked barcode to ${item.sku} (Bin ${item.binNumber})"
        }
        .onFailure { err ->
          _actionMessage.value = err.localizedMessage ?: "Failed to link barcode"
        }
    }
  }

  fun saveShoeItem(
    existingItemId: String?,
    sku: String,
    binNumber: String,
    modelName: String,
    barcodes: List<String>,
    quantity: Long,
    notes: String,
    fromScanCode: String? = null,
  ) {
    viewModelScope.launch {
      val result =
        if (existingItemId.isNullOrBlank()) {
          repository.addItem(sku, binNumber, modelName, barcodes, quantity, notes)
        } else {
          repository.updateItem(existingItemId, sku, binNumber, modelName, barcodes, quantity, notes)
        }
      result
        .onSuccess {
          _actionMessage.value =
            if (existingItemId.isNullOrBlank()) {
              "Added $sku to Bin $binNumber"
            } else {
              "Updated $sku (Bin $binNumber)"
            }
          if (!fromScanCode.isNullOrBlank()) {
            val currentList = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val match =
              currentList.firstOrNull { it.sku.equals(sku.trim(), ignoreCase = true) }
                ?: ShoeItem(
                  id = existingItemId.orEmpty(),
                  userId = currentUserId,
                  sku = sku.trim(),
                  binNumber = binNumber.trim(),
                  modelName = modelName.trim(),
                  barcodes = barcodes,
                  quantity = quantity,
                  notes = notes.trim(),
                )
            _scanState.value =
              ScanLookupState.Matched(
                scannedCode = fromScanCode,
                item = match,
                matchSource = "Saved & Linked",
              )
          }
        }
        .onFailure { err ->
          _actionMessage.value = err.localizedMessage ?: "Unable to save item"
        }
    }
  }

  fun adjustQuantity(item: ShoeItem, delta: Long) {
    val nextQty = (item.quantity + delta).coerceIn(0L, 1_000_000L)
    if (nextQty == item.quantity) return
    viewModelScope.launch {
      repository.updateQuantity(item, nextQty).onFailure { err ->
        _actionMessage.value = err.localizedMessage ?: "Failed to update quantity"
      }
    }
  }

  fun deleteItem(item: ShoeItem) {
    viewModelScope.launch {
      repository
        .deleteItem(item.id)
        .onSuccess {
          if ((_scanState.value as? ScanLookupState.Matched)?.item?.id == item.id) {
            _scanState.value = ScanLookupState.Idle
          }
          _actionMessage.value = "Removed ${item.sku}"
        }
        .onFailure { err ->
          _actionMessage.value = err.localizedMessage ?: "Failed to remove item"
        }
    }
  }

  fun parseSpreadsheetStream(
    openStream: suspend () -> InputStream?,
    fileName: String?,
  ) {
    viewModelScope.launch {
      _importStatus.value = ImportStatus.Parsing
      val parsed =
        withContext(Dispatchers.IO) {
          runCatching {
            val stream = openStream() ?: error("Unable to open selected file")
            stream.use { SpreadsheetParser.parseStream(it, fileName) }
          }
        }
      parsed
        .onSuccess { res -> handleParsedResult(fileName ?: "Selected File", res) }
        .onFailure { err ->
          _importStatus.value =
            ImportStatus.Failed(err.localizedMessage ?: "Could not read spreadsheet")
        }
    }
  }

  fun fetchSheetFromUrl(url: String) {
    val cleanUrl = url.trim()
    if (cleanUrl.isEmpty()) {
      _importStatus.value = ImportStatus.Failed("Enter a published CSV or Google Sheets link")
      return
    }
    viewModelScope.launch {
      _importStatus.value = ImportStatus.Parsing
      prefsRepository.setSheetSyncUrl(cleanUrl)
      val res = lookupService.fetchSheetFromUrl(cleanUrl)
      res
        .onSuccess { parsed -> handleParsedResult("Remote Sheet", parsed) }
        .onFailure { err ->
          _importStatus.value =
            ImportStatus.Failed(err.localizedMessage ?: "Unable to fetch sheet URL")
        }
    }
  }

  private fun handleParsedResult(label: String, res: ParsedSheetResult) {
    if (res.rows.isEmpty()) {
      _importStatus.value =
        ImportStatus.Failed(
          "No valid SKU & Bin rows found. Ensure Column 1 is SKU and Column 2 is Bin Number."
        )
    } else {
      _importStatus.value =
        ImportStatus.ReadyToImport(
          sourceLabel = label,
          rows = res.rows,
          skippedHeader = res.skippedHeader,
        )
    }
  }

  fun confirmImport() {
    val ready = _importStatus.value as? ImportStatus.ReadyToImport ?: return
    viewModelScope.launch {
      _importStatus.value = ImportStatus.Importing
      repository
        .importStockRows(ready.rows)
        .onSuccess { summary ->
          _importStatus.value = ImportStatus.Completed(summary)
          _actionMessage.value =
            "Synced ${summary.createdCount} new & ${summary.updatedCount} existing SKUs"
        }
        .onFailure { err ->
          _importStatus.value =
            ImportStatus.Failed(err.localizedMessage ?: "Failed to sync imported rows")
        }
    }
  }

  fun resetImportStatus() {
    _importStatus.value = ImportStatus.Idle
  }

  fun buildExportCsv(): String {
    val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
    return SpreadsheetParser.exportToCsv(items)
  }

  private companion object {
    const val TAG = "InventoryVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
