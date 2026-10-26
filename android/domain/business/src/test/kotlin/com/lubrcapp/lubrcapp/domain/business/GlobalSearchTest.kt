package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {

    private fun entries(): List<SearchEntry> = listOf(
        SearchEntry(SearchGroup.PAYMENTS, "pay-1", "Receipt 5000", "Ali Autos"),
        SearchEntry(SearchGroup.INVOICES, "inv-1", "INV-0182", "Ali Autos"),
        SearchEntry(SearchGroup.CUSTOMERS, "cust-1", "Ali Autos", "03331234567"),
        SearchEntry(SearchGroup.ITEMS, "item-1", "Shell HX7", "", "barcode-890123"),
        SearchEntry(SearchGroup.ORDERS, "ord-1", "SO-0042", "Ali Autos"),
        SearchEntry(SearchGroup.CUSTOMERS, "cust-2", "Bilal Traders", "03339876543"),
    )

    @Test
    fun groupsReturnInSpecOrder() {
        val groups = GlobalSearch.search(entries(), "ali").map { it.group }
        assertEquals(
            listOf(
                SearchGroup.CUSTOMERS,
                SearchGroup.INVOICES,
                SearchGroup.ORDERS,
                SearchGroup.PAYMENTS,
            ),
            groups,
        )
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val lower = GlobalSearch.search(entries(), "ali")
        val upper = GlobalSearch.search(entries(), "ALI")
        assertEquals(lower, upper)
        val item = GlobalSearch.search(entries(), "SHELL")
        assertEquals(1, item.single().entries.size)
        assertEquals("item-1", item.single().entries.single().id)
    }

    @Test
    fun matchesDetailPhoneAndKeywordBarcode() {
        val byPhone = GlobalSearch.search(entries(), "03331234567")
        assertEquals("cust-1", byPhone.single().entries.single().id)
        val byBarcode = GlobalSearch.search(entries(), "890123")
        assertEquals("item-1", byBarcode.single().entries.single().id)
    }

    @Test
    fun matchesDocumentNumber() {
        val results = GlobalSearch.search(entries(), "INV-0182")
        assertEquals(SearchGroup.INVOICES, results.single().group)
        assertEquals("inv-1", results.single().entries.single().id)
    }

    @Test
    fun emptyOrBlankQueryReturnsNothing() {
        assertTrue(GlobalSearch.search(entries(), "").isEmpty())
        assertTrue(GlobalSearch.search(entries(), "   ").isEmpty())
    }

    @Test
    fun noMatchReturnsNothing() {
        assertTrue(GlobalSearch.search(entries(), "zzz-no-such").isEmpty())
    }

    @Test
    fun preservesInputOrderWithinGroup() {
        val results = GlobalSearch.search(entries(), "0333")
        val customers = results.single { it.group == SearchGroup.CUSTOMERS }
        assertEquals(listOf("cust-1", "cust-2"), customers.entries.map { it.id })
    }

    @Test
    fun queryTrimsSurroundingWhitespace() {
        val results = GlobalSearch.search(entries(), "  ali  ")
        assertEquals(4, results.sumOf { it.entries.size })
    }
}
