package com.lubrcapp.lubrcapp.feature.purchases

/**
 * Phase 31.2 — an account the shell may offer as a payment
 * account. Feature modules do not depend on each other, so
 * this is the purchases-side twin of feature/sales'
 * PostingAccountOption: the shell maps its accounts into
 * this type instead of the sales one.
 */
data class PurchaseAccountOption(
    val id: String,
    val name: String,
)
