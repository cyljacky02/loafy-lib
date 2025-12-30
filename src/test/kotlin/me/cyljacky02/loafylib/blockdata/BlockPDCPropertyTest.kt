package me.cyljacky02.loafylib.blockdata

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import org.bukkit.block.Block

/**
 * Property-based tests for BlockPDC key generation and parsing.
 *
 * Tests the coordinate-based key system which is the foundation of
 * block data storage. These are pure functions that can be fully tested
 * without complex Bukkit PDC mocking.
 */
class BlockPDCPropertyTest : FunSpec({

    context("Key Round-Trip Consistency") {
        /**
         * For any valid block coordinates, generating a key and parsing it
         * should return the same relative coordinates. This is the core
         * round-trip property that ensures data integrity.
         */
        test("key generation and parsing are inverse operations") {
            val plugin = mockk<org.bukkit.plugin.Plugin> {
                every { name } returns "testplugin"
                every { namespace() } returns "testplugin"
            }

            checkAll(100, Arb.int(-30000000..30000000), Arb.int(-64..320), Arb.int(-30000000..30000000)) { x, y, z ->
                val block = mockk<Block> {
                    every { this@mockk.x } returns x
                    every { this@mockk.y } returns y
                    every { this@mockk.z } returns z
                }

                val key = BlockPDC.createBlockKey(block, plugin)
                val parsed = BlockPDC.parseBlockKey(key.key)
                
                parsed shouldNotBe null
                val (parsedX, parsedY, parsedZ) = parsed!!
                
                // X and Z are relative to chunk (0-15), Y is absolute
                parsedX shouldBe (x and 0x000F)
                parsedY shouldBe y
                parsedZ shouldBe (z and 0x000F)
            }
        }
        
        test("key namespace matches plugin") {
            val plugin = mockk<org.bukkit.plugin.Plugin> {
                every { name } returns "myplugin"
                every { namespace() } returns "myplugin"
            }
            
            val block = mockk<Block> {
                every { x } returns 100
                every { y } returns 64
                every { z } returns 200
            }
            
            val key = BlockPDC.createBlockKey(block, plugin)
            key.namespace shouldBe "myplugin"
        }
    }

    context("Key Parsing Edge Cases") {
        test("parseBlockKey returns null for invalid formats") {
            BlockPDC.parseBlockKey("invalid") shouldBe null
            BlockPDC.parseBlockKey("gggggg") shouldBe null  // Invalid hex
            BlockPDC.parseBlockKey("") shouldBe null
            BlockPDC.parseBlockKey("xyz") shouldBe null
        }

        test("parseBlockKey handles boundary coordinates") {
            // Maximum chunk-relative X/Z (15, 15) with Y=320
            // packed = (15 << 16) | (15 << 12) | (320 + 2048) = 0xFFF940
            BlockPDC.parseBlockKey("ff940") shouldBe Triple(15, 320, 15)
            
            // Minimum Y (deep dark) at (0, -64, 0)
            // packed = (0 << 16) | (0 << 12) | (-64 + 2048) = 0x7C0
            BlockPDC.parseBlockKey("007c0") shouldBe Triple(0, -64, 0)
            
            // Zero coordinates (0, 0, 0)
            // packed = (0 << 16) | (0 << 12) | (0 + 2048) = 0x800
            BlockPDC.parseBlockKey("00800") shouldBe Triple(0, 0, 0)
        }
        
        test("packBlockKey and unpackBlockKey are inverse operations") {
            // Test various coordinate combinations
            val testCases = listOf(
                Triple(0, 0, 0),
                Triple(15, 320, 15),
                Triple(0, -64, 0),
                Triple(8, 100, 12),
                Triple(15, -2048, 15),  // Min Y
                Triple(0, 2047, 0),     // Max Y
            )
            
            testCases.forEach { (relX, absY, relZ) ->
                val packed = BlockPDC.packBlockKey(relX, absY, relZ)
                val unpacked = BlockPDC.unpackBlockKey(packed)
                unpacked shouldBe Triple(relX, absY, relZ)
            }
        }
    }
})
