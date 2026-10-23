package com.lubrcapp.lubrcapp.feature.parties

import java.math.BigDecimal

/**
 * Phase 14.3 — a customer group (master spec §19), stored as its own
 * operational record type ("customer_groups") in the same scoped store
 * as parties.
 *
 * §19: groups such as Retail / Wholesale / Dealer / Distributor / VIP /
 * Cash Customer may define a price level, a discount, payment terms,
 * tax rules, a salesperson and a territory. Those are defaults the
 * group offers — Phase 14 stores them; later phases decide how sales
 * flows consume them.
 *
 * Parties link by [Party.groupId]. Deleting a group never deletes or
 * edits its parties: the link is kept and the UI shows it unresolved
 * ("—"), because sync may resurrect the group from another device.
 */
data class PartyGroup(
    val id: String,
    val name: String,
    val priceLevel: String? = null,
    val discountPercent: BigDecimal? = null,
    val paymentTerms: String? = null,
    val taxRule: String? = null,
    val salesperson: String? = null,
    val territory: String? = null,
)
