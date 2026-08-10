package me.cyljacky02.loafylib.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * TypeSerializer for Kotlin's [Duration] type.
 *
 * Supports deserializing from:
 * - Numbers: interpreted as seconds (e.g., `30` → 30 seconds)
 * - ISO-8601 duration strings via [Duration.parse] (e.g., `PT1H30M`)
 * - Human-friendly strings: `5s`, `30m`, `2h`, `1d`, `100ms`, or combinations like `1d12h30m`
 *
 * Serializes to ISO-8601 format via [Duration.toString].
 */
class DurationTypeSerializer : TypeSerializer<Duration> {

    override fun deserialize(type: Type, node: ConfigurationNode): Duration {
        val raw = node.raw() ?: return Duration.ZERO

        return when (raw) {
            is Number -> raw.toLong().seconds
            is String -> parseDuration(raw) ?: Duration.ZERO
            else -> throw SerializationException(node, type, "Unsupported duration value: ${raw::class.java.name}")
        }
    }

    override fun serialize(type: Type, obj: Duration?, node: ConfigurationNode) {
        if (obj == null) {
            node.set(null)
            return
        }

        node.set(obj.toString())
    }

    companion object {
        // Pre-compiled regex for parsing human-friendly duration strings
        private val DURATION_REGEX = Regex("(\\d+(?:\\.\\d+)?)(ms|s|m|h|d)", RegexOption.IGNORE_CASE)

        /**
         * Parses a duration string like "100ms", "5s", "30m", "2h", "1d" or combinations like "1d12h30m".
         * Also accepts ISO-8601 format (e.g., "PT1H30M"). Returns null if the format is invalid.
         */
        fun parseDuration(input: String): Duration? {
            val text = input.trim().removeSurrounding("\"")
            if (text.isEmpty()) return Duration.ZERO

            try {
                return Duration.parse(text)
            } catch (_: IllegalArgumentException) {
                // Fall through to custom parsing
            }

            return try {
                var index = 0
                var total = Duration.ZERO

                for (match in DURATION_REGEX.findAll(text)) {
                    if (match.range.first != index) {
                        return null
                    }

                    val amount = match.groupValues[1].toDouble()
                    val unit = match.groupValues[2].lowercase()

                    total += when (unit) {
                        "ms" -> (amount).milliseconds
                        "s" -> (amount).seconds
                        "m" -> (amount).minutes
                        "h" -> (amount).hours
                        "d" -> (amount).days
                        else -> return null
                    }

                    index = match.range.last + 1
                }

                if (index != text.length) null else total
            } catch (_: Exception) {
                null
            }
        }
    }
}
