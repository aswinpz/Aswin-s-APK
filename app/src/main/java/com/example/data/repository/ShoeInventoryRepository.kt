package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ImportSummary
import com.example.data.model.ShoeItem
import com.example.data.model.StockImportRow
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ShoeInventoryRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun userCollection(uid: String) =
    db.collection("users").document(uid).collection(COLLECTION_SHOE_ITEMS)

  fun observeInventory(): Flow<List<ShoeItem>> = flow {
    val uid = requireUserId()
    val colRef = userCollection(uid)
    emitAll(
      colRef
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              ShoeItem::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedBy { it.sku.lowercase() }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
          }
          throw error
        }
    )
  }

  suspend fun getItemById(itemId: String): Result<ShoeItem?> {
    return try {
      val uid = requireUserId()
      val docRef = userCollection(uid).document(itemId)
      val snapshot = docRef.get().await()
      Result.success(
        snapshot.toObject(
          ShoeItem::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val uid = auth.currentUser?.uid.orEmpty()
      handleFirestoreError(e, OperationType.GET, "users/$uid/$COLLECTION_SHOE_ITEMS/$itemId")
      Result.failure(e)
    }
  }

  suspend fun addItem(
    sku: String,
    binNumber: String,
    modelName: String,
    barcodes: List<String>,
    quantity: Long,
    notes: String,
  ): Result<String> {
    return try {
      val uid = requireUserId()
      val cleanSku = sku.trim().take(80)
      val cleanBin = binNumber.trim().take(40)
      require(cleanSku.isNotEmpty()) { "SKU cannot be empty" }
      require(cleanBin.isNotEmpty()) { "Bin number cannot be empty" }

      val cleanBarcodes =
        barcodes.map { it.trim().take(80) }.filter { it.isNotEmpty() }.distinct().take(20)
      val cleanQty = quantity.coerceIn(0L, 1_000_000L)
      val docRef = userCollection(uid).document()
      val payload =
        mapOf(
          "userId" to uid,
          "sku" to cleanSku,
          "binNumber" to cleanBin,
          "modelName" to modelName.trim().take(120),
          "barcodes" to cleanBarcodes,
          "quantity" to cleanQty,
          "notes" to notes.trim().take(240),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val uid = auth.currentUser?.uid.orEmpty()
      handleFirestoreError(e, OperationType.CREATE, "users/$uid/$COLLECTION_SHOE_ITEMS")
      Result.failure(e)
    }
  }

  suspend fun updateItem(
    itemId: String,
    sku: String,
    binNumber: String,
    modelName: String,
    barcodes: List<String>,
    quantity: Long,
    notes: String,
  ): Result<Unit> {
    return try {
      val uid = requireUserId()
      require(itemId.isNotBlank()) { "Item ID must not be blank" }
      val cleanSku = sku.trim().take(80)
      val cleanBin = binNumber.trim().take(40)
      require(cleanSku.isNotEmpty()) { "SKU cannot be empty" }
      require(cleanBin.isNotEmpty()) { "Bin number cannot be empty" }

      val cleanBarcodes =
        barcodes.map { it.trim().take(80) }.filter { it.isNotEmpty() }.distinct().take(20)
      val cleanQty = quantity.coerceIn(0L, 1_000_000L)
      val docRef = userCollection(uid).document(itemId)
      val updates =
        mapOf(
          "sku" to cleanSku,
          "binNumber" to cleanBin,
          "modelName" to modelName.trim().take(120),
          "barcodes" to cleanBarcodes,
          "quantity" to cleanQty,
          "notes" to notes.trim().take(240),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val uid = auth.currentUser?.uid.orEmpty()
      handleFirestoreError(e, OperationType.UPDATE, "users/$uid/$COLLECTION_SHOE_ITEMS/$itemId")
      Result.failure(e)
    }
  }

  suspend fun linkBarcodeToItem(item: ShoeItem, barcode: String): Result<Unit> {
    val cleanBarcode = barcode.trim().take(80)
    if (cleanBarcode.isEmpty()) return Result.failure(IllegalArgumentException("Empty barcode"))
    val updatedBarcodes = (item.barcodes + cleanBarcode).distinct().take(20)
    return updateItem(
      itemId = item.id,
      sku = item.sku,
      binNumber = item.binNumber,
      modelName = item.modelName,
      barcodes = updatedBarcodes,
      quantity = item.quantity,
      notes = item.notes,
    )
  }

  suspend fun updateQuantity(item: ShoeItem, newQuantity: Long): Result<Unit> {
    return updateItem(
      itemId = item.id,
      sku = item.sku,
      binNumber = item.binNumber,
      modelName = item.modelName,
      barcodes = item.barcodes,
      quantity = newQuantity.coerceIn(0L, 1_000_000L),
      notes = item.notes,
    )
  }

  suspend fun deleteItem(itemId: String): Result<Unit> {
    return try {
      val uid = requireUserId()
      require(itemId.isNotBlank()) { "Item ID must not be blank" }
      val docRef = userCollection(uid).document(itemId)
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val uid = auth.currentUser?.uid.orEmpty()
      handleFirestoreError(e, OperationType.DELETE, "users/$uid/$COLLECTION_SHOE_ITEMS/$itemId")
      Result.failure(e)
    }
  }

  suspend fun importStockRows(rows: List<StockImportRow>): Result<ImportSummary> {
    return try {
      val uid = requireUserId()
      val colRef = userCollection(uid)
      val existingSnapshot = colRef.get().await()
      val existingBySku =
        existingSnapshot
          .toObjects(ShoeItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .associateBy { it.sku.trim().uppercase() }
          .toMutableMap()

      // Merge duplicate SKUs within the import sheet itself
      val mergedRows = linkedMapOf<String, StockImportRow>()
      var skipped = 0
      for (row in rows) {
        val cleanSku = row.sku.trim().take(80)
        val cleanBin = row.binNumber.trim().take(40)
        if (cleanSku.isEmpty() || cleanBin.isEmpty()) {
          skipped++
          continue
        }
        val key = cleanSku.uppercase()
        val prev = mergedRows[key]
        if (prev == null) {
          mergedRows[key] =
            row.copy(
              sku = cleanSku,
              binNumber = cleanBin,
              barcode = row.barcode.trim().take(80),
              modelName = row.modelName.trim().take(120),
            )
        } else {
          mergedRows[key] =
            prev.copy(
              binNumber = cleanBin,
              barcode = row.barcode.trim().take(80).ifEmpty { prev.barcode },
              modelName = row.modelName.trim().take(120).ifEmpty { prev.modelName },
            )
        }
      }

      var created = 0
      var updated = 0
      val entries = mergedRows.entries.toList()
      val batchSize = 400
      for (chunk in entries.chunked(batchSize)) {
        val batch = db.batch()
        for ((skuKey, row) in chunk) {
          val existing = existingBySku[skuKey]
          if (existing != null) {
            val docRef = colRef.document(existing.id)
            val nextBarcodes =
              if (row.barcode.isNotEmpty()) {
                (existing.barcodes + row.barcode).distinct().take(20)
              } else {
                existing.barcodes
              }
            val nextModel = row.modelName.ifEmpty { existing.modelName }
            batch.update(
              docRef,
              mapOf(
                "binNumber" to row.binNumber,
                "modelName" to nextModel,
                "barcodes" to nextBarcodes,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
            updated++
          } else {
            val docRef = colRef.document()
            val initialBarcodes =
              if (row.barcode.isNotEmpty()) listOf(row.barcode) else emptyList()
            batch.set(
              docRef,
              mapOf(
                "userId" to uid,
                "sku" to row.sku,
                "binNumber" to row.binNumber,
                "modelName" to row.modelName,
                "barcodes" to initialBarcodes,
                "quantity" to row.quantity.coerceIn(0L, 1_000_000L),
                "notes" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
            created++
          }
        }
        batch.commit().await()
      }

      Result.success(
        ImportSummary(
          createdCount = created,
          updatedCount = updated,
          skippedCount = skipped,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val uid = auth.currentUser?.uid.orEmpty()
      handleFirestoreError(e, OperationType.WRITE, "users/$uid/$COLLECTION_SHOE_ITEMS")
      Result.failure(e)
    }
  }

  companion object {
    const val COLLECTION_SHOE_ITEMS = "shoe_items"
  }
}
