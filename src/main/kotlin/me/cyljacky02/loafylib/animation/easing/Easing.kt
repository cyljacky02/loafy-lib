package me.cyljacky02.loafylib.animation.easing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Easing functions for smooth animation transitions.
 *
 * Each function takes a progress value from 0.0 to 1.0 and returns
 * an eased value, also typically 0.0 to 1.0 (but may overshoot for
 * elastic/back easings).
 *
 * @see <a href="https://easings.net/">Easing Functions Cheat Sheet</a>
 */
enum class Easing {
    /** Linear interpolation - no easing */
    LINEAR {
        override fun apply(t: Float): Float = t
    },

    /** Quadratic ease in - slow start */
    EASE_IN_QUAD {
        override fun apply(t: Float): Float = t * t
    },

    /** Quadratic ease out - slow end */
    EASE_OUT_QUAD {
        override fun apply(t: Float): Float = 1 - (1 - t) * (1 - t)
    },

    /** Quadratic ease in-out - slow start and end */
    EASE_IN_OUT_QUAD {
        override fun apply(t: Float): Float =
            if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
    },

    /** Cubic ease in - slower start */
    EASE_IN_CUBIC {
        override fun apply(t: Float): Float = t * t * t
    },

    /** Cubic ease out - slower end */
    EASE_OUT_CUBIC {
        override fun apply(t: Float): Float = 1 - (1 - t).pow(3)
    },

    /** Cubic ease in-out - slower start and end */
    EASE_IN_OUT_CUBIC {
        override fun apply(t: Float): Float =
            if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    },

    /** Sine ease in - gentle start */
    EASE_IN_SINE {
        override fun apply(t: Float): Float = (1 - cos(t * PI / 2)).toFloat()
    },

    /** Sine ease out - gentle end */
    EASE_OUT_SINE {
        override fun apply(t: Float): Float = sin(t * PI / 2).toFloat()
    },

    /** Sine ease in-out - gentle start and end */
    EASE_IN_OUT_SINE {
        override fun apply(t: Float): Float = (-(cos(PI * t) - 1) / 2).toFloat()
    },

    /** Exponential ease in - very slow start, fast end */
    EASE_IN_EXPO {
        override fun apply(t: Float): Float =
            if (t == 0f) 0f else 2f.pow(10 * t - 10)
    },

    /** Exponential ease out - fast start, very slow end */
    EASE_OUT_EXPO {
        override fun apply(t: Float): Float =
            if (t == 1f) 1f else 1 - 2f.pow(-10 * t)
    },

    /** Back ease in - slight overshoot at start */
    EASE_IN_BACK {
        override fun apply(t: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1
            return c3 * t * t * t - c1 * t * t
        }
    },

    /** Back ease out - slight overshoot at end */
    EASE_OUT_BACK {
        override fun apply(t: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1
            return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
        }
    },

    /** Elastic ease out - bouncy overshoot at end */
    EASE_OUT_ELASTIC {
        override fun apply(t: Float): Float {
            val c4 = (2 * PI) / 3
            return when {
                t == 0f -> 0f
                t == 1f -> 1f
                else -> 2f.pow(-10 * t) * sin((t * 10 - 0.75) * c4).toFloat() + 1
            }
        }
    },

    /** Bounce ease out - bouncing effect at end */
    EASE_OUT_BOUNCE {
        override fun apply(t: Float): Float {
            val n1 = 7.5625f
            val d1 = 2.75f
            var x = t
            return when {
                x < 1 / d1 -> n1 * x * x
                x < 2 / d1 -> {
                    x -= 1.5f / d1
                    n1 * x * x + 0.75f
                }
                x < 2.5 / d1 -> {
                    x -= 2.25f / d1
                    n1 * x * x + 0.9375f
                }
                else -> {
                    x -= 2.625f / d1
                    n1 * x * x + 0.984375f
                }
            }
        }
    };

    /**
     * Apply the easing function to a progress value.
     *
     * @param t Progress from 0.0 (start) to 1.0 (end)
     * @return Eased value (typically 0.0 to 1.0, may overshoot)
     */
    abstract fun apply(t: Float): Float

    companion object {
        /**
         * Parse an easing name from string (case-insensitive).
         *
         * @param name The easing name (e.g., "ease_out_cubic" or "EASE_OUT_CUBIC")
         * @return The easing function, or LINEAR if not found
         */
        fun fromString(name: String): Easing =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: LINEAR
    }
}

