package me.cyljacky02.loafylib.animation.core

/**
 * A named sequence of animation actions.
 *
 * Sequences are the primary unit of animation - they contain an ordered
 * list of actions that execute sequentially.
 *
 * @property id Unique identifier for this sequence (used in registry and YAML)
 * @property description Human-readable description of what this animation does
 * @property actions The ordered list of actions to execute
 */
data class AnimationSequence(
    val id: String,
    val description: String = "",
    val actions: List<AnimationAction>
) {
    init {
        require(id.isNotBlank()) { "Animation sequence id cannot be blank" }
        require(actions.isNotEmpty()) { "Animation sequence must have at least one action" }
    }

    /**
     * Total duration of this sequence in ticks.
     * Sum of all action durations.
     */
    val totalDurationTicks: Int
        get() = actions.sumOf { it.durationTicks }

    /**
     * Number of actions in this sequence.
     */
    val actionCount: Int
        get() = actions.size
}

