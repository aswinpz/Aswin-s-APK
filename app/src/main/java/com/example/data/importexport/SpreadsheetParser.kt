package com.example.data.importexport

import android.util.Xml
import com.example.data.model.ShoeItem
import com.example.data.model.StockImportRow
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

data class ParsedSheetResult(
  val rows: List<StockImportRow>,
  val skippedHeader: Boolean,
)

object SpreadsheetParser {

  fun parseStream(inputStream: InputStream, fileName: String?): ParsedSheetResult {
    val bytes = inputStream.readBytes()
    val isXlsx =
      fileName?.lowercase()?.endsWith(".xlsx") == true ||
        (bytes.size >= 4 &&
          bytes[0] == 0x50.toByte() &&
          bytes[1] == 0x4B.toByte() &&
          bytes[2] == 0x03.toByte() &&
          bytes[3] == 0x04.toByte())

    val rawRows =
      if (isXlsx) {
        parseXlsxBytes(bytes)
      } else {
        parseDelimitedText(String(bytes, StandardCharsets.UTF_8))
      }

    return toStockRows(rawRows)
  }

  fun parseDelimitedText(text: String): List<List<String>> {
    val lines =
      text
        .replace("\uFEFF", "")
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.isEmpty()) return emptyList()

    val first = lines.first()
    val delimiter =
      when {
        first.contains('\t') -> '\t'
        first.count { it == ';' } > first.count { it == ',' } -> ';'
        else -> ','
      }

    return lines.map { splitCsvLine(it, delimiter) }
  }

  private fun splitCsvLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
      val ch = line[i]
      when {
        ch == '"' -> {
          if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
            current.append('"')
            i++
          } else {
            inQuotes = !inQuotes
          }
        }
        ch == delimiter && !inQuotes -> {
          result.add(current.toString().trim())
          current.clear()
        }
        else -> current.append(ch)
      }
      i++
    }
    result.add(current.toString().trim())
    return result
  }

  private fun parseXlsxBytes(bytes: ByteArray): List<List<String>> {
    var sharedStringsBytes: ByteArray? = null
    var sheet1Bytes: ByteArray? = null

    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        val name = entry.name.lowercase()
        when {
          name == "xl/sharedstrings.xml" -> sharedStringsBytes = zis.readBytes()
          name == "xl/worksheets/sheet1.xml" -> sheet1Bytes = zis.readBytes()
          sheet1Bytes == null && name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
            sheet1Bytes = zis.readBytes()
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    val sheetBytes = sheet1Bytes ?: return emptyList()
    val sharedStrings =
      sharedStringsBytes?.let { parseSharedStrings(it) } ?: emptyList()
    return parseWorksheetXml(sheetBytes, sharedStrings)
  }

  private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
    val list = mutableListOf<String>()
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

    var inSi = false
    var inText = false
    val current = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "si" -> {
              inSi = true
              current.clear()
            }
            "t" -> if (inSi) inText = true
          }
        }
        XmlPullParser.TEXT -> {
          if (inSi && inText) current.append(parser.text)
        }
        XmlPullParser.END_TAG -> {
          when (parser.name) {
            "t" -> inText = false
            "si" -> {
              list.add(current.toString().trim())
              inSi = false
            }
          }
        }
      }
      event = parser.next()
    }
    return list
  }

  private fun parseWorksheetXml(
    xmlBytes: ByteArray,
    sharedStrings: List<String>,
  ): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

    var currentRowCells = sortedMapOf<Int, String>()
    var currentColIndex = -1
    var currentCellType: String? = null
    var inValue = false
    var inInlineText = false
    val textBuf = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "row" -> currentRowCells = sortedMapOf()
            "c" -> {
              val ref = parser.getAttributeValue(null, "r")
              currentColIndex = if (ref != null) columnIndexFromRef(ref) else currentRowCells.size
              currentCellType = parser.getAttributeValue(null, "t")
              textBuf.clear()
            }
            "v" -> {
              inValue = true
              textBuf.clear()
            }
            "t" -> {
              if (currentCellType == "inlineStr") {
                inInlineText = true
                textBuf.clear()
              }
            }
          }
        }
        XmlPullParser.TEXT -> {
          if (inValue || inInlineText) {
            textBuf.append(parser.text)
          }
        }
        XmlPullParser.END_TAG -> {
          when (parser.name) {
            "v" -> {
              inValue = false
              val raw = textBuf.toString().trim()
              val resolved =
                if (currentCellType == "s") {
                  val idx = raw.toIntOrNull()
                  if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                } else {
                  raw
                }
              if (currentColIndex >= 0) {
                currentRowCells[currentColIndex] = resolved
              }
            }
            "t" -> {
              if (inInlineText) {
                inInlineText = false
                if (currentColIndex >= 0) {
                  currentRowCells[currentColIndex] = textBuf.toString().trim()
                }
              }
            }
            "row" -> {
              if (currentRowCells.isNotEmpty()) {
                val maxCol = currentRowCells.lastKey()
                val rowList = (0..maxCol).map { currentRowCells[it]?.trim().orEmpty() }
                if (rowList.any { it.isNotEmpty() }) {
                  rows.add(rowList)
                }
              }
            }
          }
        }
      }
      event = parser.next()
    }
    return rows
  }

  private fun columnIndexFromRef(cellRef: String): Int {
    var col = 0
    for (ch in cellRef) {
      if (ch.isLetter()) {
        col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
      } else {
        break
      }
    }
    return (col - 1).coerceAtLeast(0)
  }

  fun toStockRows(rawRows: List<List<String>>): ParsedSheetResult {
    if (rawRows.isEmpty()) return ParsedSheetResult(emptyList(), skippedHeader = false)

    val firstRow = rawRows.first()
    val c0 = firstRow.getOrNull(0)?.lowercase().orEmpty()
    val c1 = firstRow.getOrNull(1)?.lowercase().orEmpty()
    val looksLikeHeader =
      c0.contains("sku") || c0.contains("style") || c0.contains("code") ||
        c1.contains("bin") || c1.contains("loc") || c1.contains("rack")

    val dataRows = if (looksLikeHeader) rawRows.drop(1) else rawRows
    val parsed =
      dataRows.mapNotNull { cols ->
        val sku = cols.getOrNull(0)?.trim().orEmpty()
        val bin = cols.getOrNull(1)?.trim().orEmpty()
        if (sku.isEmpty() || bin.isEmpty()) return@mapNotNull null
        val col2 = cols.getOrNull(2)?.trim().orEmpty()
        val col3 = cols.getOrNull(3)?.trim().orEmpty()
        // If column 3 looks numeric (barcode) or column 3 is present as barcode + model
        val (barcode, modelName) =
          when {
            col2.isNotEmpty() && col3.isNotEmpty() -> col2 to col3
            col2.all { it.isDigit() } && col2.length >= 6 -> col2 to ""
            else -> "" to col2
          }
        StockImportRow(
          sku = sku,
          binNumber = bin,
          barcode = barcode,
          modelName = modelName,
        )
      }

    return ParsedSheetResult(rows = parsed, skippedHeader = looksLikeHeader)
  }

  fun exportToCsv(items: List<ShoeItem>): String {
    val sb = StringBuilder()
    sb.appendLine("SKU,Bin Number,Barcodes,Model Name,Quantity,Notes")
    for (item in items) {
      val cells =
        listOf(
          escapeCsv(item.sku),
          escapeCsv(item.binNumber),
          escapeCsv(item.barcodes.joinToString(";")),
          escapeCsv(item.modelName),
          item.quantity.toString(),
          escapeCsv(item.notes),
        )
      sb.appendLine(cells.joinToString(","))
    }
    return sb.toString()
  }

  private fun escapeCsv(value: String): String {
    return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
      "\"${value.replace("\"", "\"\"")}\""
    } else {
      value
    }
  }
}
