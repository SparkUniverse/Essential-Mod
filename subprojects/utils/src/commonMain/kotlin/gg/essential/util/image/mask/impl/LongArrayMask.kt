/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.util.image.mask.impl

import gg.essential.util.image.mask.Mask
import gg.essential.util.image.mask.MutableMask

internal class LongArrayMask(
    override val width: Int,
    override val height: Int,
    private val data: LongArray,
) : MutableMask {
    constructor(width: Int, height: Int) : this(width, height, LongArray((width * height + 63) / 64))

    override fun count(): Int {
        return data.sumOf { it.countOneBits() }
    }

    override fun get(x: Int, y: Int): Boolean {
        val index = y * width + x
        val longIndex = index shr BPL
        val bitIndex = index and INDEX_MASK
        return data[longIndex] shr bitIndex and 1 == 1L
    }

    override fun set(x: Int, y: Int) {
        val index = y * width + x
        val longIndex = index shr BPL
        val bitIndex = index and INDEX_MASK
        data[longIndex] = data[longIndex] or (1L shl bitIndex)
    }

    override fun clear(x: Int, y: Int) {
        val index = y * width + x
        val longIndex = index shr BPL
        val bitIndex = index and INDEX_MASK
        data[longIndex] = data[longIndex] and (1L shl bitIndex).inv()
    }

    override fun set(x: Int, y: Int, w: Int, h: Int) {
        if (w == width && h == height) {
            data.fill(0L.inv())
            fixFinalLong()
            return
        }
        // TODO a lot more that could be done here, but we don't really need it yet
        super.set(x, y, w, h)
    }

    override fun clear(x: Int, y: Int, w: Int, h: Int) {
        if (w == width && h == height) {
            data.fill(0L)
            return
        }
        // TODO a lot more that could be done here, but we don't really need it yet
        super.clear(x, y, w, h)
    }

    override fun inv() {
        for (i in data.indices) {
            data[i] = data[i].inv()
        }
        fixFinalLong()
    }

    // Some operations may pollute the unused bits in the final Long entry, which would throw of methods like [count],
    // so we need to clear those again.
    private fun fixFinalLong() {
        // Indices of the first unused bit
        val index = width * height
        val longIndex = index shr BPL
        val bitIndex = index and INDEX_MASK
        if (bitIndex == 0) {
            return // no unused bits, nothing to fix
        }
        data[longIndex] = data[longIndex] and (0L.inv() ushr (64 - bitIndex))
    }

    private fun checkCompatible(mask: Mask): LongArrayMask {
        check(this.width == mask.width)
        check(this.height == mask.height)

        return if (mask is LongArrayMask) {
            mask
        } else {
            intoLongArrayMask(mask)
        }
    }

    override fun setOr(other: Mask) {
        val otherData = checkCompatible(other).data
        for (i in data.indices) {
            data[i] = data[i] or otherData[i]
        }
    }

    override fun setAnd(other: Mask) {
        val otherData = checkCompatible(other).data
        for (i in data.indices) {
            data[i] = data[i] and otherData[i]
        }
    }

    override fun mutableCopy(): MutableMask {
        return LongArrayMask(width, height, data.copyOf())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Mask) return false
        if (this.width != other.width) return false
        if (this.height != other.height) return false

        val otherData = if (other is LongArrayMask) other.data else intoLongArrayMask(other).data
        return this.data.contentEquals(otherData)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        /** Bits per long. */
        private const val BPL: Int = 6
        private const val INDEX_MASK: Int = (1 shl BPL) - 1

        private fun intoLongArrayMask(mask: Mask): LongArrayMask {
            val result = LongArrayMask(mask.width, mask.height)
            for (y in 0 until mask.height) {
                for (x in 0 until mask.width) {
                    result[x, y] = mask[x, y]
                }
            }
            return result
        }
    }
}