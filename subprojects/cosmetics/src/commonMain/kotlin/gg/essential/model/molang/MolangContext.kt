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
package gg.essential.model.molang

import gg.essential.model.ParticleSystem
import kotlin.random.Random
import kotlin.reflect.KProperty
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MolangContext(
    val query: MolangQuery,
    val variables: Variables = VariablesMap(),
)

interface MolangQuery {
    object Empty : MolangQuery
}

/** Not an official query but used internally so we can get deterministic results if we need them. */
interface MolangQueryRandom : MolangQuery {
    val random: Random
}

interface MolangQueryAnimation : MolangQuery {
    val animTime: Float
    /** Same as [animTime] but modulo animation length for looping animations. Not part of official Molang. */
    val animLoopTime: Float
}

interface MolangQueryTime : MolangQuery {
    val time: Float
}

@OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
interface MolangQueryEntity : MolangQuery, MolangQueryTime {
    val lifeTime: Float
    val modifiedDistanceMoved: Float
    val modifiedMoveSpeed: Float
    /** Internal query for retrieving entity the position and rotation of the entity (for global-space particles). */
    val locator: ParticleSystem.Locator
    val uuid: Uuid?

    override val time: Float
        get() = lifeTime
}

interface Variables {
    /** Returns the variable with the given name or null if no such variable exists. */
    fun getOrNull(name: String): Variable?

    /** Returns the variable with the given name. Initializing it with [initialValue] if it does not yet exist. */
    fun getOrPut(name: String, initialValue: Float = 0f): Variable

    /** Returns the value of the variable with the given name or 0 if no such variable exists. */
    operator fun get(name: String): Float = getOrNull(name)?.get() ?: 0f

    /** Sets the value of the variable with the given name. Creates the variable if it does not yet exist. */
    operator fun set(name: String, value: Float) = getOrPut(name).set(value)

    /**
     * Returns a new [Variables] instance that contains the variables of `this` instance and the [fallback] instance.
     * When both instances contain a variable, the one in `this` instance is returned.
     * New variables will be create in `this` instance only.
     */
    fun fallbackBackTo(fallback: Variables): Variables = VariablesWithFallback(this, fallback)

    interface Variable {
        fun get(): Float
        fun set(value: Float)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Float = get()
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) = set(value)
    }
}

class VariablesMap(private val map : MutableMap<String, Variable> = mutableMapOf()) : Variables {

    override fun getOrNull(name: String): Variables.Variable? =
        map[name]

    override fun getOrPut(name: String, initialValue: Float): Variables.Variable =
        map.getOrPut(name) { Variable(initialValue) }

    fun copy(): VariablesMap {
        return VariablesMap(map.mapValues{ it.value.copy() }.toMutableMap())
    }

    class Variable(var field: Float) : Variables.Variable {
        override fun get(): Float = field
        override fun set(value: Float) { field = value }

        fun copy(): Variable = Variable(field)
    }
}

private class VariablesWithFallback(val primary: Variables, val fallback: Variables) : Variables {
    override fun getOrNull(name: String): Variables.Variable? = primary.getOrNull(name) ?: fallback.getOrNull(name)

    override fun getOrPut(name: String, initialValue: Float): Variables.Variable =
        getOrNull(name) ?: primary.getOrPut(name, initialValue)
}

