package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.ShoeItem
import com.example.data.remote.BarcodeLookupService
import com.example.data.repository.ShoeInventoryRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.LinkSkuPickerDialog
import com.example.ui.components.ShoeItemEditorSheet
import com.example.ui.inventory.InventoryViewModel
import com.example.ui.inventory.UiState
import com.example.ui.screens.ImportScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.theme.ShoeBinTheme
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private const val ROUTE_SCAN = "scan"
private const val ROUTE_INVENTORY = "inventory"
private const val ROUTE_IMPORT = "import"

private enum class TopDestination(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  SCAN(ROUTE_SCAN, R.string.nav_scan, Icons.Rounded.QrCodeScanner, Icons.Rounded.QrCodeScanner),
  INVENTORY(ROUTE_INVENTORY, R.string.nav_inventory, Icons.Rounded.Inventory2, Icons.Outlined.Inventory2),
  IMPORT(ROUTE_IMPORT, R.string.nav_import, Icons.Rounded.UploadFile, Icons.Outlined.UploadFile),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current.applicationContext
      val prefsRepository = remember(context) { UserPreferencesRepository(context) }
      val themeMode by prefsRepository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
      val systemDark = isSystemInDarkTheme()
      val isEffectiveDark =
        when (themeMode) {
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
          ThemeMode.SYSTEM -> systemDark
        }
      val scope = rememberCoroutineScope()
      val toggleTheme: () -> Unit = {
        val next = if (isEffectiveDark) ThemeMode.LIGHT else ThemeMode.DARK
        scope.launch { prefsRepository.setThemeMode(next) }
      }

      ShoeBinTheme(themeMode = themeMode) {
        AppRoot(
          isDarkTheme = isEffectiveDark,
          onToggleTheme = toggleTheme,
          prefsRepository = prefsRepository,
        )
      }
    }
  }
}

@Composable
private fun AppRoot(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  prefsRepository: UserPreferencesRepository,
) {
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(
      isDarkTheme = isDarkTheme,
      onToggleTheme = onToggleTheme,
      onAuthSuccess = { currentUser = Firebase.auth.currentUser },
    )
  } else {
    key(user.uid) {
      ShoeBinMainScaffold(
        currentUserId = user.uid,
        userEmail = user.email,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        prefsRepository = prefsRepository,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoeBinMainScaffold(
  currentUserId: String,
  userEmail: String?,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  prefsRepository: UserPreferencesRepository,
) {
  val viewModel: InventoryViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(databaseId)
            InventoryViewModel(
              repository = ShoeInventoryRepository(firestore, Firebase.auth),
              lookupService = BarcodeLookupService(),
              prefsRepository = prefsRepository,
              currentUserId = currentUserId,
            )
          }
        },
    )

  val inventoryState by viewModel.inventoryState.collectAsStateWithLifecycle()
  val filteredItems by viewModel.filteredItems.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
  val scanState by viewModel.scanState.collectAsStateWithLifecycle()
  val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
  val savedSheetUrl by viewModel.savedSheetUrl.collectAsStateWithLifecycle()
  val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

  val allItems = (inventoryState as? UiState.Success)?.data.orEmpty()
  val linkedBarcodeCount = remember(allItems) { allItems.sumOf { it.barcodes.size } }

  LaunchedEffect(allItems) {
    viewModel.refreshMatchedItemIfNeeded(allItems)
  }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(actionMessage) {
    if (!actionMessage.isNullOrBlank()) {
      snackbarHostState.showSnackbar(actionMessage.orEmpty())
      viewModel.clearActionMessage()
    }
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var showAccountMenu by remember { mutableStateOf(false) }
  var editorVisible by remember { mutableStateOf(false) }
  var editingItem by remember { mutableStateOf<ShoeItem?>(null) }
  var editorPrefillBarcode by remember { mutableStateOf<String?>(null) }
  var editorPrefillModel by remember { mutableStateOf<String?>(null) }

  var linkPickerBarcode by remember { mutableStateOf<String?>(null) }
  var linkPickerModelHint by remember { mutableStateOf<String?>(null) }

  val navigateToTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
            )
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
          actions = {
            IconButton(
              onClick = onToggleTheme,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button"),
            ) {
              Icon(
                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = stringResource(R.string.toggle_theme_cd),
              )
            }
            Box {
              IconButton(
                onClick = { showAccountMenu = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("account_menu_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.Logout,
                  contentDescription = stringResource(R.string.sign_out),
                )
              }
              DropdownMenu(
                expanded = showAccountMenu,
                onDismissRequest = { showAccountMenu = false },
              ) {
                if (!userEmail.isNullOrBlank()) {
                  DropdownMenuItem(
                    text = {
                      Text(
                        text = userEmail,
                        style = MaterialTheme.typography.labelMedium,
                      )
                    },
                    onClick = { showAccountMenu = false },
                  )
                }
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.sign_out)) },
                  onClick = {
                    showAccountMenu = false
                    signOut(credentialManager, scope)
                  },
                )
              }
            }
          },
        )
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar(windowInsets = WindowInsets.navigationBars) {
            TopDestination.entries.forEach { dest ->
              val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = stringResource(dest.labelRes),
                  )
                },
                label = { Text(stringResource(dest.labelRes)) },
                modifier = Modifier.testTag("nav_${dest.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (useRail) {
          NavigationRail {
            TopDestination.entries.forEach { dest ->
              val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = stringResource(dest.labelRes),
                  )
                },
                label = { Text(stringResource(dest.labelRes)) },
                modifier = Modifier.testTag("rail_${dest.route}"),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = ROUTE_SCAN,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          composable(ROUTE_SCAN) {
            ScanScreen(
              scanState = scanState,
              inventoryCount = allItems.size,
              linkedBarcodeCount = linkedBarcodeCount,
              onCodeScannedOrSubmitted = viewModel::onCodeScannedOrSubmitted,
              onClearScanResult = viewModel::clearScanResult,
              onAdjustQuantity = viewModel::adjustQuantity,
              onEditItem = { item ->
                editingItem = item
                editorPrefillBarcode = null
                editorPrefillModel = null
                editorVisible = true
              },
              onLinkToSuggestedSku = { barcode, item, discoveredModel ->
                viewModel.linkBarcodeToSku(barcode, item, discoveredModel)
              },
              onOpenLinkSkuPicker = { barcode, discoveredModel ->
                linkPickerBarcode = barcode
                linkPickerModelHint = discoveredModel
              },
              onAddNewModelFromBarcode = { barcode, discoveredModel ->
                editingItem = null
                editorPrefillBarcode = barcode
                editorPrefillModel = discoveredModel
                editorVisible = true
              },
              onNavigateToImport = { navigateToTab(ROUTE_IMPORT) },
            )
          }

          composable(ROUTE_INVENTORY) {
            InventoryScreen(
              inventoryState = inventoryState,
              filteredItems = filteredItems,
              searchQuery = searchQuery,
              filterMode = filterMode,
              onSearchQueryChange = viewModel::updateSearchQuery,
              onFilterChange = viewModel::updateFilterMode,
              onItemClick = { item ->
                editingItem = item
                editorPrefillBarcode = null
                editorPrefillModel = null
                editorVisible = true
              },
              onAddNewClick = {
                editingItem = null
                editorPrefillBarcode = null
                editorPrefillModel = null
                editorVisible = true
              },
              onNavigateToImport = { navigateToTab(ROUTE_IMPORT) },
            )
          }

          composable(ROUTE_IMPORT) {
            ImportScreen(
              importStatus = importStatus,
              savedSheetUrl = savedSheetUrl,
              totalInventoryCount = allItems.size,
              onPickSpreadsheet = viewModel::parseSpreadsheetStream,
              onFetchFromUrl = viewModel::fetchSheetFromUrl,
              onConfirmImport = viewModel::confirmImport,
              onResetImportStatus = viewModel::resetImportStatus,
              onBuildExportCsv = viewModel::buildExportCsv,
            )
          }
        }
      }
    }
  }

  if (editorVisible) {
    ShoeItemEditorSheet(
      existingItem = editingItem,
      prefillBarcode = editorPrefillBarcode,
      prefillModelName = editorPrefillModel,
      onDismiss = { editorVisible = false },
      onSave = { existingId, sku, bin, model, barcodes, qty, notes ->
        viewModel.saveShoeItem(
          existingItemId = existingId,
          sku = sku,
          binNumber = bin,
          modelName = model,
          barcodes = barcodes,
          quantity = qty,
          notes = notes,
          fromScanCode = editorPrefillBarcode,
        )
      },
      onDelete = viewModel::deleteItem,
    )
  }

  val pendingLinkBarcode = linkPickerBarcode
  if (pendingLinkBarcode != null) {
    LinkSkuPickerDialog(
      barcode = pendingLinkBarcode,
      inventory = allItems,
      onSelectSku = { chosenItem ->
        viewModel.linkBarcodeToSku(pendingLinkBarcode, chosenItem, linkPickerModelHint)
        linkPickerBarcode = null
        linkPickerModelHint = null
      },
      onDismiss = {
        linkPickerBarcode = null
        linkPickerModelHint = null
      },
    )
  }
}
