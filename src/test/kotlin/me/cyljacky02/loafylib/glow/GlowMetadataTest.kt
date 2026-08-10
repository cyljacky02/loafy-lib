package me.cyljacky02.loafylib.glow

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GlowMetadataTest : FunSpec({

    test("captureAndMaybeApplyGlowingFlags preserves existing flags while adding glow") {
        val mutation = captureAndMaybeApplyGlowingFlags(INVISIBLE_FLAG_MASK.toByte(), glowing = true)

        mutation.observedFlags shouldBe INVISIBLE_FLAG_MASK.toByte()
        mutation.mergedFlags shouldBe INVISIBLE_GLOWING_FLAGS
        mutation.modified shouldBe true
    }

    test("captureAndMaybeApplyGlowingFlags creates a flags byte when none was observed") {
        val mutation = captureAndMaybeApplyGlowingFlags(observedFlags = null, glowing = true)

        mutation.observedFlags shouldBe 0.toByte()
        mutation.mergedFlags shouldBe GLOWING_FLAG
        mutation.modified shouldBe true
    }

    test("captureAndMaybeApplyGlowingFlags caches flags without mutating when glow is not requested") {
        val mutation = captureAndMaybeApplyGlowingFlags(0x05.toByte(), glowing = false)

        mutation.observedFlags shouldBe 0x05.toByte()
        mutation.mergedFlags shouldBe 0x05.toByte()
        mutation.modified shouldBe false
    }

    test("withGlowingFlag preserves unrelated entity flag bits") {
        withGlowingFlag(0x05.toByte()) shouldBe 0x45.toByte()
    }
})
