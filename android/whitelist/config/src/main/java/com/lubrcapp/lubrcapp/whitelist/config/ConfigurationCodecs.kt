package com.lubrcapp.lubrcapp.whitelist.config

import com.lubrcapp.lubrcapp.domain.common.Percentage

object ConfigurationCodecs {
    fun csvSet(raw: String?): Set<String> =
        raw.orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    fun paymentMethods(raw: String?): List<PaymentMethodConfig>? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = value.split(',').mapNotNull { entry ->
            val parts = entry.trim().split(':', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].trim()
            val label = parts[1].trim()
            if (id.isEmpty() || label.isEmpty()) null else PaymentMethodConfig(id, label)
        }
        return parsed.takeIf { it.isNotEmpty() }
    }

    fun argb(raw: String?): Int? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val clean = value.removePrefix("#").removePrefix("0x").removePrefix("0X")
        val normalized = when (clean.length) {
            6 -> "FF$clean"
            8 -> clean
            else -> return null
        }
        return normalized.toLongOrNull(16)?.toInt()
    }

    fun boolean(raw: String?): Boolean? =
        when (raw?.trim()?.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }

    fun percentage(raw: String?): Percentage? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { Percentage.of(it) }.getOrNull()
        }

    fun optional(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
}
