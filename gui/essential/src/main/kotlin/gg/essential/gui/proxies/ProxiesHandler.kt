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
package gg.essential.gui.proxies

import gg.essential.elementa.UIComponent
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box

interface ProxiesHandler {
    fun register(id: String, container: UIComponent, mounted: MutableState<State<Boolean>>)

    object None : ProxiesHandler {
        override fun register(id: String, container: UIComponent, mounted: MutableState<State<Boolean>>) {}
    }
}

fun LayoutScope.mountWithProxy(proxiesHandler: ProxiesHandler, id: String, modifier: Modifier = Modifier, block: LayoutScope.() -> Unit) {
    val mounted = mutableStateOf(stateOf(true))
    lateinit var container: UIComponent
    if_({ mounted()() }) {
        // use box to create a container to hold the components and send to the proxies
        container = box(modifier) {
            block() // may setup any components, even none or conditional ones
        }
    }
    proxiesHandler.register(id, container, mounted)
}
