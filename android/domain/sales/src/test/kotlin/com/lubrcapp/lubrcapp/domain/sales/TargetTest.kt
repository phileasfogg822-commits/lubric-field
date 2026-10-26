package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.BrandId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TargetTest {

    private val pkr = CurrencyCode.of("PKR")
    private val owner = UserId("sales-1")

    private fun valueTarget(): SalesTarget = SalesTarget(
        id = "t-1",
        ownerId = owner,
        type = TargetType.SALES_VALUE,
        period = "2026-10",
        goalAmount = Money.of("50000", pkr),
    )

    @Test
    fun validatePassesForEachGoalShape() {
        TargetOps.validate(valueTarget())
        TargetOps.validate(
            SalesTarget("t-2", owner, TargetType.ORDERS, goalCount = 10),
        )
        TargetOps.validate(
            SalesTarget(
                "t-3", owner, TargetType.SPECIFIC_PRODUCT,
                itemId = ItemId("oil-1"), goalQuantity = Quantity.of("100"),
            ),
        )
        TargetOps.validate(
            SalesTarget(
                "t-4", owner, TargetType.BRAND,
                brandId = BrandId("shell"), goalAmount = Money.of("20000", pkr),
            ),
        )
    }

    @Test
    fun validateThrowsOnMissingGoalFields() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetOps.validate(SalesTarget("t-1", owner, TargetType.SALES_VALUE))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetOps.validate(SalesTarget("t-2", owner, TargetType.ORDERS))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetOps.validate(
                SalesTarget("t-3", owner, TargetType.SPECIFIC_PRODUCT, itemId = ItemId("oil-1")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetOps.validate(
                SalesTarget("t-4", owner, TargetType.BRAND, goalAmount = Money.of("20000", pkr)),
            )
        }
    }

    @Test
    fun progressValueTargetAsDashboardPercent() {
        val progress = TargetOps.progress(valueTarget(), achievedAmount = Money.of("36000", pkr))
        assertEquals(Percentage.of("72"), progress)
    }

    @Test
    fun progressCountTarget() {
        val target = SalesTarget("t-2", owner, TargetType.ORDERS, goalCount = 10)
        assertEquals(Percentage.of("70"), TargetOps.progress(target, achievedCount = 7))
    }

    @Test
    fun progressProductTargetByQuantity() {
        val target = SalesTarget(
            "t-3", owner, TargetType.SPECIFIC_PRODUCT,
            itemId = ItemId("oil-1"), goalQuantity = Quantity.of("10"),
        )
        assertEquals(
            Percentage.of("80"),
            TargetOps.progress(target, achievedQuantity = Quantity.of("8")),
        )
    }

    @Test
    fun progressIsUnclampedAboveHundred() {
        val progress = TargetOps.progress(valueTarget(), achievedAmount = Money.of("60000", pkr))
        assertEquals(Percentage.of("120"), progress)
    }

    @Test
    fun progressThrowsOnCurrencyMismatch() {
        val usd = CurrencyCode.of("USD")
        assertThrows(IllegalArgumentException::class.java) {
            TargetOps.progress(valueTarget(), achievedAmount = Money.of("100", usd))
        }
    }

    @Test
    fun progressThrowsOnMissingAchieved() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetOps.progress(valueTarget())
        }
    }
}
