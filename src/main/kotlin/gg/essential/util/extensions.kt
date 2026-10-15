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

import dev.folomeev.kotgl.matrix.matrices.mat3
import dev.folomeev.kotgl.matrix.matrices.mat4
import gg.essential.connectionmanager.common.model.knownserver.KnownServer
import gg.essential.elementa.utils.elementaDebug
import gg.essential.lib.gson.JsonElement
import gg.essential.lib.gson.JsonPrimitive
import gg.essential.mixins.ext.client.executor
import gg.essential.mixins.ext.client.ext
import gg.essential.mixins.ext.client.gui.LabyModMainMenu
import gg.essential.mixins.ext.client.multiplayer.ext
import gg.essential.mixins.ext.client.multiplayer.isTrusted
import gg.essential.mixins.ext.compatibility.isCMMMainMenu
import gg.essential.mixins.impl.client.MinecraftExt
import gg.essential.mixins.transformers.client.gui.GuiScreenAccessor
import gg.essential.serverdiscovery.model.ServerDiscovery
import gg.essential.universal.UImage
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMinecraft
import gg.essential.universal.utils.MCMinecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.resources.I18n
import net.minecraft.server.MinecraftServer
import net.minecraft.server.integrated.IntegratedServer
import java.awt.Color
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

//#if MC>=11400
//$$ import gg.essential.mixins.impl.util.math.Matrix3fExt
//$$ import gg.essential.mixins.impl.util.math.Matrix4fExt
//$$ import gg.essential.universal.UMinecraft.getMinecraft
//$$ import gg.essential.util.textTranslatable
//$$ import net.minecraft.util.math.vector.Matrix3f
//$$ import net.minecraft.util.math.vector.Matrix4f
//$$ import net.minecraft.world.storage.FolderName
//$$ import kotlin.io.path.div
//$$ import dev.folomeev.kotgl.matrix.matrices.Mat4
//$$ import dev.folomeev.kotgl.matrix.matrices.Mat3
//#endif

//#if MC==11903
//$$ import net.minecraft.client.gui.Element
//$$ import net.minecraft.client.gui.ParentElement
//#endif

val JsonElement?.optBoolean get() = if (this is JsonPrimitive && isBoolean) asBoolean else null

operator fun Color.component1() = this.red
operator fun Color.component2() = this.green
operator fun Color.component3() = this.blue
operator fun Color.component4() = this.alpha


fun ServerDiscovery.toServerData(knownServers: Map<String, ServerData> = emptyMap()) =
    knownServers[addresses[0]]
        ?: ServerData(
            getDisplayName("en_us") ?: addresses[0],
            addresses[0],
            //#if MC>=12002
            //$$ ServerInfo.ServerType.OTHER,
            //#else
            false,
            //#endif
        ).apply {
            ext.isTrusted = false
            resourceMode = ServerData.ServerResourceMode.ENABLED
        }

fun KnownServer.toServerData(knownServers: Map<String, ServerData> = emptyMap()) =
    knownServers[addresses[0]]
        ?: ServerData(
            names["en_us"] ?: addresses[0],
            addresses[0],
            //#if MC>=12002
            //$$ ServerInfo.ServerType.OTHER,
            //#else
            false,
            //#endif
        ).apply {
            ext.isTrusted = false
            resourceMode = ServerData.ServerResourceMode.ENABLED
        }

val <T> Lazy<T>.orNull: T? get() = if (isInitialized()) value else null

fun <T> List<CompletableFuture<T>>.merge(): CompletableFuture<List<T>> =
    CompletableFuture.allOf(*toTypedArray()).thenApply { map { it.join() } }

val MCMinecraft.executor get() = ext.executor

val MinecraftServer.executor
    get() =
    //#if MC>=11400
    //$$ this
        //#else
        Executor { addScheduledTask(it) }
//#endif

fun toggleElementaDebug() {
    elementaDebug = !elementaDebug
}

// FIXME preprocessor bug: Workaround for https://github.com/ReplayMod/remap/issues/12
var ServerData.serverResourcePack
    get() = resourceMode
    set(value) {
        resourceMode = value
    }

fun MCMinecraft.setSession(session: USession) {
    (this as MinecraftExt).setSession(session.toMC())
}

fun GuiScreen.findButtonByLabel(vararg label: String): () -> GuiButton? = {
    //#if MC==11903
    //$$ // 1.19.3 uses a unique system for grids
    //$$ fun findIn(elem: Element): ButtonWidget? {
    //$$     if (elem is ButtonWidget && label.any { elem.message.string.trim() == textTranslatable(it).string } ) {
    //$$         return elem
    //$$     }
    //$$     return (elem as? ParentElement)?.children()?.firstNotNullOfOrNull { findIn(it) }
    //$$ }
    //$$ findIn(this)
    //#elseif MC>=11600
    //$$ (this as GuiScreenAccessor).`essential$getChildren`()
    //$$ .filterIsInstance<Button>().lastOrNull { button -> label.any { button.message.string.trim() == textTranslatable(it).string } }
    //#else
    (this as GuiScreenAccessor).buttonList.lastOrNull { button -> label.any { button.displayString.trim() == I18n.format(it) } }
    //#endif
}

fun UImage.Companion.read(stream: InputStream): UImage {
    //#if MC>=11400
    //$$ return UImage(net.minecraft.client.renderer.texture.NativeImage.read(stream))
    //#else
    return UImage(javax.imageio.ImageIO.read(stream))
    //#endif
}

fun UImage.Companion.read(bytes: ByteArray) = read(bytes.inputStream())

fun UMatrixStack.toCommon() =
    gg.essential.model.util.UMatrixStack(
        //#if MC>=11400
        //$$ peek().model.kotgl,
        //$$ peek().normal.kotgl,
        //#else
        // Note: lwjgl2 uses column-row naming, unlike kotgl which uses row-column naming
        with(peek().model) {
            mat4(
                m00, m10, m20, m30,
                m01, m11, m21, m31,
                m02, m12, m22, m32,
                m03, m13, m23, m33,
            )
        },
        with(peek().normal) {
            mat3(
                m00, m10, m20,
                m01, m11, m21,
                m02, m12, m22,
            )
        },
        //#endif
    )

fun gg.essential.model.util.UMatrixStack.toUC() =
    UMatrixStack().apply { set(this@toUC) }

fun UMatrixStack.set(value: gg.essential.model.util.UMatrixStack) =
    value.run {
        val uc = this@set
        //#if MC>=11400
        //$$ uc.peek().model.kotgl = peek().model
        //$$ uc.peek().normal.kotgl = peek().normal
        //#else
        // Note: lwjgl2 uses column-row naming, unlike kotgl which uses row-column naming
        with(peek().model) {
            val m = uc.peek().model
            m.m00 = m00
            m.m10 = m01
            m.m20 = m02
            m.m30 = m03
            m.m01 = m10
            m.m11 = m11
            m.m21 = m12
            m.m31 = m13
            m.m02 = m20
            m.m12 = m21
            m.m22 = m22
            m.m32 = m23
            m.m03 = m30
            m.m13 = m31
            m.m23 = m32
            m.m33 = m33
        }
        with(peek().normal) {
            val m = uc.peek().normal
            m.m00 = m00
            m.m10 = m01
            m.m20 = m02
            m.m01 = m10
            m.m11 = m11
            m.m21 = m12
            m.m02 = m20
            m.m12 = m21
            m.m22 = m22
        }
        //#endif
    }

//#if MC>=11903
//$$ // Note: JOML is column-major and kotgl is row-major
//$$ var Matrix4f.kotgl: Mat4
//$$     get() =
//$$         mat4(
//$$             m00(), m10(), m20(), m30(),
//$$             m01(), m11(), m21(), m31(),
//$$             m02(), m12(), m22(), m32(),
//$$             m03(), m13(), m23(), m33(),
//$$         )
//$$     set(m) {
//$$         set(
//$$             m.m00, m.m10, m.m20, m.m30,
//$$             m.m01, m.m11, m.m21, m.m31,
//$$             m.m02, m.m12, m.m22, m.m32,
//$$             m.m03, m.m13, m.m23, m.m33,
//$$         )
//$$     }
//$$
//$$ var Matrix3f.kotgl: Mat3
//$$     get() =
//$$         mat3(
//$$             m00(), m10(), m20(),
//$$             m01(), m11(), m21(),
//$$             m02(), m12(), m22(),
//$$         )
//$$     set(m) {
//$$         set(
//$$             m.m00, m.m10, m.m20,
//$$             m.m01, m.m11, m.m21,
//$$             m.m02, m.m12, m.m22,
//$$         )
//$$     }
//#elseif MC>=11400
//$$ var Matrix4f.kotgl: Mat4
//$$     get() = (this as Matrix4fExt).kotgl
//$$     set(m) {
//$$         (this as Matrix4fExt).kotgl = m
//$$     }
//$$
//$$ var Matrix3f.kotgl: Mat3
//$$     get() = (this as Matrix3fExt).kotgl
//$$     set(m) {
//$$         (this as Matrix3fExt).kotgl = m
//$$     }
//#endif

val IntegratedServer.worldDirectory: Path
    get() =
        //#if MC>=11602
        //$$ this.func_240776_a_(FolderName.DOT)
        //#else
        this.getWorld(0 )!!.saveHandler.worldDirectory.toPath()
        //#endif

val GuiScreen?.isMainMenu: Boolean
    get() = if (this != null) {
        this is GuiMainMenu || this is LabyModMainMenu || this.isCMMMainMenu
    } else {
        false
    }

// Kotlin's `.let { ... }` but for Java
// Wrapped in an object to not accidentally shadow the original.
object Let {
    @JvmStatic
    fun <T, R> let(obj: T, block: (T) -> R): R = block(obj)
}
