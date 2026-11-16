package com.example.data.remote

import com.example.data.importexport.ParsedSheetResult
import com.example.data.importexport.SpreadsheetParser
import com.example.data.model.ShoeItem
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class OnlineProductMetadata(
  val barcode: String,
  val title: String,
  val brand: String,
  val model: String,
)

class BarcodeLookupService(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(6, TimeUnit.SECONDS)
      .build()
) {

  suspend fun lookupOnlineProduct(barcode: String): OnlineProductMetadata? =
    withContext(Dispatchers.IO) {
      val clean = barcode.trim()
      if (clean.length < 6) return@withContext null
      runCatching {
          val request =
            Request.Builder()
              .url("https://api.upcitemdb.com/prod/trial/lookup?upc=$clean")
              .header("Accept", "application/json")
              .build()
          client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: return@use null
            if (items.length() == 0) return@use null
            val first = items.getJSONObject(0)
            val title = first.optString("title").trim()
            val brand = first.optString("brand").trim()
            val model = first.optString("model").trim()
            if (title.isEmpty() && brand.isEmpty() && model.isEmpty()) return@use null
            OnlineProductMetadata(
              barcode = clean,
              title = title,
              brand = brand,
              model = model,
            )
          }
        }
        .getOrNull()
    }

  suspend fun fetchSheetFromUrl(rawUrl: String): Result<ParsedSheetResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val normalizedUrl = normalizeGoogleSheetUrl(rawUrl.trim())
        require(normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")) {
          "Enter a valid http:// or https:// spreadsheet URL"
        }
        val request = Request.Builder().url(normalizedUrl).build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            error("HTTP ${response.code}: Unable to fetch sheet")
          }
          val bytes = response.body?.bytes() ?: error("Empty response from sheet URL")
          val fileName = if (normalizedUrl.lowercase().contains(".xlsx")) "sheet.xlsx" else "sheet.csv"
          SpreadsheetParser.parseStream(ByteArrayInputStream(bytes), fileName)
        }
      }
    }

  private fun normalizeGoogleSheetUrl(url: String): String {
    // Convert standard Google Sheets edit URL to CSV export URL if needed
    val regex = Regex("https://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9_-]+)")
    val match = regex.find(url)
    return if (match != null && !url.contains("output=csv") && !url.contains("format=csv")) {
      val sheetId = match.groupValues[1]
      "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
    } else {
      url
    }
  }

  fun suggestMatchingItems(
    barcode: String,
    onlineMeta: OnlineProductMetadata?,
    inventory: List<ShoeItem>,
  ): List<ShoeItem> {
    if (inventory.isEmpty()) return emptyList()
    val queryTokens = buildSet {
      addAll(tokenize(barcode))
      if (onlineMeta != null) {
        addAll(tokenize(onlineMeta.title))
        addAll(tokenize(onlineMeta.brand))
        addAll(tokenize(onlineMeta.model))
      }
    }
    if (queryTokens.isEmpty()) {
      return inventory.filter { it.barcodes.isEmpty() }.take(5)
    }

    return inventory
      .map { item ->
        val itemText = "${item.sku} ${item.modelName} ${item.notes}"
        val itemTokens = tokenize(itemText)
        var score = 0
        for (token in queryTokens) {
          if (token.length < 2) continue
          if (item.sku.contains(token, ignoreCase = true)) score += 3
          if (item.modelName.contains(token, ignoreCase = true)) score += 2
          if (itemTokens.contains(token)) score += 2
        }
        if (onlineMeta?.model?.isNotBlank() == true &&
          item.sku.contains(onlineMeta.model, ignoreCase = true)
        ) {
          score += 10
        }
        item to score
      }
      .filter { it.second > 0 }
      .sortedByDescending { it.second }
      .map { it.first }
      .take(5)
  }

  private fun tokenize(input: String): Set<String> =
    input
      .lowercase()
      .split(Regex("[^a-z0-9]+"))
      .filter { it.length >= 2 }
      .toSet()
}
