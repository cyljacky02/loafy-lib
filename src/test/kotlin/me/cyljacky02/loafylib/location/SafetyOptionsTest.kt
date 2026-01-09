package me.cyljacky02.loafylib.location

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for SafetyOptions presets.
 */
class SafetyOptionsTest : FunSpec({

    context("SafetyOptions Preset Invariants") {

        test("DEFAULT preset has checkCollision=true, allowWater=false, checkWorldBorder=true, checkYBounds=true, checkEntities=false") {
            SafetyOptions.DEFAULT.checkCollision shouldBe true
            SafetyOptions.DEFAULT.allowWater shouldBe false
            SafetyOptions.DEFAULT.checkWorldBorder shouldBe true
            SafetyOptions.DEFAULT.checkYBounds shouldBe true
            SafetyOptions.DEFAULT.checkEntities shouldBe false
        }

        test("FAST preset has checkCollision=false") {
            SafetyOptions.FAST.checkCollision shouldBe false
            // Other values should be defaults
            SafetyOptions.FAST.allowWater shouldBe false
            SafetyOptions.FAST.checkWorldBorder shouldBe true
            SafetyOptions.FAST.checkYBounds shouldBe true
            SafetyOptions.FAST.checkEntities shouldBe false
        }

        test("AQUATIC preset has allowWater=true") {
            SafetyOptions.AQUATIC.allowWater shouldBe true
            // Other values should be defaults
            SafetyOptions.AQUATIC.checkCollision shouldBe true
            SafetyOptions.AQUATIC.checkWorldBorder shouldBe true
            SafetyOptions.AQUATIC.checkYBounds shouldBe true
            SafetyOptions.AQUATIC.checkEntities shouldBe false
        }

        test("STRICT preset has checkEntities=true") {
            SafetyOptions.STRICT.checkEntities shouldBe true
            // Other values should be defaults
            SafetyOptions.STRICT.checkCollision shouldBe true
            SafetyOptions.STRICT.allowWater shouldBe false
            SafetyOptions.STRICT.checkWorldBorder shouldBe true
            SafetyOptions.STRICT.checkYBounds shouldBe true
        }
    }

    context("SafetyOptions default constructor values") {

        test("Default constructor matches DEFAULT preset") {
            val options = SafetyOptions()
            options shouldBe SafetyOptions.DEFAULT
        }

        test("checkCollision defaults to true") {
            SafetyOptions().checkCollision shouldBe true
        }

        test("allowWater defaults to false") {
            SafetyOptions().allowWater shouldBe false
        }

        test("checkWorldBorder defaults to true") {
            SafetyOptions().checkWorldBorder shouldBe true
        }

        test("checkYBounds defaults to true") {
            SafetyOptions().checkYBounds shouldBe true
        }

        test("checkEntities defaults to false") {
            SafetyOptions().checkEntities shouldBe false
        }
    }

    context("SafetyOptions immutability") {

        test("Data class copy creates new instance with modified values") {
            val original = SafetyOptions.DEFAULT
            val modified = original.copy(allowWater = true)

            modified.allowWater shouldBe true
            original.allowWater shouldBe false // Original unchanged
        }
    }
})
