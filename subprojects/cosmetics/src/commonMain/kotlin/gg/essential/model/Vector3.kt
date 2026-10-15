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
package gg.essential.model

import kotlin.jvm.JvmField
import kotlin.math.*

//Taken from https://github.com/markaren/three.kt
data class Vector3(
    @JvmField
    var x: Float,
    @JvmField
    var y: Float,
    @JvmField
    var z: Float
) {


    constructor() : this(0f, 0f, 0f)

    constructor(x: Number, y: Number, z: Number) : this(x.toFloat(), y.toFloat(), z.toFloat())

    /**
     * Sets value of Vector3 vector.
     */
    fun set(x: Number, y: Number, z: Number): Vector3 {
        this.x = x.toFloat()
        this.y = y.toFloat()
        this.z = z.toFloat()
        return this
    }

    /**
     * Sets all values of Vector3 vector.
     */
    fun setScalar(s: Number): Vector3 {
        return set(s, s, s)
    }

    operator fun set(index: Int, value: Float): Vector3 {
        when (index) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
            else -> throw IndexOutOfBoundsException()
        }
        return this
    }

    operator fun get(index: Int): Float {
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IndexOutOfBoundsException()
        }
    }

    /**
     * Clones Vector3 vector.
     */
    fun clone() = copy()

    /**
     * Copies value of v to Vector3 vector.
     */
    fun copy(v: Vector3): Vector3 {
        return set(v.x, v.y, v.z)
    }

    /**
     * Adds v to Vector3 vector.
     */
    fun add(v: Vector3): Vector3 {
        this.x += v.x
        this.y += v.y
        this.z += v.z

        return this
    }

    fun addScalar(s: Float): Vector3 {
        this.x += s
        this.y += s
        this.z += s

        return this
    }

    /**
     * Sets Vector3 vector to a + b.
     */
    fun addVectors(a: Vector3, b: Vector3): Vector3 {
        this.x = a.x + b.x
        this.y = a.y + b.y
        this.z = a.z + b.z

        return this
    }

    /**
     * Subtracts v from Vector3 vector.
     */
    fun sub(v: Vector3): Vector3 {
        this.x -= v.x
        this.y -= v.y
        this.z -= v.z

        return this
    }

    /**
     * Sets Vector3 vector to a - b.
     */
    fun subVectors(a: Vector3, b: Vector3): Vector3 {
        this.x = a.x - b.x
        this.y = a.y - b.y
        this.z = a.z - b.z

        return this
    }

    fun multiply(v: Vector3): Vector3 {
        this.x *= v.x
        this.y *= v.y
        this.z *= v.z

        return this
    }

    /**
     * Multiplies Vector3 vector by scalar s.
     */
    fun multiplyScalar(s: Float): Vector3 {
        this.x *= s
        this.y *= s
        this.z *= s

        return this
    }


    /**
     * Divides Vector3 vector by scalar s.
     * Set vector to ( 0, 0, 0 ) if s == 0.
     */
    fun divideScalar(s: Float): Vector3 {
        return this.multiplyScalar(1f / s)
    }

    fun min(v: Vector3): Vector3 {
        this.x = kotlin.math.min(this.x, v.x)
        this.y = kotlin.math.min(this.y, v.y)
        this.z = kotlin.math.min(this.z, v.z)

        return this
    }

    fun max(v: Vector3): Vector3 {
        this.x = kotlin.math.max(this.x, v.x)
        this.y = kotlin.math.max(this.y, v.y)
        this.z = kotlin.math.max(this.z, v.z)

        return this
    }

    fun floor(): Vector3 {
        this.x = kotlin.math.floor(this.x)
        this.y = kotlin.math.floor(this.y)
        this.z = kotlin.math.floor(this.z)

        return this
    }

    fun ceil(): Vector3 {
        this.x = kotlin.math.ceil(this.x)
        this.y = kotlin.math.ceil(this.y)
        this.z = kotlin.math.ceil(this.z)

        return this
    }

    fun round(): Vector3 {
        this.x = this.x.roundToInt().toFloat()
        this.y = this.y.roundToInt().toFloat()
        this.z = this.z.roundToInt().toFloat()

        return this
    }

    /**
     * Inverts Vector3 vector.
     */
    fun negate(): Vector3 {
        this.x = -this.x
        this.y = -this.y
        this.z = -this.z

        return this
    }

    fun negateY(): Vector3 {
        this.y = -this.y

        return this
    }

    /**
     * Computes dot product of Vector3 vector and v.
     */
    fun dot(v: Vector3): Float {
        return this.x * v.x + this.y * v.y + this.z * v.z
    }

    /**
     * Computes length of Vector3 vector.
     */
    fun length(): Float {
        return sqrt(this.x * this.x + this.y * this.y + this.z * this.z)
    }



    /**
     * Normalizes Vector3 vector.
     */
    fun normalize(): Vector3 {
        var length = length()
        if (length.isNaN()) length = 1.toFloat()
        return this.divideScalar(length)
    }

    /**
     * Normalizes Vector3 vector and multiplies it by l.
     */
    fun setLength(length: Float): Vector3 {
        return this.normalize().multiplyScalar(length)
    }

    fun lerp(v: Vector3, alpha: Float): Vector3 {
        this.x += (v.x - this.x) * alpha
        this.y += (v.y - this.y) * alpha
        this.z += (v.z - this.z) * alpha

        return this
    }

    /**
     * Sets Vector3 vector to cross product of itself and v.
     */
    fun cross(v: Vector3): Vector3 {
        return this.crossVectors(this, v)
    }

    /**
     * Sets Vector3 vector to cross product of a and b.
     */
    fun crossVectors(a: Vector3, b: Vector3): Vector3 {
        val ax = a.x
        val ay = a.y
        val az = a.z
        val bx = b.x
        val by = b.y
        val bz = b.z

        this.x = ay * bz - az * by
        this.y = az * bx - ax * bz
        this.z = ax * by - ay * bx

        return this
    }

    fun reflect(normal: Vector3): Vector3 {
        val v1 = Vector3()
        return this.sub(v1.copy(normal).multiplyScalar(2 * this.dot(normal)))
    }

    /**
     * Computes distance of Vector3 vector to v.
     */
    fun distanceTo(v: Vector3): Float {
        return sqrt(this.distanceToSquared(v))
    }

    /**
     * Computes squared distance of Vector3 vector to v.
     */
    fun distanceToSquared(v: Vector3): Float {
        val dx = this.x - v.x
        val dy = this.y - v.y
        val dz = this.z - v.z
        return dx * dx + dy * dy + dz * dz
    }


    operator fun plus(b: Vector3): Vector3 {
        return copy().add(b)
    }

    operator fun minus(b: Vector3): Vector3 {
        return copy().sub(b)
    }


    companion object {

        @JvmField
        val ZERO = Vector3(0f, 0f, 0f)
        @JvmField
        val X = Vector3(1f, 0f, 0f)
        @JvmField
        val Y = Vector3(0f, 1f, 0f)
        @JvmField
        val Z = Vector3(0f, 0f, 1f)

    }

}
