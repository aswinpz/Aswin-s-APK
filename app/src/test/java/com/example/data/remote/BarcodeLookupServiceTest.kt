package com.example.data.remote

import com.example.data.model.ShoeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeLookupServiceTest {

  private val service = BarcodeLookupService()

  @Test
  fun suggestMatchingItems_ranksMatchingSkuFromOnlineMetadataFirst() {
    val inventory =
      listOf(
        ShoeItem(id = "1", sku = "AD-UB22-WHT-9", binNumber = "B-04", modelName = "Ultraboost 22"),
        ShoeItem(id = "2", sku = "NK-AM90-BLK-10", binNumber = "A-12", modelName = ""),
        ShoeItem(id = "3", sku = "NB-574-GRY-11", binNumber = "C-01", modelName = "New Balance 574"),
      )

    val onlineMeta =
      OnlineProductMetadata(
        barcode = "0194957382912",
        title = "Nike Air Max 90 Black Mens Size 10",
        brand = "Nike",
        model = "AM90",
      )

    val suggestions = service.suggestMatchingItems("0194957382912", onlineMeta, inventory)
    assertTrue(suggestions.isNotEmpty())
    assertEquals("NK-AM90-BLK-10", suggestions.first().sku)
  }
}
