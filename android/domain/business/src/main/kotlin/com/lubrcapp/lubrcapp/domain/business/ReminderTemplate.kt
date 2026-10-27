package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 29.3 — reminder templates (§102).
 * A template is DATA, not a hardcoded string:
 * §102 says templates must be editable, so the
 * body lives on the record and rendering only
 * substitutes `{name}` placeholders from the
 * values the caller supplies.
 *
 * A placeholder with no supplied value is left
 * intact, never silently dropped — a rendered
 * message must never lose a field without a
 * trace. Names are letters, digits and
 * underscores; anything else inside braces
 * (including spaces) is literal text.
 */
data class ReminderTemplate(
    val id: String,
    val body: String = "",
)

object ReminderTemplateOps {
    private val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)\\}")

    fun placeholders(template: ReminderTemplate): Set<String> =
        PLACEHOLDER.findAll(template.body).map { it.groupValues[1] }.toSet()

    fun render(template: ReminderTemplate, values: Map<String, String>): String =
        PLACEHOLDER.replace(template.body) { match ->
            values[match.groupValues[1]] ?: match.value
        }
}
