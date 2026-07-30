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
package gg.essential.util

inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun <T, K, R> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, R>?): Map<K, R> = buildMap {
    for (item in this@associateNotNull) {
        val (key, value) = transform(item) ?: continue
        this[key] = value
    }
}


fun <T> Iterable<T>.isSorted(comparator: Comparator<T>): Boolean = iterator().isSorted(comparator)
fun <T> Iterator<T>.isSorted(comparator: Comparator<T>): Boolean {
    if (!hasNext()) return true
    var prev: T = next()
    while (hasNext()) {
        val next = next()
        if (comparator.compare(prev, next) > 0) {
            return false
        }
        prev = next
    }
    return true
}
