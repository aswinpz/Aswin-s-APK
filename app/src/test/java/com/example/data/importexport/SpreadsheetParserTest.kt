package com.example.data.importexport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SpreadsheetParserTest {

  @Test
  fun parseCsv_twoColumnsWithHeader_extractsSkuAndBin() {
    val csv =
      """
      SKU,Bin Number
      NK-AM90-BLK-10,A-12
      AD-UB22-WHT-09,B-04
      "NB-574-GRY,10.5",C-08
      """
        .trimIndent()

    val result =
      SpreadsheetParser.parseStream(
        ByteArrayInputStream(csv.toByteArray()),
        "stock.csv",
      )

    assertTrue(result.skippedHeader)
    assertEquals(3, result.rows.size)
    assertEquals("NK-AM90-BLK-10", result.rows[0].sku)
    assertEquals("A-12", result.rows[0].binNumber)
    assertEquals("NB-574-GRY,10.5", result.rows[2].sku)
    assertEquals("C-08", result.rows[2].binNumber)
  }

  @Test
  fun parseXlsx_sharedStringsAndInlineValues_extractsSkuAndBin() {
    val sharedStrings =
      """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <si><t>SKU</t></si>
        <si><t>Bin</t></si>
        <si><t>PUMA-RSX-10</t></si>
        <si><t>D-19</t></si>
      </sst>
      """
        .trimIndent()

    val sheet1 =
      """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <sheetData>
          <row r="1">
            <c r="A1" t="s"><v>0</v></c>
            <c r="B1" t="s"><v>1</v></c>
          </row>
          <row r="2">
            <c r="A2" t="s"><v>2</v></c>
            <c r="B2" t="s"><v>3</v></c>
          </row>
        </sheetData>
      </worksheet>
      """
        .trimIndent()

    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
      zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
      zos.write(sharedStrings.toByteArray())
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
      zos.write(sheet1.toByteArray())
      zos.closeEntry()
    }

    val result =
      SpreadsheetParser.parseStream(
        ByteArrayInputStream(baos.toByteArray()),
        "current_stock.xlsx",
      )

    assertTrue(result.skippedHeader)
    assertEquals(1, result.rows.size)
    assertEquals("PUMA-RSX-10", result.rows[0].sku)
    assertEquals("D-19", result.rows[0].binNumber)
  }
}
