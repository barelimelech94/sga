package com.bar.sga.data

import java.util.Locale

/** Declaration order is the display order. */
enum class BodyGroup {
    CHEST, BACK, SHOULDERS, REAR_DELTS, BICEPS, TRICEPS, LEGS, CORE, OTHER;

    companion object {
        private fun key(s: String) = s.trim().replace(Regex("[\\s-]+"), "_").uppercase(Locale.ROOT)

        private val byName = entries.associateBy { it.name }
        private val hebrew = mapOf(
            "חזה" to CHEST, "גב" to BACK, "כתפיים" to SHOULDERS, "כתף אחורית" to REAR_DELTS,
            "יד קדמית" to BICEPS, "יד אחורית" to TRICEPS, "רגליים" to LEGS, "בטן" to CORE,
        ).mapKeys { key(it.key) }

        /** Tolerant: databases and old backups hold free text. Anything unrecognised is [OTHER]. */
        fun from(stored: String): BodyGroup = key(stored).let { byName[it] ?: hebrew[it] ?: OTHER }
    }
}

val Exercise.bodyGroup: BodyGroup get() = BodyGroup.from(bodyPart)
