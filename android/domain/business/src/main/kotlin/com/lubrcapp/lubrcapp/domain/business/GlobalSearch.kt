package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 28.1 — global search (§129). Pure model over
 * plain entries: the screen layer maps real records
 * (customers, items, invoices, orders, payments) to
 * [SearchEntry] values; this core never touches a
 * repository, so it needs no index tables and no
 * persistence.
 *
 * Groups are exactly the §129 list, in spec order.
 * Matching is case-insensitive substring over title,
 * detail and keywords — so a name ("Ali Autos"), a
 * document number ("INV-0182"), an item name
 * ("Shell HX7"), a phone ("0333...") and a barcode
 * all match through the same rule, provided the
 * mapper puts them in title/detail/keywords.
 * An empty or blank query returns no groups: a
 * search with no query shows nothing, not everything.
 * Groups with no matches are omitted; within a
 * group, entries keep their input order.
 */
enum class SearchGroup {
    CUSTOMERS,
    ITEMS,
    INVOICES,
    ORDERS,
    PAYMENTS,
}

data class SearchEntry(
    val group: SearchGroup,
    val id: String,
    val title: String,
    val detail: String = "",
    val keywords: String = "",
)

data class SearchGroupResults(
    val group: SearchGroup,
    val entries: List<SearchEntry>,
)

object GlobalSearch {
    fun search(entries: List<SearchEntry>, query: String): List<SearchGroupResults> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val matched = entries.filter { entry ->
            entry.title.lowercase().contains(needle) ||
                entry.detail.lowercase().contains(needle) ||
                entry.keywords.lowercase().contains(needle)
        }
        return SearchGroup.entries.mapNotNull { group ->
            val inGroup = matched.filter { it.group == group }
            if (inGroup.isEmpty()) null else SearchGroupResults(group, inGroup)
        }
    }
}
