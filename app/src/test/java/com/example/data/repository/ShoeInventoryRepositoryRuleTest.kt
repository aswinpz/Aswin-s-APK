package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.StockImportRow
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoeInventoryRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun addItem_authenticatedOwner_createsDocumentAndObservesRealtime() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ShoeInventoryRepository(firestore, auth)

    val createResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addItem(
          sku = "NK-AM90-BLK-10",
          binNumber = "A-12",
          modelName = "Nike Air Max 90 Black",
          barcodes = listOf("0194957382912"),
          quantity = 5L,
          notes = "Top rack",
        )
      }
    assertTrue(createResult.isSuccess)
    val itemId = createResult.getOrThrow()
    assertTrue(itemId.isNotEmpty())

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId } }
      }
    val item = observed.first { it.id == itemId }
    assertEquals("NK-AM90-BLK-10", item.sku)
    assertEquals("A-12", item.binNumber)
    assertEquals(listOf("0194957382912"), item.barcodes)
  }

  @Test
  fun linkBarcodeAndUpdateBin_authenticatedOwner_succeeds() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ShoeInventoryRepository(firestore, auth)

    val itemId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .addItem(
            sku = "AD-UB22-WHT-9",
            binNumber = "B-03",
            modelName = "Adidas Ultraboost 22",
            barcodes = emptyList(),
            quantity = 3L,
            notes = "",
          )
          .getOrThrow()
      }

    val created = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getItemById(itemId).getOrThrow() }
    assertNotNull(created)

    val linkRes =
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.linkBarcodeToItem(created!!, "4065423991021") }
    assertTrue(linkRes.isSuccess)

    val updateRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateItem(
          itemId = itemId,
          sku = "AD-UB22-WHT-9",
          binNumber = "B-09",
          modelName = "Adidas Ultraboost 22",
          barcodes = listOf("4065423991021"),
          quantity = 7L,
          notes = "Moved to B-09",
        )
      }
    assertTrue(updateRes.isSuccess)

    val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getItemById(itemId).getOrThrow() }
    assertEquals("B-09", updated?.binNumber)
    assertEquals(7L, updated?.quantity)
    assertEquals(listOf("4065423991021"), updated?.barcodes)
  }

  @Test
  fun importStockRows_createsAndUpdatesBySku() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ShoeInventoryRepository(firestore, auth)

    val firstImport =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.importStockRows(
          listOf(
            StockImportRow(sku = "NB-574-GRY-10", binNumber = "C-01"),
            StockImportRow(sku = "PM-SUEDE-RED-8", binNumber = "C-02"),
          )
        )
      }
    assertTrue(firstImport.isSuccess)
    assertEquals(2, firstImport.getOrThrow().createdCount)

    val secondImport =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.importStockRows(
          listOf(
            StockImportRow(sku = "NB-574-GRY-10", binNumber = "D-14"),
            StockImportRow(sku = "AS-GEL14-BLU-11", binNumber = "D-15"),
          )
        )
      }
    assertTrue(secondImport.isSuccess)
    val summary = secondImport.getOrThrow()
    assertEquals(1, summary.createdCount)
    assertEquals(1, summary.updatedCount)
  }

  @Test
  fun crossUserIsolation_bobCannotReadAliceDocument() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = ShoeInventoryRepository(firestore, auth)
    val aliceItemId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .addItem("NIKE-PRIVATE", "Z-01", "Private Model", emptyList(), 1L, "")
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    var caught: Exception? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore
          .collection("users")
          .document(aliceUid)
          .collection(ShoeInventoryRepository.COLLECTION_SHOE_ITEMS)
          .document(aliceItemId)
          .get()
          .let { com.google.android.gms.tasks.Tasks.await(it) }
      }
    } catch (e: Exception) {
      caught = e
    }
    val firestoreException = caught?.unwrapFirestoreException()
    assertNotNull("Expected PERMISSION_DENIED for cross-user read", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun unauthenticatedCaller_cannotQueryOrObserve() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()

    var caught: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        firestore
          .collection("users")
          .document(aliceUid)
          .collection(ShoeInventoryRepository.COLLECTION_SHOE_ITEMS)
          .get()
          .let { com.google.android.gms.tasks.Tasks.await(it) }
      }
    } catch (e: Exception) {
      caught = e
    }
    val firestoreException = caught?.unwrapFirestoreException()
    assertNotNull("Expected PERMISSION_DENIED for unauthenticated query", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_shoes@test.com"
    const val BOB_EMAIL = "bob_shoes@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
