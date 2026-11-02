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

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.*

val USD_CURRENCY: Currency = Currency.getInstance("USD")

val USD_DECIMAL_FORMAT: DecimalFormat = DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US))

fun Currency.format(price: Double) = when (this) {
    USD_CURRENCY -> "$" + USD_DECIMAL_FORMAT.format(price)
    else -> try {
        val format = NumberFormat.getCurrencyInstance()
        format.currency = this
        format.format(price).replace("\u00a0", " ") // Replace $nbsp with normal space, Minecraft shows the $nbsp as a character....
    } catch (e: IllegalArgumentException) {
        "${this.currencyCode} $price"
    }
}

