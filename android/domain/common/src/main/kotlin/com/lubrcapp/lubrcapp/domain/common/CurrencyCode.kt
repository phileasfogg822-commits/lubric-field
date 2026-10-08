package com.lubrcapp.lubrcapp.domain.common

import java.util.Locale

@JvmInline
value class CurrencyCode(val value: String) {
    init {
        require(CODE.matches(value)) {
            "Currency code must be exactly three uppercase letters"
        }
    }

    override fun toString(): String = value

    companion object {
        private val CODE = Regex("[A-Z]{3}")

        fun of(raw: String): CurrencyCode =
            CurrencyCode(raw.trim().uppercase(Locale.ROOT))
    }
}
