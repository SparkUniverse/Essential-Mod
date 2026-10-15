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
package gg.essential.gui.common

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import dev.folomeev.kotgl.matrix.vectors.mutables.minus
import dev.folomeev.kotgl.matrix.vectors.mutables.plus
import dev.folomeev.kotgl.matrix.vectors.vec3
import dev.folomeev.kotgl.matrix.vectors.vecUnitX
import dev.folomeev.kotgl.matrix.vectors.vecUnitY
import dev.folomeev.kotgl.matrix.vectors.vecUnitZ
import dev.folomeev.kotgl.matrix.vectors.vecZero
import gg.essential.Essential
import gg.essential.api.profile.WrappedGameProfile
import gg.essential.api.profile.wrapped
import gg.essential.cosmetics.CosmeticId
import gg.essential.cosmetics.CosmeticsState
import gg.essential.cosmetics.EquippedCosmetic
import gg.essential.cosmetics.WearablesManager
import gg.essential.cosmetics.events.CosmeticEventDispatcher.dispatchEvents
import gg.essential.cosmetics.renderCosmeticsForOutlines
import gg.essential.cosmetics.skinmask.MaskedSkinProvider
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.handlers.EssentialSoundManager
import gg.essential.handlers.GameProfileManager
import gg.essential.gui.elementa.state.v2.State as StateV2
import gg.essential.mixins.ext.client.ParticleSystemHolder
import gg.essential.mixins.impl.client.entity.AbstractClientPlayerExt
import gg.essential.mod.Model
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.preview.PerspectiveCamera
import gg.essential.mod.player.AbstractPlayerRenderer
import gg.essential.model.BedrockModel
import gg.essential.model.ModelAnimationState
import gg.essential.model.ParticleSystem
import gg.essential.model.PlayerMolangQuery
import gg.essential.model.backend.PlayerPose
import gg.essential.model.backend.RenderBackend
import gg.essential.model.backend.minecraft.MinecraftRenderBackend
import gg.essential.model.light.Light
import gg.essential.model.util.Quaternion
import gg.essential.model.util.rotateBy
import gg.essential.network.connectionmanager.cosmetics.AssetLoader
import gg.essential.model.util.UMatrixStack as CMatrixStack
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UResolution
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.util.Client
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.ModLoaderUtil
import gg.essential.util.identifier
import gg.essential.util.orNull
import gg.essential.util.withDefaultMcGuiRenderingSetup
import gg.essential.util.toUC
import gg.essential.vigilance.utils.onLeftClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.plus
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

//#if MC >= 26.3
//#else
import gg.essential.util.DrawFramebufferContext
//#endif

//#if MC >= 26.2
//$$ import net.minecraft.client.renderer.SubmitNodeStorage
//#endif

//#if MC>=12109
//$$ import net.minecraft.client.render.state.CameraRenderState
//#endif

//#if MC>=12106
//$$ import com.mojang.blaze3d.buffers.GpuBuffer
//$$ import com.mojang.blaze3d.buffers.Std140Builder
//$$ import org.lwjgl.system.MemoryStack
//#else
import gg.essential.util.ScissorState
//#endif

//#if MC>=12102
//$$ import com.mojang.blaze3d.systems.ProjectionType
//#endif

//#if MC>=12002
//$$ import org.joml.Quaternionf
//$$ import gg.essential.util.thenAcceptOnMainThread
//#endif

//#if MC>=12000
//$$ import com.mojang.blaze3d.systems.VertexSorter
//$$ import gg.essential.util.*
//$$ import net.minecraft.client.gui.DrawContext
//#endif

//#if MC>=11904
//$$ import net.minecraft.client.util.math.MatrixStack
//#endif

//#if MC == 1.16.5
//$$ import dev.folomeev.kotgl.matrix.matrices.mutables.set
//$$ import gg.essential.model.util.toMat4
//$$ import net.minecraft.client.renderer.GLAllocation
//#endif

//#if MC>=11903
//$$ import net.minecraft.util.math.RotationAxis
//#endif

//#if MC>=11400
//$$ import net.minecraft.client.renderer.ActiveRenderInfo
//$$ import net.minecraft.util.math.vector.Matrix4f
//$$ import net.minecraft.util.math.vector.Vector3f
//$$ import net.minecraft.util.math.vector.Vector4f
//#else
import org.lwjgl.util.vector.Matrix4f
import org.lwjgl.util.vector.Vector4f
//#endif

//#if MC==10809
//$$ import gg.essential.universal.shader.BlendState
//#endif

open class UI3DPlayer(
    val hideNameTags: State<Boolean>,
    val draggable: State<Boolean>,
    player: EntityPlayer?,
    val sounds: StateV2<Boolean> = stateOf(false),
    soundsVolume: StateV2<Float> = stateOf(1f),
    private val profile: State<WrappedGameProfile?> = BasicState(player?.gameProfile?.wrapped()),
) : UIPlayer() {

    private val closed = mutableStateOf(false)
    val soundsVolume: StateV2<Float> = StateV2 { if (closed()) 0f else soundsVolume() }

    var player: EntityPlayer? = player
        set(value) {
            field = value
            cosmeticsSource = cosmeticsSource // apply cosmetics source to new player
        }

    var cosmeticsSource: StateV2<Map<CosmeticSlot, EquippedCosmetic>>? = null
        set(value) {
            field = value
            if (value != null) {
                (player as? AbstractClientPlayerExt)?.cosmeticsSource = value
            }
        }

    override val wearablesManager: WearablesManager?
        get() = fallbackPlayer.orNull?.wearablesManager
            ?: (player as? AbstractClientPlayerExt)?.wearablesManager

    /**
     * When set, the given skin texture will be used in place of the one specified in the [profile].
     *
     * Note that if skin masking is required, the texture object at the given resource location should implement
     * [gg.essential.mixins.ext.client.renderer.PlayerSkinTextureExt], otherwise no masking will be applied.
     *
     * Forces use of standalone renderer.
     */
    var skinTextureOverride: ResourceLocation? = null
        set(texture) {
            errored = true
            field = texture
        }

    private var dragging = false
    private var rotationAngleHorizontal = 30f
    private var rotationAngleVerticalFront = -10f
    private var rotationAngleVerticalSide = 0f
    private val rotationAngleCamera: PerspectiveCamera
        get() {
            val target = vec3(0f, PLAYER_MODEL_HEIGHT / 2, 0f)
            val rotHorizontal = Quaternion.fromAxisAngle(vecUnitY(), (180 - rotationAngleHorizontal) / 180f * PI.toFloat())
            val rotVertSide = Quaternion.fromAxisAngle(vecUnitZ(), rotationAngleVerticalSide / 180f * PI.toFloat())
            val rotVertFront = Quaternion.fromAxisAngle(vecUnitX(), rotationAngleVerticalFront / 180f * PI.toFloat())
            val cameraOffset = rotHorizontal * rotVertSide * rotVertFront * vec3(0f, 0f, ORTHOGRAPHIC_CAMERA_DISTANCE)
            return PerspectiveCamera(target.plus(cameraOffset), target, 0f)
        }

    private var prevX = -1f
    private var prevY = -1f

    var partialTicks = 0f

    /**
     * If set, this UI3DPlayer ignores its internal rotation and instead positions the camera strictly based on this
     * value.
     * The player is standing on 0/0/0 looking towards negative Z.
     */
    var perspectiveCamera: PerspectiveCamera? = null

    val camera: PerspectiveCamera
        get() = perspectiveCamera ?: rotationAngleCamera

    protected var errored = false
    init {
        if (ModLoaderUtil.isModLoaded("figura")) {
            // probably a hopeless case, they inject-cancel the entire InventoryScreen.drawEntity method
            errored = true
        }
    }

    private val fallbackPlayer = lazy { FallbackPlayer() }
    private var fallbackErrored = false

    init {
        setWidth((100 * playerWidth).pixels())
        setHeight((100 * playerHeight).pixels())

        onLeftClick { event ->
            if (draggable.get()) {
                dragging = true
                prevX = event.relativeX
                prevY = event.relativeY
            }
        }
        onMouseDrag { mouseX, mouseY, mouseButton -> doDrag(mouseX, mouseY, mouseButton) }
        onMouseRelease {
            if (draggable.get()) {
                prevX = -1f
                prevY = -1f
                dragging = false
            }
        }
    }

    fun setRotations(pitch: Float, yaw: Float) {
        this.rotationAngleHorizontal = yaw
        this.rotationAngleVerticalFront = pitch
    }

    private fun doDrag(mouseX: Float, mouseY: Float, mouseButton: Int) {
        if (!dragging || !draggable.get())
            return

        val dX = (mouseX - prevX) / 1.5f
        val dY = (mouseY - prevY) / 1.5f

        val sidePercentage = 0// if (normalizedHorizontal >= 1) 2 - normalizedHorizontal else normalizedHorizontal
        val frontPercentage = 1 - sidePercentage

        rotationAngleHorizontal += dX
        rotationAngleVerticalFront += -(dY * frontPercentage)
        rotationAngleVerticalFront = rotationAngleVerticalFront.coerceIn(-30f, 30f)

        prevX = mouseX
        prevY = mouseY
    }

    override fun close() {
        closed.set(true)

        fallbackPlayer.orNull?.close()
    }

    init {
        // FIXME We currently put up a loading spinner and hide this component via a scissor effect while cosmetics are
        //  loading (see [CosmeticPreview]). That however means [extract] won't be called.
        //  And we currently update the cosmetics only in the middle of our `draw`, which then won't happen, so the
        //  spinner will never finish.
        //  Ideally we should be separating this update code from the actual rendering; but with particle positions
        //  potentially depending on the rendered entity pose, this is non-trivial.
        //  Hence this workaround for now.
        addUpdateFunc { _, _ -> if (errored) fallbackPlayer.value.checkForUpdates() }
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        val window = Window.of(this)
        // While the player itself is always within the bounds of this component, cosmetics and particles are not,
        // so we'll size the component to be the entire screen, and let the scissor reduce it where possible.
        extractor.special(
            0,
            0,
            (window.getWidth() * extractor.guiScale).roundToInt(),
            (window.getHeight() * extractor.guiScale).roundToInt(),
            factory = UI3DPlayerSpecialRendererFactory,
            args = extractRenderState(),
        )
    }

    override fun extractRenderState(): UI3DPlayerRenderState {
        return UI3DPlayerRenderState(this)
    }

    //#if MC < 26.3
    private val mc12106ScissorHandler = Mc12106ScissorHandler()

    // Note: This has to be kept around because this component is exposed via our API.
    @Deprecated(
        "`draw`-style rendering is deprecated. Override `extractComponent` instead. Call `extract` to extract this component, its effects, and its children.",
        replaceWith = ReplaceWith("extract(extractor)")
    )
    override fun draw(matrixStack: UMatrixStack) {
        @Suppress("DEPRECATION")
        beforeDraw(matrixStack)

        mc12106ScissorHandler.beforeDraw(matrixStack)

        val camera = perspectiveCamera
        if (camera != null) {
            clearDepthTexture()

            drawWithPerspectiveProjection(matrixStack.fork(), camera, mc12106ScissorHandler.viewportWidth, mc12106ScissorHandler.viewportHeight)

            //#if MC >= 1.20 && FORGE
            //$$ // EM-2272: Forge messes with MC's orthographic projection, causing some depth values to be out of the expected range.
            //$$ // https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/patches/minecraft/net/minecraft/client/renderer/GameRenderer.java.patch#L36-L43
            //$$ GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
            //#endif
        } else {
            val stack = matrixStack.fork()

            if (platform.usesReversedZ) {
                clearDepthTexture()
                if (!platform.irisReversesZ) {
                    stack.scale(1f, 1f, -1f)
                }
            }

            drawWithOrthographicProjection(stack)
        }

        mc12106ScissorHandler.afterDraw(matrixStack)

        @Suppress("DEPRECATION")
        super.draw(matrixStack)
    }
    //#endif

    internal fun drawWithOrthographicProjection(stack: UMatrixStack) {
        // Center player within component
        stack.translate(getLeft() + getWidth() / 2, getTop() + getHeight() / 2, 450f)

        // Limit player size such that it'll always fit within our bounds (with a small bit of padding for style)
        val playerHeightInPixels = min(getWidth() * 2, getHeight()) * (1 - PADDING_FACTOR)

        // Scale from screen space where the player is 200px high and the y axis points down
        // to world space where they are 2m high and the y axis points up
        val scale = playerHeightInPixels / PLAYER_MODEL_HEIGHT
        stack.scale(scale, -scale, scale)

        // With orthographic projection, we place the camera very far away, see [ORTHOGRAPHIC_CAMERA_DISTANCE].
        // This will however cause issues with the near/far plane because the resulting geometry will lie far
        // outside those bounds. To counteract this, we offset the global state in the opposite direction.
        // That way, the vertices that end up in the BufferBuilder (where MC will sort them) are still relative
        // to the really distant camera (and therefore sorted correctly), but once OpenGL combines them with the
        // global ModelViewMatrix, they'll end up close to zero / inside the near-far plane bounds again.
        stack.translate(0f, 0f, ORTHOGRAPHIC_CAMERA_DISTANCE)

        // Need this for normals to behave with orthographic projection
        //#if MC<11700
        GlStateManager.enableRescaleNormal()
        //#endif

        stack.runWithGlobalState {
            drawPlayer()
        }

        //#if MC<11700
        GlStateManager.disableRescaleNormal()
        //#endif
    }

    //#if MC < 26.3
    private fun clearDepthTexture() {
        // Perspective depth values are incredibly close to 1 (while orthographic are about [0.2; 0.5]),
        // so they will naturally always end up behind anything which was already rendered there and therefore won't be
        // visible if we do not clear the depth buffer first.
        // As of 26.2, MC uses reversed Z (while Elementa still uses regular Z), so even with orthographic rendering
        // we'd fail the depth test if we don't clear the depth buffer first.
        //#if MC>=12105
        //#if MC >= 26.2
        //$$ val mcDepthTexture = Minecraft.getInstance().gameRenderer.mainRenderTarget().depthTexture
        //#else
        //$$ val mcDepthTexture = MinecraftClient.getInstance().framebuffer.depthAttachment
        //#endif
        //#if MC>=12106
        //$$ val depthTexture = RenderSystem.outputDepthTextureOverride?.texture() ?: mcDepthTexture
        //#else
        //$$ val depthTexture = mcDepthTexture
        //#endif
        //$$ if (depthTexture != null) {
            //#if MC >= 26.2
            //$$ val clearDepth = 0.0
            //#else
            //$$ val clearDepth = 1.0
            //#endif
        //$$     RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, clearDepth)
        //$$ }
        //#else
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
        //#endif
    }
    //#endif

    internal fun drawWithPerspectiveProjection(stack: UMatrixStack, camera: PerspectiveCamera, viewportWidth: Int, viewportHeight: Int) {
        val (left, top) = stack.transform(getLeft(), getTop())
        val (right, bottom) = stack.transform(getRight(), getBottom())
        val middleX = (left + right) / 2
        val middleY = (top + bottom) / 2
        val width = right - left
        val height = bottom - top

        val windowWidth = viewportWidth / UResolution.scaleFactor.toFloat()
        val windowHeight = viewportHeight / UResolution.scaleFactor.toFloat()
        val scaleX = width / windowWidth
        val scaleY = height / windowHeight

        // Compute our projection matrix (perspective projection but scaled and offset to fit into this component)
        val projectionMatrix = UMatrixStack()
        projectionMatrix.translate(
            -1f + (middleX / windowWidth) * 2,
            1f - (middleY / windowHeight) * 2,
            0f,
        )
        projectionMatrix.scale(scaleX, scaleY, 1f)
        // For older versions, we use gluPerspective on the OpenGL stack itself
        //#if MC>=11903
        //$$ var zNear = 0.5f
        //$$ var zFar = 20f
        //$$ if (platform.usesReversedZ && !platform.irisReversesZ) {
        //$$     zNear = zFar.also { zFar = zNear }
        //$$ }
        //$$ projectionMatrix.peek().model.mul(Matrix4f().perspective(Math.toRadians(camera.fov.toDouble()).toFloat(), getWidth() / getHeight(), zNear, zFar, platform.isZZeroToOne))
        //#elseif MC>=11400
        //$$ projectionMatrix.peek().model.mul(Matrix4f.perspective(camera.fov.toDouble(), getWidth() / getHeight(), 0.5f, 20f))
        //#endif

        //#if MC>=12106
        //$$ val orgProjectionMatrixBuffer = RenderSystem.getProjectionMatrixBuffer()
        //$$ val orgProjectionType = RenderSystem.getProjectionType()
        //$$ val projectionMatrixBuffer = MemoryStack.stackPush().use { memoryStack ->
        //$$     RenderSystem.getDevice().createBuffer(
        //$$         { "Projection matrix UBO UI3DPlayer" },
        //$$         GpuBuffer.USAGE_UNIFORM,
        //$$         Std140Builder.onStack(memoryStack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE)
        //$$             .putMat4f(projectionMatrix.peek().model)
        //$$             .get(),
        //$$     )
        //$$ }.slice()
        //$$ RenderSystem.setProjectionMatrix(projectionMatrixBuffer, ProjectionType.PERSPECTIVE)
        //#elseif MC>=12102
        //$$ val orgProjectionMatrix = RenderSystem.getProjectionMatrix()
        //$$ val orgProjectionType = RenderSystem.getProjectionType()
        //$$ RenderSystem.setProjectionMatrix(projectionMatrix.peek().model, ProjectionType.PERSPECTIVE)
        //#elseif MC>=12000
        //$$ val orgProjectionMatrix = RenderSystem.getProjectionMatrix()
        //$$ val orgVertexSorting = RenderSystem.getVertexSorting()
        //$$ RenderSystem.setProjectionMatrix(projectionMatrix.peek().model, VertexSorter.BY_DISTANCE)
        //#elseif MC>=11700
        //$$ val orgProjectionMatrix = RenderSystem.getProjectionMatrix()
        //$$ RenderSystem.setProjectionMatrix(projectionMatrix.peek().model)
        //#else
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        projectionMatrix.replaceGlobalState()
        //#if MC<11400
        org.lwjgl.util.glu.Project.gluPerspective(camera.fov, getWidth() / getHeight(), 0.05f, 2000f)
        //#endif
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        //#endif

        isRenderingPerspective = true
        UMatrixStack().runReplacingGlobalState {
            drawPlayer()
        }
        isRenderingPerspective = false

        //#if MC>=12106
        //$$ @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // missing Nullable annotation
        //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrixBuffer, orgProjectionType)
        //$$ projectionMatrixBuffer.buffer.close()
        //#elseif MC>=12102
        //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrix, orgProjectionType);
        //#elseif MC>=12000
        //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrix, orgVertexSorting);
        //#elseif MC>=11700
        //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrix);
        //#else
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.popMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        //#endif
    }

    private fun UMatrixStack.transform(x: Float, y: Float): Pair<Float, Float> {
        val vec = Vector4f(x, y, 0f, 1f)
        //#if MC>=11903
        //$$ peek().model.transform(vec);
        //#elseif MC>=11400
        //$$ vec.transform(peek().model);
        //#else
        Matrix4f.transform(peek().model, vec, vec)
        //#endif
        return Pair(vec.x, vec.y)
    }

    protected open fun drawPlayer() {
        bindWhiteLightMapTexture()

        if (errored) {
            if (fallbackErrored) return
            try {
                doDrawFallbackPlayer()
                doDrawParticles(fallbackPlayer.value.particleSystem)
            } catch (e: Exception) {
                Essential.logger.error("Error rendering fallback player", e)
                fallbackErrored = true
            }
            return
        }

        try {
            current = this
            val player = player as? AbstractClientPlayer ?: return
            val playerExt = player as? AbstractClientPlayerExt ?: return

            playerExt.wearablesManager.update()
            playerExt.poseManager.update(playerExt.wearablesManager)

            doDrawPlayer()

            // We just drew the player, the rendered pose shouldn't ever be null (except if a third-party mod does some
            // really weird things), so the fallback doesn't really matter
            val renderedPose = playerExt.renderedPose ?: PlayerPose.neutral()

            playerExt.wearablesManager.updateLocators(renderedPose)
            dispatchEvents(player, playerExt.wearablesManager)

            // An emulated player has its own dedicated particle system which we need to manually update here (that is
            // after we have rendered the player, which updates any locators).
            val dedicatedParticleSystem = (player as? EmulatedUI3DPlayer.EmulatedPlayer)?.particleSystem
            dedicatedParticleSystem?.update()

            // The particle system is ordinarily rendered separately after all entities, so if we want it to show up
            // in the UI (and with the correct billboard facing), we need to render it manually here.
            val particleSystem = dedicatedParticleSystem ?: (player?.world as? ParticleSystemHolder)?.particleSystem
            if (particleSystem != null) {
                doDrawParticles(particleSystem)
            }
        } catch (e: Exception) {
            Essential.logger.error("Error rendering emulated player", e)
            errored = true
        } finally {
            current = null
        }
    }

    private fun doDrawPlayer() {
        val p = player ?: return

        val renderManager = Minecraft.getMinecraft().renderManager
        //#if MC >= 26.1
        //$$ // CameraRenderState set up later below
        //#elseif MC>=11400
        //#if MC>=12109
        //$$ val camera = object : Camera() {
        //#else
        //$$ renderManager.info = object : ActiveRenderInfo() {
        //#endif
        //$$     init {
        //$$         update(p.world, p, true, true, 0f)
        //$$         setDirection(rotationAngleHorizontal, rotationAngleVerticalFront * 0)
                //#if MC>=12100
                //$$ moveBy(4f, 0f, 0f)
                //#else
                //$$ movePosition(4.0, 0.0, 0.0)
                //#endif
        //$$     }
        //$$ }
        //#else
        renderManager.renderViewEntity = player
        //#endif

        UGraphics.directColor3f(1f, 1f, 1f)
        UGraphics.enableDepth()
        //#if MC==10809
        //$$ // RenderPlayer.doRender() (run via GuiInventory.drawEntityOnScreen() below) applies correct blending in 1.12.2+ but
        //$$ // not in 1.8.9, where they relied on the blend state already being set earlier in the draw, so we need to set it here
        //$$ val prevBlend = BlendState.active()
        //$$ if (prevBlend != BlendState.ALPHA) UGraphics.Globals.blendState(BlendState.ALPHA)
        //#endif

        //#if MC<12106
        val stack = CMatrixStack()
        // Undo parts of the flipping/rotating which GuiInventory.drawEntityOnScreen applies
        // (specifically the parts that apply to the global matrix stack)
        // (remainder is dealt with in [applyCamera])
        //#if MC>=11400
        //$$ stack.scale(1f, 1f, -1f)
        //#else
        stack.scale(1f, -1f, 1f)
        //#endif
        // Undo the Z offset from GuiInventory.drawEntityOnScreen
        stack.translate(0f, 0f, -50f)
        //#endif

        //#if MC>=12106
        //$$ run {
        //#elseif MC>=12000
        //$$ val context =
        //$$     MinecraftClient.getInstance().let { mc -> DrawContext(mc, mc.bufferBuilders.entityVertexConsumers) }
        //$$ context.matrices.multiplyPositionMatrix(stack.toUC().peek().model)
        //$$ run {
        //#else
        stack.toUC().runWithGlobalState {
        //#endif
            //#if MC>=12005
            //$$ val scale = 1f
            //#else
            val scale = 1
            //#endif
            //#if MC>=12002
            //$$ // As of 1.20.2, the simple vanilla method applies glScissor which we don't want (that's Elementa's job)
            //$$ // so we'll instead have to call the other overload and do all the save&restore ourselves.
            //$$ val orgPitch: Float = p.pitch
            //$$ val orgYaw: Float = p.yaw
            //$$ val orgBodyYaw: Float = p.bodyYaw
            //$$ val orgHeadYaw: Float = p.headYaw
            //$$ val orgPrevHeadYaw: Float = p.prevHeadYaw
            //$$ p.pitch = 0f
            //$$ p.yaw = 180f
            //$$ p.bodyYaw = 180f
            //$$ p.headYaw = 180f
            //$$ p.prevHeadYaw = 180f
            //$$
            //#if MC>=12106
            //$$ val dispatcher = MinecraftClient.getInstance().entityRenderDispatcher
            //$$ val renderer = dispatcher.getRenderer(p)
            //$$ val state = renderer.getAndUpdateRenderState(p, 1f)
            //#if MC>=12111
            //$$ // Hitboxes are now drawn by EntityHitboxDebugRenderer, independently from the entity renderer
            //#else
            //$$ state.hitbox = null
            //#endif
            //$$
            //$$ val restoreLighting = setupPlayerLight()
            //$$ val stack = applyCamera(dispatcher)
            //$$
            //#if MC>=12109
            //$$ state.light = MAX_LIGHT.value.toInt()
            //$$ state.shadowPieces.clear()
            //#if MC >= 26.2
            //$$ val entityRenderPass = Minecraft.getInstance().gameRenderer.featureRenderDispatcher()
            //$$ val submitNodeStorage = SubmitNodeStorage()
            //#else
            //$$ val entityRenderPass = MinecraftClient.getInstance().gameRenderer.entityRenderDispatcher
            //$$ val submitNodeStorage = entityRenderPass.queue
            //#endif
            //$$ val cameraState = CameraRenderState().also { cameraState ->
            //#if MC>=12111
            //$$     cameraState.initialized = true
            //$$     cameraState.orientation = with(this@UI3DPlayer.camera.rotation) { Quaternionf(x, y, z, w) }
            //#else
            //$$     // See GameRenderer.updateCameraState
            //$$     cameraState.initialized = camera.isReady
            //$$     cameraState.pos = camera.pos
            //$$     cameraState.blockPos = camera.blockPos
            //$$     cameraState.entityPos = camera.focusedEntity.getLerpedPos(camera.lastTickProgress)
            //$$     cameraState.orientation = Quaternionf(camera.rotation)
            //#endif
            //$$ }
            //$$ renderManager.render(state, cameraState, 0.0, 0.0, 0.0, stack.toMC(), submitNodeStorage)
            //#if MC >= 26.3
            //$$ entityRenderPass.prepareFrame(submitNodeStorage).use { frame ->
            //$$     RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            //$$         { "Cosmetics" },
            //$$         MinecraftRenderBackend.outputColorTexture!!,
            //$$         java.util.Optional.empty(),
            //$$         MinecraftRenderBackend.outputDepthTexture,
            //$$         java.util.OptionalDouble.empty(),
            //$$     ).use { renderPass ->
            //$$         RenderSystem.bindDefaultUniforms(renderPass);
            //$$         net.minecraft.client.renderer.feature.FeatureRenderDispatcher
            //$$             .renderAllFeatures(renderPass, frame);
            //$$     }
            //$$ }
            //#elseif MC >= 26.2
            //$$ entityRenderPass.renderAllFeatures(submitNodeStorage)
            //#else
            //$$ entityRenderPass.render()
            //#endif
            //#else
            //$$ val vertexConsumers = MinecraftClient.getInstance().bufferBuilders.entityVertexConsumers
            //$$ dispatcher.setRenderShadows(false)
            //$$ dispatcher.render(state, 0.0, 0.0, 0.0, stack.toMC(), vertexConsumers, MAX_LIGHT.value.toInt())
            //$$ dispatcher.setRenderShadows(true)
            //$$ vertexConsumers.draw()
            //#endif
            //$$
            //$$ restoreLighting()
            //#else
            //$$ val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
            //$$ InventoryScreen.drawEntity(context, 0f, 0f, scale, Vector3f(), rotation, Quaternionf(), p)
            //#endif
            //$$
            //$$ p.pitch = orgPitch
            //$$ p.yaw = orgYaw
            //$$ p.bodyYaw = orgBodyYaw
            //$$ p.headYaw = orgHeadYaw
            //$$ p.prevHeadYaw = orgPrevHeadYaw
            //#else
            GuiInventory.drawEntityOnScreen(
                //#if MC>=12000
                //$$ context,
                //#elseif MC>=11904
                //$$ MatrixStack(),
                //#endif
                0, 0, scale, 0f, 0f, p
            )
            //#endif
        }

        UGraphics.depthFunc(GL11.GL_LEQUAL)
        UGraphics.color4f(1.0f, 1.0f, 1.0f, 1.0f)
        UGraphics.disableDepth()
        //#if MC==10809
        //$$ if (prevBlend != BlendState.ALPHA) UGraphics.Globals.blendState(prevBlend) // restore previous blend state
        //#endif
    }

    fun applyCamera(renderManager: RenderManager): UMatrixStack {
        //#if MC>=12106
        //$$ // Handled near call site in `doDrawPlayer`
        //#else
        setupPlayerLight()
        //#endif

        //#if MC>=12109
        //$$ // Camera state is passed directly to entity renderer in `doDrawPlayer`
        //#elseif MC>=11400
        //$$ renderManager.cameraOrientation = renderManager.info.rotation
        //#else
        renderManager.playerViewX = -rotationAngleVerticalFront
        renderManager.playerViewY = rotationAngleHorizontal - 180
        //#endif

        val stack = CMatrixStack()

        // Undo remainder of the rotating which GuiInventory.drawEntityOnScreen applies
        //#if MC>=11400 && MC<12106
        //$$ stack.scale(-1f, -1f, 1f)
        //#endif

        // Apply our camera
        stack.multiply(camera.createModelViewMatrix())

        //#if MC<11400
        // GuiInventory.drawEntityOnScreen uses entity yaw of 0, which means pointing towards positive z, we want it
        // pointing towards negative z though, so rotate by 180
        stack.scale(-1f, 1f, -1f)
        //#endif

        return stack.toUC()
    }

    private fun setupPlayerLight(): () -> Unit {
        val stack = CMatrixStack()

        //#if MC == 1.16.5
        //$$ // 1.16 is a weird mix of legacy gl stack and explicit pojo stack
        //$$ // The lighting setup method already ignores the gl stack but the renderer doesn't yet take the pojo stack
        //$$ // stack as a separate input, so one actually has to combine the two stacks when setting lighting for it
        //$$ // to function properly.
        //$$ // We can only pass a pojo stack to the method though, so we gotta convert the gl stack into one first.
        //$$ val buf = GLAllocation.createDirectFloatBuffer(16)
        //$$ GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, buf)
        //$$ val array = FloatArray(16).apply { buf.get(this) }
        //$$ stack.peek().model.set(array.toMat4())
        //#endif

        // Apply our camera
        stack.rotate(camera.rotation.invert())

        // Lighting as per GuiInventory
        stack.rotate(135f, 0f, 1f, 0f, degrees = true)

        //#if MC>=11400
        //$$ val matrix = stack.toUC().peek().model
        //#if MC>=12106
        //$$ val shaderLights = MemoryStack.stackPush().use { memoryStack ->
        //$$     RenderSystem.getDevice().createBuffer(
        //$$         { "Lighting UBO" },
        //$$         GpuBuffer.USAGE_UNIFORM,
        //$$         Std140Builder.onStack(memoryStack, DiffuseLighting.UBO_SIZE)
        //$$             .putVec3(Vector3f(0.2f, 1.0f, -0.7f).normalize().mulDirection(matrix))
        //$$             .putVec3(Vector3f(-0.2f, 1.0f, 0.7f).normalize().mulDirection(matrix))
        //$$             .get(),
        //$$     )
        //$$ }.slice()
        //$$ val orgShaderLights = RenderSystem.getShaderLights()
        //$$ RenderSystem.setShaderLights(shaderLights)
        //#elseif MC>=12005
        //$$ RenderSystem.setupLevelDiffuseLighting(
        //$$     Vector3f(0.2f, 1.0f, -0.7f).normalize().mulDirection(matrix),
        //$$     Vector3f(-0.2f, 1.0f, 0.7f).normalize().mulDirection(matrix)
        //$$ )
        //#elseif MC>=11700
        //$$ // FIXME preprocessor bug: this looks like it should be automatically mapped
        //$$ DiffuseLighting.disableForLevel(matrix)
        //#else
        //$$ RenderHelper.setupLevelDiffuseLighting(matrix)
        //#endif
        //#else
        stack.toUC().runWithGlobalState {
            RenderHelper.enableStandardItemLighting()
        }
        //#endif

        return {
            //#if MC>=12106
            //$$ @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // missing Nullable annotation
            //$$ RenderSystem.setShaderLights(orgShaderLights)
            //$$ shaderLights.buffer.close()
            //#elseif MC>=11400
            //$$ RenderHelper.setupGui3DDiffuseLighting()
            //#else
            RenderHelper.disableStandardItemLighting()
            //#endif
        }
    }

    private fun doDrawFallbackPlayer() {
        val restoreLighting = setupPlayerLight()

        val stack = camera.createModelViewMatrix()

        // See RenderLivingBase.prepareScale
        stack.scale(-1f, -1f, 1f)

        // See RenderPlayer.preRenderCallback
        stack.scale(0.9375f)

        val playerQueue = MinecraftRenderBackend.CommandQueue()
        val cosmeticQueues = mutableMapOf<CosmeticId, MinecraftRenderBackend.CommandQueue>()
        val cosmeticQueuesProvider = WearablesManager.CommandQueueProvider { cosmetic ->
            cosmeticQueues.getOrPut(cosmetic) { MinecraftRenderBackend.CommandQueue() }
        }

        fallbackPlayer.value.render(stack, playerQueue, cosmeticQueuesProvider)

        CosmeticHoverOutlineHook.active?.renderCosmeticsForOutlines(cosmeticQueues)

        val combinedQueue = playerQueue
        cosmeticQueues.values.forEach { it.copyTo(combinedQueue) }
        combinedQueue.renderImmediate()

        restoreLighting()
    }

    @OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
    private fun doDrawParticles(particleSystem: ParticleSystem) {
        val commandQueue = MinecraftRenderBackend.CommandQueue()

        val stack = camera.createModelViewMatrix()

        // The current stack has the player (and thereby implicitly also the world) oriented towards the camera
        // but the particle system expects absolute coordinates, so we need to offset the stack accordingly.
        val realRotation = player.takeUnless { errored }?.let { PlayerMolangQuery(it).entityRotation } ?: Quaternion.Identity
        stack.rotate(realRotation.invert())

        // The current stack has the player at the origin, but the player isn't really at the world origin,
        // so we need to offset the stack accordingly.
        val realPosition = player.takeUnless { errored }?.let { PlayerMolangQuery(it).entityPosition } ?: vecZero()
        stack.translate(vecZero().minus(realPosition))

        val camera = perspectiveCamera ?: rotationAngleCamera
        val cameraPos = camera.camera.rotateBy(realRotation).plus(realPosition)
        particleSystem.render(
            stack,
            cameraPos,
            realRotation * camera.rotation,
            commandQueue,
            Uuid.NIL,
            false,
            false,
            player.takeUnless { errored }?.uniqueID?.toKotlinUuid(), // try to only render this player's particles
            MAX_LIGHT,
        )

        bindWhiteLightMapTexture()

        commandQueue.renderImmediate()
    }

    // Older versions of MC do not always have a proper lightmap texture bound.
    // To always have one bound, we simply bind an all-white (we don't really care about any value other than `MAX_LIGHT`) texture ourselves.
    private fun bindWhiteLightMapTexture() {
        //#if MC>=12111
        //$$ // RenderLayer now always binds the lightmap texture of its configured RenderSetup
        //#else
        //#if MC>=11600
        //$$ val lightMapTextureUnit = 2
        //#else
        val lightMapTextureUnit = 1
        //#endif
        UGraphics.bindTexture(lightMapTextureUnit, identifier("essential", "textures/white.png"))
        //#endif
    }

    private inner class FallbackPlayer : AbstractPlayerRenderer(
        renderBackend = MinecraftRenderBackend,
        scope = CoroutineScope(SupervisorJob()) + Dispatchers.Client,
    ) {
        private val essential = Essential.getInstance()
        private val cosmeticsManager = essential.connectionManager.cosmeticsManager

        private val maskedSkinProvider = MaskedSkinProvider()

        private var currentProfileConfigured: WrappedGameProfile? = null
        private var currentProfile: GameProfile? = null // with overwrites
        private var currentSkin: ResourceLocation = DefaultPlayerSkin.getDefaultSkinLegacy()
        private var currentCape: ResourceLocation? = null

        private var liveCosmeticsSource: StateV2<Map<CosmeticSlot, EquippedCosmetic>>? = null
        private val cosmeticsSource: StateV2<Map<CosmeticSlot, EquippedCosmetic>>
            get() = this@UI3DPlayer.cosmeticsSource ?: liveCosmeticsSource ?: stateOf(emptyMap())

        private var thirdPartyCapeErrored = false

        val cosmeticsState: CosmeticsState
            get() = wearablesManager.state

        override val light: Light
            get() = MAX_LIGHT

        private fun loadTextures(profile: GameProfile) {
            //#if MC>=12002
            //$$ MinecraftClient.getInstance().skinProvider.fetchSkinTextures(profile).thenAcceptOnMainThread { skin ->
                //#if MC>=12104
                //$$ val skin = skin.get()
                //#endif
                //#if MC>=12109
                //$$ currentSkin = skin.body.texturePath()
                //$$ updateSkinType(Model.byTypeOrDefault(skin.model.asString()))
                //$$ currentCape = skin.cape?.texturePath()
                //#else
                //$$ currentSkin = skin.texture
                //$$ updateSkinType(Model.byTypeOrDefault(skin.model.getName()))
                //$$ currentCape = skin.capeTexture
                //#endif
            //$$ }
            //#else
            // Restore default (because we can't guaranteed that the texture callback will ever be called)
            currentSkin = DefaultPlayerSkin.getDefaultSkin(profile.id)
            updateSkinType(Model.byTypeOrDefault(DefaultPlayerSkin.getSkinType(profile.id)))
            currentCape = null

            Minecraft.getMinecraft().skinManager.loadProfileTextures(profile, { type, location, texture ->
                when (type) {
                    MinecraftProfileTexture.Type.SKIN -> {
                        currentSkin = location
                        updateSkinType(Model.byTypeOrDefault(texture.getMetadata("model") ?: ""))
                    }
                    MinecraftProfileTexture.Type.CAPE -> currentCape = location
                    else -> {}
                }
            }, true)
            //#endif
        }

        @OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
        fun checkForUpdates() {
            // Check if the profile configured for this UI3DPlayer has changed
            val profileConfigured = player?.gameProfile?.wrapped() ?: profile.get() ?: EmulatedUI3DPlayer.getLocalGameProfile()
            if (currentProfileConfigured != profileConfigured) {
                currentProfileConfigured = profileConfigured

                // original changed, need to re-compute the derived one
                currentProfile = null

                // UUID may have changed, need to update live cosmetic source accordingly
                liveCosmeticsSource = cosmeticsManager.infraEquippedOutfitsManager.getVisibleCosmeticsState(profileConfigured.id)

                entity.uuid = profileConfigured.id.toKotlinUuid()
            }

            // Check if there are any new overwrites for the current profile
            // (this will return null if the profile is fine as is)
            val skinOverride = cosmeticsManager.infraEquippedOutfitsManager.getSkin(profileConfigured.id)
            val newOverwrites =
                GameProfileManager.handleGameProfile(currentProfile ?: profileConfigured.profile, skinOverride)
            // We need to set a new profile if none is set, or if a new overwrite is available
            val newProfile =
                if (currentProfile == null) newOverwrites ?: profileConfigured.profile else newOverwrites

            if (newProfile != null) {
                currentProfile = newProfile

                // Profile changed, need to re-load its textures
                loadTextures(newProfile)
            }

            // Check if the equipped cosmetics or any of their settings have changed
            // FIXME should use State effect instead of checking every frame (though note that this field isn't a proper state yet!)
            updateCosmetics(cosmeticsSource.getUntracked())
        }

        override fun getCurrentSkin(): RenderBackend.Texture {
            val rawSkin = skinTextureOverride ?: currentSkin
            val skinLocation = maskedSkinProvider.provide(rawSkin, cosmeticsState.skinMask) ?: rawSkin
            return MinecraftRenderBackend.SkinTexture(skinLocation)
        }

        override fun getThirdPartyCape(): MinecraftRenderBackend.CapeTexture? {
            if (thirdPartyCapeErrored || CosmeticSlot.CAPE in cosmetics) {
                return null
            }
            try {
                val player = player as? AbstractClientPlayer ?: return null
                //#if MC>=12109
                //$$ val textureLocation = player.skin.cape()?.texturePath() ?: return null
                //#elseif MC>=12002
                //$$ val textureLocation = player.getSkinTextures().capeTexture ?: return null
                //#else
                val textureLocation = player.locationCape ?: return null
                //#endif
                return MinecraftRenderBackend.CapeTexture(textureLocation)
            } catch (e: Exception) {
                Essential.logger.error("Error rendering cape for fallback player", e)
                thirdPartyCapeErrored = true
                return null
            }
        }

        override fun playSound(event: ModelAnimationState.SoundEvent) {
            this@UI3DPlayer.playSound(event)
        }

        override fun loadModel(cosmetic: EquippedCosmetic, skinType: Model): Deferred<BedrockModel> {
            return cosmeticsManager.modelLoader
                .getModel(cosmetic.cosmetic, cosmetic.variant, skinType, AssetLoader.Priority.High)
                .asDeferred()
        }

        override fun beforeRender() {
            checkForUpdates()
        }

        init {
            addUpdateFunc { dt, _ -> update(dt) }
        }
    }

    protected fun playSound(event: ModelAnimationState.SoundEvent) {
        if (sounds.getUntracked()) {
            EssentialSoundManager.playSound(event, forceGlobal = true, volume = soundsVolume, enforceEmoteSoundSettings = false)
        }
    }

    companion object {
        private const val playerHeight = 1.8F
        private const val playerWidth = 0.6F

        private const val MAGIC_HEIGHT_SCALING_FACTOR = 0.525
        private const val PLAYER_MODEL_HEIGHT = 1 / MAGIC_HEIGHT_SCALING_FACTOR.toFloat()

        // We put the logical camera far away such that any facing-towards-camera logic (e.g. billboard particles,
        // transparency sorting, etc.) will behave close enough to correctly that we don't need to special case
        // orthographic projection in all of them.
        // Note: Some servers (CubeCraft and OriginRealms) have custom resource packs which override the entity core
        //       shader with one which treat certain vertices (those that use a 64 pixels wide texture, are rendered
        //       with orthographic projection, and have Z < -100) completely differently, to render custom geometry.
        //       This can corrupt our menu player if it happens to fit all these conditions, which it does when
        //       this camera distance is close to 100 or greater.
        //       As such, a value substantially below 100 is chosen, so none of our geometry ends up with z < -100.
        private const val ORTHOGRAPHIC_CAMERA_DISTANCE = 80f

        // Padding constant in terms of percentage of additional width and height.
        // This accounts for both the extra space needed for rotation, as well as
        // the extra space taken up by the arms due to their angle
        private const val PADDING_FACTOR = 0.05f

        //#if MC >= 26.1
        //$$ // Uses MC's uiLightmap which is only 1x1, so regular MAX_VALUE would result in undefined behavior during
        //$$ // texelFetch (usually resulting in a color of 0, rendering the entity fully translucent)
        //$$ private val MAX_LIGHT: Light
        //$$     get() {
        //$$         // Note: This currently depends on when exactly we render. Once we've migrated more to MC's "new"
        //$$         //       rendering system, it will however always be the ui lightmap. We're currently just
        //$$         //       rendering in somewhat random places.
        //$$         val isUiLightmap = Minecraft.getInstance().gameRenderer.let { it.lightmap() != it.levelLightmap() }
        //$$         return if (isUiLightmap) Light(0u) else Light.MAX_VALUE
        //$$     }
        //#else
        private val MAX_LIGHT = Light.MAX_VALUE
        //#endif

        @JvmField
        var current: UI3DPlayer? = null
        @JvmField
        var isRenderingPerspective = false
    }
}

// FIXME needs to be made safe to use from render thread once that becomes a thing
class UI3DPlayerRenderState(
    val ui3DPlayer: UI3DPlayer
) : UIPlayer.RenderState

private object UI3DPlayerSpecialRendererFactory : SpecialRenderer.Factory<UI3DPlayerRenderState> {
    override fun create(): SpecialRenderer<UI3DPlayerRenderState> =
        UI3DPlayerSpecialRendererInstance()
}

private class UI3DPlayerSpecialRendererInstance : SpecialRenderer<UI3DPlayerRenderState> {
    override val supportsScissor: Boolean
        //#if MC >= 1.21.6
        //$$ get() = false
        //#else
        get() = true
        //#endif
    override val onlyDrawsInBounds: Boolean
        get() = false

    var depthTextureView: UGpuTextureView? = null

    override fun render(
        destination: UGpuTextureView,
        instances: List<SpecialRenderer.Instance<UI3DPlayerRenderState>>
    ) {
        val device = UGraphics.getDevice()

        if (depthTextureView?.texture?.width != destination.texture.width || depthTextureView?.texture?.height != destination.texture.height) {
            depthTextureView?.texture?.close()
            depthTextureView?.close()
            depthTextureView = null
        }

        val depthTextureView = depthTextureView ?: run {
            device.createTextureView(device.createTexture(
                "UI3DPlayer Depth",
                UGpuTexture.Usage.RENDER_ATTACHMENT + UGpuTexture.Usage.COPY_DST,
                UGpuFormat.DEFAULT_DEPTH,
                destination.texture.width,
                destination.texture.height,
                1,
            ))
        }.also { depthTextureView = it }

        device.clearDepth(depthTextureView.texture, if (platform.usesReversedZ) 0.0 else 1.0)

        UI3DPlayerSpecialRenderer.render(destination, depthTextureView, instances)
    }

    override fun close() {
        depthTextureView?.texture?.close()
        depthTextureView?.close()
        depthTextureView = null
    }
}

object UI3DPlayerSpecialRenderer {
    //#if MC < 26.3
    private val drawFramebufferContext = DrawFramebufferContext()
    //#endif

    fun render(
        color: UGpuTextureView,
        depth: UGpuTextureView,
        instance: SpecialRenderer.Instance<UIPlayer.RenderState>,
    ) {
        render(color, depth, listOf(SpecialRenderer.Instance(
            instance.dstX, instance.dstY, instance.width, instance.height,
            instance.scissorX, instance.scissorY, instance.scissorWidth, instance.scissorHeight,
            instance.args as UI3DPlayerRenderState,
        )))
    }

    fun render(
        destination: UGpuTextureView,
        destinationDepth: UGpuTextureView,
        instances: List<SpecialRenderer.Instance<UI3DPlayerRenderState>>,
    ) {
        //#if MC >= 26.3
        //$$ overrideRenderTarget(destination, destinationDepth)
        //$$ try {
        //#else
        drawFramebufferContext.withDrawFramebuffer(destination, destinationDepth) {
        //#endif
            val guiScale = UResolution.scaleFactor
            withDefaultMcGuiRenderingSetup(destination.texture.width, destination.texture.height, guiScale) {
                for (instance in instances) {
                    //#if MC < 1.21.6
                    ScissorState(true, instance.scissorX, destination.texture.height - instance.scissorHeight - instance.scissorY, instance.scissorWidth, instance.scissorHeight).activate()
                    //#endif

                    val stack = UMatrixStack()
                    stack.translate(instance.dstX.toFloat() / guiScale.toFloat(), instance.dstY.toFloat() / guiScale.toFloat(), 0f)

                    val player = instance.args.ui3DPlayer
                    val camera = player.perspectiveCamera
                    if (camera != null) {
                        player.drawWithPerspectiveProjection(stack, camera, destination.texture.width, destination.texture.height)
                    } else {
                        player.drawWithOrthographicProjection(stack)
                    }
                }
            }
        //#if MC >= 26.3
        //$$ } finally {
        //$$     MinecraftRenderBackend.outputColorTexture = null
        //$$     MinecraftRenderBackend.outputDepthTexture = null
        //$$ }
        //#else
        }
        //#endif
    }

    fun overrideRenderTarget(colorView: UGpuTextureView, depthView: UGpuTextureView) {
        //#if MC >= 26.3
        //$$ MinecraftRenderBackend.outputColorTexture = UGraphics.getPlatformAdapter().textureView(colorView)
        //$$ MinecraftRenderBackend.outputDepthTexture = UGraphics.getPlatformAdapter().textureView(depthView)
        //#else
        drawFramebufferContext.overrideRenderTarget(colorView, depthView)
        //#endif
    }
}
