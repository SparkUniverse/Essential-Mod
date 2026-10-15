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
package gg.essential.cosmetics

import gg.essential.config.EssentialConfig
import gg.essential.gui.common.CosmeticHoverOutlineHook
import gg.essential.mixins.impl.client.gui.GuiInventoryExt
import gg.essential.model.EnumPart
import gg.essential.model.backend.RenderBackend
import gg.essential.model.backend.minecraft.MinecraftRenderBackend
import gg.essential.model.backend.minecraft.toPose
import gg.essential.model.light.Light
import gg.essential.universal.UMatrixStack
import gg.essential.util.toCommon
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.client.renderer.entity.layers.LayerRenderer

//#if MC >= 1.21.9
//$$ import net.minecraft.client.render.command.OrderedRenderCommandQueue
//#endif

//#if MC >= 1.21.2
//$$ import gg.essential.mixins.impl.client.model.PlayerEntityRenderStateExt
//$$ import net.minecraft.client.render.entity.state.PlayerEntityRenderState
//#endif

//#if MC >= 1.14
//$$ import com.mojang.blaze3d.matrix.MatrixStack
//#if MC < 1.21.9
//$$ import net.minecraft.client.renderer.IRenderTypeBuffer
//#endif
//$$ import net.minecraft.client.renderer.entity.model.PlayerModel
//#else
import gg.essential.universal.UGraphics
import gg.essential.model.backend.minecraft.getRelativeCameraPosFromGlState
import net.minecraft.client.renderer.OpenGlHelper
//#endif

class EssentialModelRenderer(
    //#if MC >= 1.21.9
    //$$ private val playerRenderer: PlayerEntityRenderer<*>,
    //#else
    private val playerRenderer: RenderPlayer,
    //#endif
) :
    //#if MC >= 1.21.2
    //$$ FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel>(playerRenderer)
    //#elseif MC >= 1.14
    //$$ LayerRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>>(playerRenderer)
    //#else
    LayerRenderer<AbstractClientPlayer>
    //#endif
{
    fun render(
        matrixStack: UMatrixStack,
        //#if MC >= 1.21.9
        //$$ queue: RenderBackend.CommandQueue,
        //$$ playerState: PlayerEntityRenderState?, // may be null when angles are already applied or not required
        //#else
        vertexConsumerProvider: RenderBackend.VertexConsumerProvider,
        playerState: Object?, // always null, exists for symmetry with 1.21.9+
        //#endif
        cState: CosmeticsRenderState,
        lightInt: Int,
        parts: Set<EnumPart> = EnumPart.entries.toSet(),
        setsPose: Boolean = false,
        needsRootTransform: Boolean = true,
    ) {
        val wearablesManager = cState.wearablesManager() ?: return
        if (wearablesManager.models.isEmpty()) return

        //#if MC >= 1.21.9
        //$$ if (playerState != null) {
        //$$     playerRenderer.model.setAngles(playerState)
        //$$ }
        //#endif
        val pose = playerRenderer.toPose(
            wearablesManager.state.usesCapePose,
            wearablesManager.state.usesElytraPose,
        )
        val skin = MinecraftRenderBackend.SkinTexture(cState.skinTexture())
        val light = Light(lightInt.toUInt())

        matrixStack.push()

        //#if MC < 1.14
        // Reposition our stack such that the camera is at 0/0/0, this is important for translucent geometry because
        // those are sorted relative to 0/0/0.
        // Modern versions have two separate stack and the passed one already fulfills this requirement, older versions
        // however don't, so we need to create this split artificially. Luckily our renderer already uses an explicit
        // matrix stack, so this is as simple as offsetting that in one direction and the global stack in the other to
        // balance it out.
        val relativeCamera = getRelativeCameraPosFromGlState()
        matrixStack.translate(-relativeCamera.x, -relativeCamera.y, -relativeCamera.z)
        UGraphics.GL.pushMatrix()
        UGraphics.GL.translate(relativeCamera.x, relativeCamera.y, relativeCamera.z)
        //#endif

        // With 1.21.2 now properly rendering all body parts relative to the root part, some third party mods might now
        // apply transformations to the root part, we need to account for that
        //#if MC >= 1.21.2
        //$$ if (needsRootTransform) {
        //$$     val root = playerRenderer.getModel().getRootPart()
        //$$     if (root.pivotX != 0.0F || root.pivotY != 0.0F || root.pivotZ != 0.0F) {
        //$$         matrixStack.translate(root.pivotX / 16.0F, root.pivotY / 16.0F, root.pivotZ / 16.0F)
        //$$     }
        //$$     if (root.pitch != 0.0F || root.yaw != 0.0F || root.roll != 0.0F) {
        //$$         matrixStack.multiply(org.joml.Quaternionf().rotationZYX(root.roll, root.yaw, root.pitch))
        //$$     }
        //$$     if (root.xScale != 1.0F || root.yScale != 1.0F || root.zScale != 1.0F) {
        //$$         matrixStack.scale(root.xScale, root.yScale, root.zScale)
        //$$     }
        //$$ }
        //#endif

        // MC renders with y = 0 at the head, we have it at the feet
        // (un-does the 1.5 part of the 1.501 in RenderLivingBase.prepareScale)
        matrixStack.translate(0.0F, 1.5f, 0.0F)

        //#if MC < 1.17
        GlStateManager.enableRescaleNormal()
        //#endif

        //#if MC >= 1.21.9
        //$$ wearablesManager.render(matrixStack.toCommon(), { queue }, light, pose, skin, parts)
        //#else
        val queues = mutableMapOf<CosmeticId, MinecraftRenderBackend.CommandQueue>()
        val queueProvider = WearablesManager.CommandQueueProvider { cosmetic ->
            queues.getOrPut(cosmetic) { MinecraftRenderBackend.CommandQueue() }
        }
        wearablesManager.render(matrixStack.toCommon(), queueProvider, light, pose, skin, parts)
        //#endif

        //#if MC >= 1.21.9
        //$$ // Hover outline with vanilla renderer is no longer supported, UI3DPlayer.FallbackPlayer is always used
        //#else
        CosmeticHoverOutlineHook.active?.renderCosmeticsForOutlines(queues)

        val combinedQueue = MinecraftRenderBackend.CommandQueue()
        queues.values.forEach { it.copyTo(combinedQueue) }
        combinedQueue.render(vertexConsumerProvider)
        //#endif

        //#if MC < 1.17
        vertexConsumerProvider.flush()
        GlStateManager.disableRescaleNormal();
        //#endif

        matrixStack.pop();
        //#if MC < 1.14
        UGraphics.GL.popMatrix();
        //#endif

        if (setsPose) cState.setRenderedPose(pose);
    }


    //#if MC >= 1.21.9
    //$$ override fun render(matrices: MatrixStack, queue: OrderedRenderCommandQueue, light: Int, state: PlayerEntityRenderState, limbAngle: Float, limbDistance: Float) {
    //$$     val cState = (state as PlayerEntityRenderStateExt).`essential$getCosmetics`()
    //$$     val uMatrixStack = UMatrixStack(matrices)
    //$$     val uQueue = MinecraftRenderBackend.MinecraftCommandQueue(queue)
    //$$     render(uMatrixStack, uQueue, state, cState, light, setsPose = true)
    //$$ }
    //#else
    //#if MC >= 1.14
    //#if MC >= 1.21.2
    //$$ override fun render(vMatrixStack: MatrixStack, buffer: VertexConsumerProvider, light: Int, state: PlayerEntityRenderState, limbAngle: Float, limbDistance: Float) {
    //$$     val cState = (state as PlayerEntityRenderStateExt).`essential$getCosmetics`()
    //#else
    //$$ override fun render(vMatrixStack: MatrixStack, buffer: IRenderTypeBuffer, light: Int, player: AbstractClientPlayerEntity, limbSwing: Float, limbSwingAmount: Float, partialTicks: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float) {
    //$$     val cState = CosmeticsRenderState.Live(player)
    //#endif
    //$$     val matrixStack = UMatrixStack(vMatrixStack)
    //$$     val vertexConsumerProvider = MinecraftRenderBackend.VertexConsumerProvider(buffer)
    //#else
    override fun doRenderLayer(player: AbstractClientPlayer, limbSwing: Float, limbSwingAmount: Float, partialTicks: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float, scale: Float) {
        val matrixStack = UMatrixStack()
        val vertexConsumerProvider = MinecraftRenderBackend.VertexConsumerProvider()
        val cState = CosmeticsRenderState.Live(player)
        val light = OpenGlHelper.lastBrightnessX.toInt() or OpenGlHelper.lastBrightnessY.toInt().shl(16);
        if (cState.isSneaking && !playerRenderer.mainModel.isChild) {
            matrixStack.translate(0.0F, 0.2F, 0.0F) // from ModelPlayer.render
        }
    //#endif
        render(matrixStack, vertexConsumerProvider, null, cState, light, setsPose = true)
    }
    //#endif

    //#if MC < 11400
    override fun shouldCombineTextures(): Boolean = true
    //#endif

    companion object {
        @JvmStatic
        fun shouldRender(player: AbstractClientPlayer): Boolean {
            if (GuiInventoryExt.isInventoryEntityRendering.getUntracked()
                && EssentialConfig.disableCosmeticsInInventory) {
                return false
            }

            return !player.isInvisible && !player.isSpectator
        }

    }
}
