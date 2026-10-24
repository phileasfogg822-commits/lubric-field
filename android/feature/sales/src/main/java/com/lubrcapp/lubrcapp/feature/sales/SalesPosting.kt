package com.lubrcapp.lubrcapp.feature.sales

/**
 * Phase 21.5 — the sales screen's posting surface. The app
 * module owns the real posting handler (it alone can see the
 * accounts and inventory ViewModels — feature modules do not
 * depend on each other), and the sales screen owns the buttons
 * and the account picker. These two types are the contract
 * between them: plain data, no ViewModel references, so the
 * screen stays testable with a fake handler.
 *
 * [PostingAccountOption] is the registry's projection of an
 * account record (id + display name). The screen never sees
 * account types or codes: the user picks by name, and the
 * handler builds [InvoicePostingAccounts] from the picks.
 * Receivable and revenue are required; tax is optional —
 * without it the tax folds into revenue (21.3).
 */
data class PostingAccountOption(
    val id: String,
    val name: String,
)

/**
 * What the screen shows after a posting attempt. [Posted]
 * carries the document number for the notice; the flipped
 * serial numbers are recorded for the inventory screens to
 * show, not spelled out in the notice. [Rejected] carries
 * the validator's violation names, never raw internals.
 */
sealed interface SalesPostResult {
    data class Posted(
        val documentNumber: String,
        val flippedSerials: List<String>,
    ) : SalesPostResult

    data class Rejected(
        val reasons: List<String>,
    ) : SalesPostResult
}
