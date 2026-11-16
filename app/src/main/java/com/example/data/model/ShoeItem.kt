package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ShoeItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val sku: String = "",
  val binNumber: String = "",
  val modelName: String = "",
  val barcodes: List<String> = emptyList(),
  val quantity: Long = 1L,
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class StockImportRow(
  val sku: String,
  val binNumber: String,
  val barcode: String = "",
  val modelName: String = "",
  val quantity: Long = 1L,
)

data class ImportSummary(
  val createdCount: Int,
  val updatedCount: Int,
  val skippedCount: Int,
)
