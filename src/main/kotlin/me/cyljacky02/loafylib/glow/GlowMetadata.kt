package me.cyljacky02.loafylib.glow

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes

internal const val ENTITY_FLAGS_INDEX = 0
internal const val GLOWING_FLAG_MASK = 0x40
internal const val INVISIBLE_FLAG_MASK = 0x20

internal val GLOWING_FLAG: Byte = GLOWING_FLAG_MASK.toByte()
internal val INVISIBLE_GLOWING_FLAGS: Byte = (INVISIBLE_FLAG_MASK or GLOWING_FLAG_MASK).toByte()

internal data class GlowFlagMutation(
    val observedFlags: Byte?,
    val mergedFlags: Byte?,
    val modified: Boolean
)

internal data class GlowMetadataMutation(
    val observedFlags: Byte?,
    val entityMetadata: List<EntityData<*>>,
    val modified: Boolean
)

internal fun captureAndMaybeApplyGlowingFlags(
    observedFlags: Byte?,
    glowing: Boolean
): GlowFlagMutation {
    if (observedFlags != null) {
        if (!glowing) {
            return GlowFlagMutation(observedFlags, observedFlags, modified = false)
        }

        val mergedFlags = withGlowingFlag(observedFlags)
        return GlowFlagMutation(observedFlags, mergedFlags, modified = mergedFlags != observedFlags)
    }

    if (!glowing) {
        return GlowFlagMutation(observedFlags = null, mergedFlags = null, modified = false)
    }

    return GlowFlagMutation(observedFlags = 0, mergedFlags = GLOWING_FLAG, modified = true)
}

internal fun captureAndMaybeApplyGlowing(
    entityMetadata: List<EntityData<*>>,
    glowing: Boolean
): GlowMetadataMutation {
    val metadata = entityMetadata.toMutableList()

    for (i in metadata.indices) {
        val entry = metadata[i]
        if (entry.index != ENTITY_FLAGS_INDEX || entry.type != EntityDataTypes.BYTE) continue

        val mutation = captureAndMaybeApplyGlowingFlags((entry.value as? Byte) ?: 0, glowing)
        if (!mutation.modified || mutation.mergedFlags == null) {
            return GlowMetadataMutation(mutation.observedFlags, entityMetadata, modified = false)
        }

        metadata[i] = EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, mutation.mergedFlags)
        return GlowMetadataMutation(mutation.observedFlags, metadata, modified = true)
    }

    val mutation = captureAndMaybeApplyGlowingFlags(observedFlags = null, glowing = glowing)
    if (!mutation.modified || mutation.mergedFlags == null) {
        return GlowMetadataMutation(observedFlags = null, entityMetadata = entityMetadata, modified = false)
    }

    metadata.add(EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, mutation.mergedFlags))
    return GlowMetadataMutation(mutation.observedFlags, metadata, modified = true)
}

internal fun withGlowingFlag(serverFlags: Byte): Byte {
    return (serverFlags.toInt() or GLOWING_FLAG_MASK).toByte()
}
