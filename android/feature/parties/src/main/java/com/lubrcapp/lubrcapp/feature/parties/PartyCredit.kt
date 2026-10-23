package com.lubrcapp.lubrcapp.feature.parties

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Phase 14.4 — pure customer-credit rules (master spec §18) over the
 * only balance fact that exists today: the opening balance.
 *
 * Until the Phase 22 customer ledger exists there are no transactions
 * to age, so "outstanding" here is the receivable side of the opening
 * balance alone — the amount the party currently owes the business
 * (see [Party.openingBalanceDirection]). A payable-side or neutral
 * opening balance is not credit exposure and yields zero outstanding.
 * The sales phases (17/21) will recompute this from the ledger; the
 * warning thresholds and precedence defined here are the contract
 * they inherit.
 *
 * Thresholds: the spec's only concrete example is a party at 95 of a
 * 100 limit being warned, so near-limit fires at utilization >= 95%
 * (not an invented second number); over-limit at >= 100%. A blocked
 * party reports [Status.BLOCKED] ahead of every limit state.
 */
object PartyCredit {

    /** Utilization at or above which a party counts as near its limit. */
    val NEAR_LIMIT_FRACTION: BigDecimal = BigDecimal("0.95")

    enum class Status {
        /** No usable credit limit is set, so no limit state can be computed. */
        NO_LIMIT,
        WITHIN_LIMIT,
        NEAR_LIMIT,
        OVER_LIMIT,
        BLOCKED,
    }

    data class Assessment(
        /** Receivable-side outstanding (opening balance only, pre-ledger). */
        val outstanding: BigDecimal,
        val creditLimit: BigDecimal?,
        /** outstanding / creditLimit, or null when no usable limit exists. */
        val utilization: BigDecimal?,
        val status: Status,
    )

    fun assess(party: Party): Assessment {
        val outstanding = receivableOutstanding(party)
        val limit = party.creditLimit?.takeIf { it.signum() > 0 }
        val utilization = limit?.let {
            outstanding.divide(it, 6, RoundingMode.HALF_UP)
        }
        val status = when {
            party.isBlocked -> Status.BLOCKED
            limit == null -> Status.NO_LIMIT
            utilization!! >= BigDecimal.ONE -> Status.OVER_LIMIT
            utilization >= NEAR_LIMIT_FRACTION -> Status.NEAR_LIMIT
            else -> Status.WITHIN_LIMIT
        }
        return Assessment(
            outstanding = outstanding,
            creditLimit = party.creditLimit,
            utilization = utilization,
            status = status,
        )
    }

    /** The part of the opening balance the party owes the business; zero otherwise. */
    fun receivableOutstanding(party: Party): BigDecimal {
        val balance = party.openingBalance ?: return BigDecimal.ZERO
        return if (party.openingBalanceDirection() == OpeningBalanceDirection.RECEIVABLE) {
            balance.abs()
        } else {
            BigDecimal.ZERO
        }
    }
}
