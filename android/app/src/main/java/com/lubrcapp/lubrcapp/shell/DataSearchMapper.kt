package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.business.CsvWriter
import com.lubrcapp.lubrcapp.domain.business.SearchEntry
import com.lubrcapp.lubrcapp.domain.business.SearchGroup
import com.lubrcapp.lubrcapp.feature.sales.ItemOption
import com.lubrcapp.lubrcapp.feature.sales.PartyOption
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument

/**
 * Phase 28.7 — the data-tools mappers: stored
 * records become the plain entries 28.1 searches
 * and the CSV rows 28.2 writes. A document with
 * no number yet falls back to its id as the entry
 * title, so unnumbered drafts stay findable. An
 * item with no sale rate leaves its detail blank
 * rather than inventing a price. Nothing here
 * touches the repository — the content owns the
 * flows, these functions only translate.
 */
object DataSearchMapper {
    fun documentsToEntries(documents: List<SalesDocument>): List<SearchEntry> {
        return documents.map { document ->
            SearchEntry(
                group = SearchGroup.INVOICES,
                id = document.id,
                title = document.number.ifBlank { document.id },
                detail = "${document.type} · ${document.date} · ${document.status}",
            )
        }
    }

    fun itemsToEntries(items: List<ItemOption>): List<SearchEntry> {
        return items.map { item ->
            SearchEntry(
                group = SearchGroup.ITEMS,
                id = item.id,
                title = item.name,
                detail = item.saleRate?.toPlainString().orEmpty(),
            )
        }
    }

    fun partiesToEntries(parties: List<PartyOption>): List<SearchEntry> {
        return parties.map { party ->
            SearchEntry(
                group = SearchGroup.CUSTOMERS,
                id = party.id,
                title = party.name,
            )
        }
    }

    fun documentsToCsv(documents: List<SalesDocument>): String {
        return CsvWriter.write(
            headers = listOf("Number", "Date", "Status"),
            rows = documents.map { document ->
                listOf(document.number, document.date, document.status)
            },
        )
    }
}
