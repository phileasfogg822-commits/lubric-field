package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.BrandId
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Phase 27.6 — salesperson targets (§157 optional
 * target types, §158 dashboard Target %). Pure model:
 * a target belongs to one salesperson for one period
 * (period is a plain string, e.g. a month — no period
 * type exists, and inventing one is out of scope).
 *
 * Types are exactly the §157 list: Sales Value,
 * Collection, Orders, New Customers, Specific Product,
 * Brand. The goal shape differs by type, so the record
 * carries the typed goal fields and [TargetOps]
 * validates that the fields a type needs are present
 * and positive — a target with the wrong goal shape
 * is rejected, not stored half-formed:
 * - Sales Value / Collection: goalAmount (Money).
 * - Orders / New Customers: goalCount.
 * - Specific Product: itemId + goalQuantity.
 * - Brand: brandId + goalAmount (brand sales value).
 *
 * Progress is UNCLAMPED: overachievement shows
 * above 100, like any dashboard. Currency mismatch
 * between achieved and goal amounts throws — a
 * percentage across currencies is meaningless.
 * Achieved values are parameters, not stored: the
 * screen derives them from sales, collections and
 * orders at display time (the 22.x derived rule).
 */
enum class TargetType {
    SALES_VALUE,
    COLLECTION,
    ORDERS,
    NEW_CUSTOMERS,
    SPECIFIC_PRODUCT,
    BRAND,
}

data class SalesTarget(
    val id: String,
    val ownerId: UserId,
    val type: TargetType,
    val period: String = "",
    val goalAmount: Money? = null,
    val goalCount: Int? = null,
    val itemId: ItemId? = null,
    val goalQuantity: Quantity? = null,
    val brandId: BrandId? = null,
)

object TargetOps {
    fun validate(target: SalesTarget) {
        when (target.type) {
            TargetType.SALES_VALUE,
            TargetType.COLLECTION -> {
                val goal = target.goalAmount
                require(goal != null && goal.isPositive) {
                    "Value target needs a positive goal amount: ${target.id}"
                }
            }
            TargetType.ORDERS,
            TargetType.NEW_CUSTOMERS -> {
                val count = target.goalCount
                require(count != null && count > 0) {
                    "Count target needs a positive goal count: ${target.id}"
                }
            }
            TargetType.SPECIFIC_PRODUCT -> {
                require(target.itemId != null && target.goalQuantity != null && target.goalQuantity.isPositive) {
                    "Product target needs an item and a positive goal quantity: ${target.id}"
                }
            }
            TargetType.BRAND -> {
                val goal = target.goalAmount
                require(target.brandId != null && goal != null && goal.isPositive) {
                    "Brand target needs a brand and a positive goal amount: ${target.id}"
                }
            }
        }
    }

    fun progress(
        target: SalesTarget,
        achievedAmount: Money? = null,
        achievedCount: Int? = null,
        achievedQuantity: Quantity? = null,
    ): Percentage {
        validate(target)
        val points = when (target.type) {
            TargetType.SALES_VALUE,
            TargetType.COLLECTION,
            TargetType.BRAND -> {
                val goal = target.goalAmount!!
                val achieved = achievedAmount
                require(achieved != null) {
                    "Value target progress needs an achieved amount: ${target.id}"
                }
                require(achieved.currency == goal.currency) {
                    "Achieved currency must match goal currency: ${target.id}"
                }
                (achieved.amount * BigDecimal(100)).divide(goal.amount, 2, RoundingMode.HALF_UP)
            }
            TargetType.ORDERS,
            TargetType.NEW_CUSTOMERS -> {
                val achieved = achievedCount
                require(achieved != null) {
                    "Count target progress needs an achieved count: ${target.id}"
                }
                BigDecimal(achieved * 100).divide(BigDecimal(target.goalCount!!), 2, RoundingMode.HALF_UP)
            }
            TargetType.SPECIFIC_PRODUCT -> {
                val achieved = achievedQuantity
                require(achieved != null) {
                    "Product target progress needs an achieved quantity: ${target.id}"
                }
                (achieved.amount * BigDecimal(100)).divide(target.goalQuantity!!.amount, 2, RoundingMode.HALF_UP)
            }
        }
        return Percentage.of(points)
    }
}
