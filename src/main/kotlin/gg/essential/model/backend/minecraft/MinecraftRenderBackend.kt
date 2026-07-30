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
package gg.essential.model.backend.minecraft

import dev.folomeev.kotgl.matrix.vectors.mutables.mutableVec3
import dev.folomeev.kotgl.matrix.vectors.mutables.mutableVec4
import gg.essential.model.ParticleEffect
import gg.essential.model.backend.RenderBackend
import gg.essential.model.file.ParticlesFile.Material.*
import gg.essential.model.light.Light
import gg.essential.model.util.Color
import gg.essential.model.util.timesSelf
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMinecraft.getMinecraft
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.shader.BlendState
import gg.essential.universal.utils.ReleasedDynamicTexture
import gg.essential.universal.vertex.UVertexConsumer
import gg.essential.util.OptiFineAccessor
import gg.essential.util.UnownedGpuTextureImpl
import gg.essential.util.identifier
import gg.essential.util.image.GpuTexture
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.io.ByteArrayInputStream
import gg.essential.model.util.UMatrixStack as CMatrixStack
import gg.essential.model.util.UVertexConsumer as CVertexConsumer

//#if MC >= 26.2
//$$ import net.minecraft.client.Minecraft
//$$ import net.minecraft.client.renderer.SubmitNodeStorage
//#endif

//#if MC>=12111
//$$ import net.minecraft.client.render.RenderLayers
//#endif

//#if MC>=12109
//$$ import net.minecraft.client.render.command.OrderedRenderCommandQueue
//#endif

//#if MC>=12105
//$$ import net.minecraft.client.texture.GlTexture
//#endif

//#if FABRIC && MC >= 1.21.5
//$$ import gg.essential.util.ModLoaderUtil
//$$ import net.irisshaders.iris.api.v0.IrisApi
//#endif

//#if MC>=12102
//$$ import net.minecraft.client.render.VertexConsumer
//#endif

//#if MC>=11600
//$$ import net.minecraft.client.renderer.RenderType
//$$ import net.minecraft.client.renderer.texture.OverlayTexture
//#else
import net.minecraft.client.renderer.vertex.VertexFormat
import org.lwjgl.BufferUtils
//#endif

object MinecraftRenderBackend : RenderBackend {
    private val registeredTextures = mutableMapOf<ResourceLocation, DynamicTexture>()

    override fun createTexture(name: String, width: Int, height: Int): RenderBackend.Texture {
        return DynamicTexture(identifier("essential", name), ReleasedDynamicTexture(width, height))
    }

    override fun deleteTexture(texture: RenderBackend.Texture) {
        val identifier = (texture as DynamicTexture).identifier

        //#if MC>=12105
        //$$ texture.texture.close()
        //#else
        texture.texture.deleteGlTexture()
        //#endif

        if (registeredTextures.remove(identifier, texture)) {
            getMinecraft().textureManager.deleteTexture(identifier)
        }
    }

    override fun blitTexture(dst: RenderBackend.Texture, ops: Iterable<RenderBackend.BlitOp>) {
        val textureManager = getMinecraft().textureManager
        fun RenderBackend.Texture.gpuTexture(): UnownedGpuTextureImpl {
            val mcTexture = textureManager.getTexture((this as MinecraftTexture).identifier)!!
            //#if MC >= 1.21.6
            //$$ val textureView = UGraphics.getPlatformAdapter().textureView(mcTexture.glTextureView)
            //#else
            //#if MC >= 1.21.5
            //$$ val texture = UGraphics.getPlatformAdapter().texture(mcTexture.glTexture)
            //#else
            val texture = UGraphics.getPlatformAdapter().texture(mcTexture.glTextureId, UGpuFormat.DEFAULT_RGBA, width, height, 1)
            //#endif
            val textureView = UGraphics.getDevice().createTextureView(texture)
            //#endif
            return UnownedGpuTextureImpl(GpuTexture.Format.RGBA8, textureView)
        }

        dst.gpuTexture().copyFrom(ops.map { GpuTexture.CopyOp(it.src.gpuTexture(), it.srcX, it.srcY, it.destX, it.destY, it.width, it.height) })
    }

    //#if MC>=12104
    //$$ // As of 1.21.4, MC itself now finally uses the RenderLayer system for its particles, so we'll do the same.
    //$$ // Our particles have more blending modes though, so we need some custom layers.
    //$$ private val particleLayers = mutableMapOf<ParticleEffect.RenderPass, RenderLayer>()
    //$$ fun getParticleLayer(renderPass: ParticleEffect.RenderPass) = particleLayers.getOrPut(renderPass) {
    //#if MC>=12105
    //$$     RenderLayerFactory.createParticleLayer(renderPass)
    //#else
    //$$     val texture = (renderPass.texture as MinecraftTexture).identifier
    //$$     val inner =
    //$$         if (renderPass.material == Cutout) RenderLayer.getOpaqueParticle(texture)
    //$$         else RenderLayer.getTranslucentParticle(texture)
    //$$     // Note: If this is turned into an anonymous class, Kotlin will generate the bridge for the protected
    //$$     // field in the wrong class (MinecraftRenderBackend), which will then throw an IllegalAccessError.
    //$$     class ParticleLayer : RenderLayer(
    //$$         renderPass.material.name.lowercase() + "_particle",
    //$$         inner.vertexFormat,
    //$$         inner.drawMode,
    //$$         inner.expectedBufferSize,
    //$$         inner.hasCrumbling(),
    //$$         inner.isTranslucent,
    //$$         {
    //$$             inner.startDrawing()
    //$$             when (renderPass.material) {
    //$$                 Cutout -> {} // blending is disabled by default
    //$$                 Add -> BlendState(BlendState.Equation.ADD, BlendState.Param.SRC_ALPHA, BlendState.Param.ONE).activate()
    //$$                 Blend -> BlendState.ALPHA.activate()
    //$$             }
    //$$         },
    //$$         {
    //$$             RenderSystem.disableBlend()
    //$$             RenderSystem.defaultBlendFunc()
    //$$             inner.endDrawing()
    //$$         },
    //$$     )
    //$$     ParticleLayer()
    //#endif
    //$$ }
    //#endif

    //#if MC>=12102
    //$$ // MC no longer provides the CULL variant of ENTITY_TRANSLUCENT which we use to render our cosmetics, so we'll
    //$$ // have to build it ourselves.
    //$$ private val entityTranslucentCullLayers = mutableMapOf<Identifier, RenderLayer>()
    //$$ fun getEntityTranslucentCullLayer(texture: Identifier) = entityTranslucentCullLayers.getOrPut(texture) {
    //#if MC>=12105
    //$$     RenderLayerFactory.createEntityTranslucentCullLayer(texture)
    //#else
    //$$     val inner = RenderLayer.getEntityTranslucent(texture)
    //$$     // Note: If this is turned into an anonymous class, Kotlin will generate the bridge for the protected
    //$$     // field in the wrong class (MinecraftRenderBackend), which will then throw an IllegalAccessError.
    //$$     class EntityTranslucentCullLayer : RenderLayer(
    //$$         "entity_translucent_cull",
    //$$         inner.vertexFormat,
    //$$         inner.drawMode,
    //$$         inner.expectedBufferSize,
    //$$         inner.hasCrumbling(),
    //$$         inner.isTranslucent,
    //$$         {
    //$$             inner.startDrawing()
    //$$             DISABLE_CULLING.endDrawing()
    //$$             ENABLE_CULLING.startDrawing()
    //$$         },
    //$$         {
    //$$             ENABLE_CULLING.endDrawing()
    //$$             DISABLE_CULLING.startDrawing()
    //$$             inner.endDrawing()
    //$$         },
    //$$     )
    //$$     EntityTranslucentCullLayer()
    //#endif
    //$$ }
    //#elseif MC>=11400
    //$$ fun getEntityTranslucentCullLayer(texture: ResourceLocation) = RenderType.getEntityTranslucentCull(texture)
    //#endif


    //#if MC>=11400
    //$$ // Neither of the render layers which vanilla provides across the versions quite does what we need, so we'll need
    //$$ // to construct one of our own (based on one of the existing ones, so it works with shaders).
    //$$ // Specifically we want:
    //$$ // 1. Culling enabled: it is enabled during our regular pass, and during MC's cape rendering, and if we do not
    //$$ //    keep it enabled during the emissive pass, we'll get z-fighting issues because the backward-facing triangles
    //$$ //    which can then be visible will have subtly (floats are not associative!) different depth values than the
    //$$ //    forward-facing ones rendered during the regular pass.
    //$$ //    The `entity_translucent_emissive` layer does not have culling enabled.
    //$$ // 2. Regular blending: allows us to do different intensity of emissiveness via the alpha channel of the emissive
    //$$ //    texture.
    //$$ //    The `eyes` layer uses additive blending which gives overly bright results (RGB values get saturated) when in
    //$$ //    already well lit spaces.
    //$$ // 3. Alpha values: so we can do the aforementioned blending.
    //$$ //    Both layers support these, however the `entity_translucent_emissive` layer discards any pixel with
    //$$ //    `alpha < 0.1`, luckily the `eyes` one doesn't, so that's what we'll use.
    //#if MC>=12105
    //$$ // The `eyes` layer now uses regular blending, so we can use it directly
    //$$ fun getEmissiveLayer(texture: Identifier) =
        //#if MC>=12111
        //$$ RenderLayers.eyes(texture)
        //#else
        //$$ RenderLayer.getEyes(texture)
        //#endif
    //#else
    //$$ private val emissiveLayers = mutableMapOf<ResourceLocation, RenderType>()
    //$$ fun getEmissiveLayer(texture: ResourceLocation) = emissiveLayers.getOrPut(texture) {
    //$$     val inner = RenderType.getEyes(texture)
    //$$     val of = OptiFineAccessor.INSTANCE
    //$$     // Note: If this is turned into an anonymous class, Kotlin will generate the bridge for the protected
    //$$     // field in the wrong class (MinecraftRenderBackend), which will then throw an IllegalAccessError.
    //$$     class EmissiveLayer : RenderType(
    //$$         "entity_translucent_emissive_cull",
    //$$         inner.vertexFormat,
    //$$         inner.drawMode,
    //$$         inner.bufferSize,
    //$$         inner.isUseDelegate,
    //$$         true,
    //$$         {
    //$$             inner.setupRenderState()
    //$$             TRANSLUCENT_TRANSPARENCY.setupRenderState()
    //$$             // OptiFine on these versions does these calls in `RenderLayer.draw` only for the specific layer,
    //$$             // so we need to manually do them in our layer
    //$$             if (of != null && of.Config_isShaders()) {
    //$$                 of.Shaders_pushProgram()
    //$$                 of.Shaders_beginSpiderEyes()
    //$$             }
    //$$         },
    //$$         {
    //$$             if (of != null && of.Config_isShaders()) {
    //$$                 of.Shaders_endSpiderEyes()
    //$$                 of.Shaders_popProgram()
    //$$             }
    //$$             TRANSLUCENT_TRANSPARENCY.clearRenderState()
    //$$             inner.clearRenderState()
    //$$         },
    //$$     )
    //$$     EmissiveLayer()
    //$$ }
    //#endif
    //$$ // For the elytra we need need a special layer because the armor layer used to render it adds a tiny extra scale
    //$$ // offset (VIEW_OFFSET_Z_LAYERING) to the model-view matrix, meaning it'll be slightly closer to the camera than
    //$$ // our emissive layer if we don't do the same.
    //$$ private val armorLayers = mutableMapOf<ResourceLocation, RenderType>()
    //$$ fun getEmissiveArmorLayer(texture: ResourceLocation) = armorLayers.getOrPut(texture) {
    //#if MC>=12105
    //$$     RenderLayerFactory.createEmissiveArmorLayer(texture)
    //#else
    //$$     val inner = getEmissiveLayer(texture)
    //$$     // Note: If this is turned into an anonymous class, Kotlin will generate the bridge for the protected
    //$$     // field in the wrong class (MinecraftRenderBackend), which will then throw an IllegalAccessError.
    //$$     class EmissiveArmorLayer : RenderType(
    //$$         "armor_translucent_emissive",
    //$$         inner.vertexFormat,
    //$$         inner.drawMode,
    //$$         inner.bufferSize,
    //$$         inner.isUseDelegate,
    //$$         true,
    //$$         { inner.setupRenderState(); field_239235_M_.setupRenderState() },
    //$$         { field_239235_M_.clearRenderState(); inner.clearRenderState() },
    //$$     )
    //$$     EmissiveArmorLayer()
    //#endif
    //$$ }
    //#if MC>=12102
    //$$ object NullMcVertexConsumer : VertexConsumer {
    //$$     override fun vertex(x: Float, y: Float, z: Float): VertexConsumer = this
    //$$     override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = this
    //$$     override fun texture(u: Float, v: Float): VertexConsumer = this
    //$$     override fun overlay(u: Int, v: Int): VertexConsumer = this
    //$$     override fun light(u: Int, v: Int): VertexConsumer = this
    //$$     override fun normal(x: Float, y: Float, z: Float): VertexConsumer = this
        //#if MC>=12111
        //$$ override fun color(argb: Int): VertexConsumer = this
        //$$ override fun lineWidth(width: Float): VertexConsumer = this
        //#endif
    //$$ }
    //#endif
    //#else
    private val MC_AMBIENT_LIGHT = BufferUtils.createFloatBuffer(4).apply {
        put(0.4f).put(0.4f).put(0.4f).put(1f) // see RenderHelper class
        flip()
    }
    private val EMISSIVE_AMBIENT_LIGHT = BufferUtils.createFloatBuffer(4).apply {
        put(1f).put(1f).put(1f).put(1f)
        flip()
    }

    fun setupEmissiveRendering(): () -> Unit {
        // Replace lightmap texture with all-white texture
        var prevLightmapTexture = 0
        UGraphics.configureTextureUnit(1) {
            prevLightmapTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        }
        UGraphics.bindTexture(1, identifier("essential", "textures/white.png"))
        // Disable directional lighting
        // However we cannot simply disable GL_LIGHTING because that results in substantial changes to the
        // fixed-function pipeline, and apparent those are substantial enough to somehow end up with minimally
        // different z-buffer values, resulting in z-fighting between the base layer and the emissive layer.
        // So instead we disable the individual directional lights and turn up the ambient light to 100.
        GlStateManager.disableLight(0)
        GlStateManager.disableLight(1)
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, EMISSIVE_AMBIENT_LIGHT)
        // Change alphaFunc to allow us to use the full range of alpha values (by default vanilla cuts off at 0.1)
        val prevAlphaTestFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)
        val prevAlphaTestRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0f)
        // OptiFine on these versions manually wraps all the emissive eye render calls, so we need to manually
        // invoke its wrapper methods too
        val of = OptiFineAccessor.INSTANCE
        if (of != null && of.Config_isShaders()) {
            of.Shaders_pushProgram()
            of.Shaders_beginSpiderEyes()
        }

        return {
            if (of != null && of.Config_isShaders()) {
                of.Shaders_endSpiderEyes()
                of.Shaders_popProgram()
            }

            GlStateManager.alphaFunc(prevAlphaTestFunc, prevAlphaTestRef)

            GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, MC_AMBIENT_LIGHT)
            GlStateManager.enableLight(1)
            GlStateManager.enableLight(0)

            UGraphics.bindTexture(1, prevLightmapTexture)
        }
    }
    //#endif

    override suspend fun readTexture(name: String, bytes: ByteArray): RenderBackend.Texture {
        return CosmeticTexture(name, UGraphics.getTexture(ByteArrayInputStream(bytes)))
    }

    interface MinecraftTexture : RenderBackend.Texture {
        val identifier: ResourceLocation
    }

    data class SkinTexture(override val identifier: ResourceLocation) : MinecraftTexture {
        override val width: Int
            get() = 64
        override val height: Int
            get() = 64
    }

    data class CapeTexture(override val identifier: ResourceLocation) : MinecraftTexture {
        override val width: Int
            get() = 64
        override val height: Int
            get() = 32
    }

    open class DynamicTexture(identifier: ResourceLocation, val texture: ReleasedDynamicTexture) : MinecraftTexture {
        override val width: Int = texture.width
        override val height: Int = texture.height

        override val identifier: ResourceLocation by lazy {
            registeredTextures[identifier]?.let { deleteTexture(it) }
            getMinecraft().textureManager.loadTexture(identifier, texture)
            registeredTextures[identifier] = this
            identifier
        }
    }

    class CosmeticTexture(
        val name: String,
        texture: ReleasedDynamicTexture,
    ) : DynamicTexture(identifier("essential", "textures/cosmetics/${name.lowercase()}"), texture)

    private fun skipRendering(): Boolean {
        // Workaround for Iris missing a `assignPipelineShadow` method (similar to the `assignPipeline` one we already
        // use in RenderLayerFactory), and Iris still issuing the draw call despite not being able to find an
        // appropriate pipeline. This results in invalid draw call log spam (and potentially worse depending on driver)
        // when the part of our geometry that use a custom pipeline are being rendered during the shadow pass.
        // Forgelike is not affected because we copyFrom our custom RenderPipelines from vanilla ones, and Iris injects
        // into Forge's copyFrom method to copy over the shadow pipeline assignment.
        //#if FABRIC && MC >= 1.21.5
        //$$ if (ModLoaderUtil.isModLoaded("iris")) {
        //$$     // Separate method for class loading reasons
        //$$     fun isRenderingShadowPass() = IrisApi.getInstance().isRenderingShadowPass
        //$$     if (isRenderingShadowPass()) return true
        //$$ }
        //#endif
        return false
    }

    class CommandQueue : RenderBackend.CommandQueue {
        private class Key(
            val texture: MinecraftTexture,
            val translucent: Boolean,
            val emissive: Boolean,
        ) : Comparable<Key> {
            override fun compareTo(other: Key): Int = KEY_COMPARATOR.compare(this, other)
            companion object {
                val KEY_COMPARATOR =
                    // Translucent geometry must be last
                    compareBy<Key> { it.translucent }
                        // emissive pass must be after the corresponding opaque geometry
                        .thenBy { it.emissive }
                        // finally sort by identifier so passes with identical texture can be rendered in one pass
                        //#if MC>=11200
                        .thenBy { it.texture.identifier }
                        //#else
                        //$$ .thenBy { it.texture.identifier.resourceDomain }
                        //$$ .thenBy { it.texture.identifier.resourcePath }
                        //#endif
            }
        }

        private val queue = mutableMapOf<Key, MutableList<(CVertexConsumer) -> Unit>>()
        // TODO could add batching and sorting to this queue as well
        //  but ParticleSystem already does both internally, so not really worth it right now
        // TODO could merge particles and cosmetics into one queue, so we can render opaque particles before translucent
        //  cosmetics, but probably not worth it until we have a cosmetics combination where the issue is more obvious
        private val particleQueue = mutableListOf<Pair<ParticleEffect.RenderPass, (CVertexConsumer) -> Unit>>()

        override fun submit(
            texture: RenderBackend.Texture,
            translucent: Boolean,
            emissive: Boolean,
            render: (CVertexConsumer) -> Unit,
        ) {
            require(texture is MinecraftTexture)
            queue.getOrPut(Key(texture, translucent, emissive), ::mutableListOf)
                .add(render)
        }

        override fun submit(renderPass: ParticleEffect.RenderPass, render: (CVertexConsumer) -> Unit) {
            particleQueue.add(Pair(renderPass, render))
        }

        fun render(vertexConsumerProvider: RenderBackend.VertexConsumerProvider) {
            if (queue.isEmpty() || skipRendering()) return

            for ((key, commands) in queue.entries.sortedBy { it.key }) {
                vertexConsumerProvider.provide(key.texture, key.emissive) { vertexConsumer ->
                    for (command in commands) {
                        command(vertexConsumer)
                    }
                }
            }
        }

        fun render(vertexConsumerProvider: ParticleVertexConsumerProvider) {
            if (particleQueue.isEmpty() || skipRendering()) return

            for ((renderPass, command) in particleQueue) {
                vertexConsumerProvider.provide(renderPass, command)
            }
        }

        fun copyTo(other: RenderBackend.CommandQueue) {
            for ((key, commands) in queue.entries.sortedBy { it.key }) {
                for (command in commands) {
                    other.submit(key.texture, key.translucent, key.emissive, command)
                }
            }
            for ((renderPass, command) in particleQueue) {
                other.submit(renderPass, command)
            }
        }

        fun renderImmediate() {
            //#if MC >= 26.2
            //$$ val dispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher()
            //$$ val submitNodeStorage = SubmitNodeStorage()
            //$$ copyTo(MinecraftCommandQueue(submitNodeStorage))
            //$$ dispatcher.renderAllFeatures(submitNodeStorage)
            //#else
            //#if MC >= 1.14
            //$$ val immediate = net.minecraft.client.Minecraft.getInstance().renderTypeBuffers.bufferSource
            //#endif

            //#if MC >= 1.14
            //$$ render(VertexConsumerProvider(immediate))
            //#else
            render(VertexConsumerProvider())
            //#endif

            //#if MC >= 1.21.4
            //$$ render(ParticleVertexConsumerProvider(immediate))
            //#else
            render(ParticleVertexConsumerProvider())
            //#endif

            //#if MC >= 1.14
            //$$ immediate.finish()
            //#endif
            //#endif
        }
    }

    //#if MC>=12109
    //$$ class MinecraftCommandQueue(
    //$$     private val mcQueue: OrderedRenderCommandQueue,
    //$$ ) : RenderBackend.CommandQueue {
    //$$     override fun submit(
    //$$         texture: RenderBackend.Texture,
    //$$         translucent: Boolean,
    //$$         emissive: Boolean,
    //$$         render: (CVertexConsumer) -> Unit,
    //$$     ) {
    //$$         if (skipRendering()) return
    //$$
    //$$         require(texture is MinecraftTexture)
    //$$         val layer =
    //$$             if (emissive) getEmissiveLayer(texture.identifier)
    //$$             else getEntityTranslucentCullLayer(texture.identifier)
    //$$         val order = when {
    //$$             translucent -> 2
    //$$             emissive -> 1
    //$$             else -> 0
    //$$         }
    //$$         mcQueue.getBatchingQueue(order).submitCustom(net.minecraft.client.util.math.MatrixStack(), layer) { _, vertexConsumer ->
                //#if MC >= 26.2
                //$$ VertexConsumerProvider(vertexConsumer)
                //#else
                //$$ VertexConsumerProvider { vertexConsumer }
                //#endif
    //$$                 .provide(texture, emissive, render)
    //$$         }
    //$$     }
    //$$
    //$$     override fun submit(renderPass: ParticleEffect.RenderPass, render: (CVertexConsumer) -> Unit) {
    //$$         if (skipRendering()) return
    //$$
    //$$         val layer = getParticleLayer(renderPass)
    //$$         val order = when {
    //$$             renderPass.material.needsSorting -> 0
    //$$             else -> 1
    //$$         }
    //$$         mcQueue.getBatchingQueue(order).submitCustom(net.minecraft.client.util.math.MatrixStack(), layer) { _, vertexConsumer ->
                //#if MC >= 26.2
                //$$ ParticleVertexConsumerProvider(vertexConsumer)
                //#else
                //$$ ParticleVertexConsumerProvider { vertexConsumer }
                //#endif
    //$$                 .provide(renderPass, render)
    //$$         }
    //$$     }
    //$$ }
    //#endif

    //#if MC >= 26.2
    //$$ class VertexConsumerProvider(val vertexConsumer: VertexConsumer) : RenderBackend.VertexConsumerProvider {
    //#elseif MC >= 1.16
    //$$ class VertexConsumerProvider(val provider: net.minecraft.client.renderer.IRenderTypeBuffer) : RenderBackend.VertexConsumerProvider {
    //#else
    class VertexConsumerProvider : RenderBackend.VertexConsumerProvider {
    //#endif

        override fun provide(texture: RenderBackend.Texture, emissive: Boolean, block: (CVertexConsumer) -> Unit) {
            require(texture is MinecraftTexture)

            class VertexConsumerAdapter(private val inner: UVertexConsumer) : CVertexConsumer {
                override fun pos(stack: CMatrixStack, x: Double, y: Double, z: Double) = apply {
                    val vec = mutableVec4(x.toFloat(), y.toFloat(), z.toFloat(), 1f)
                    vec.timesSelf(stack.peek().model)
                    inner.pos(UMatrixStack.UNIT, vec.x.toDouble(), vec.y.toDouble(), vec.z.toDouble())
                    //#if MC>=11600
                    //$$ inner.color(1f, 1f, 1f, 1f)
                    //#endif
                }
                override fun tex(u: Double, v: Double) = apply {
                    inner.tex(u, v)
                    //#if MC>=11600
                    //$$ inner.overlay(OverlayTexture.getU(0f), OverlayTexture.getV(false))
                    //#endif
                }
                override fun norm(stack: CMatrixStack, x: Float, y: Float, z: Float) = apply {
                    val vec = mutableVec3(x, y, z)
                    vec.timesSelf(stack.peek().normal)
                    inner.norm(UMatrixStack.UNIT, vec.x, vec.y, vec.z)
                }
                override fun color(color: Color): CVertexConsumer = this
                override fun light(light: Light): CVertexConsumer = apply {
                    //#if MC>=11600
                    //$$ inner.light(light.blockLight.toInt(), light.skyLight.toInt())
                    //#else
                    // Note: X and Y are flipped because `BufferBuilder.lightmap` flips them in the SHORT case
                    inner.light(light.skyLight.toInt(), light.blockLight.toInt())
                    //#endif
                }
                override fun endVertex() = apply { inner.endVertex() }
            }
            //#if MC >= 26.2
            //$$ block(VertexConsumerAdapter(UVertexConsumer.of(vertexConsumer)))
            //#elseif MC >= 1.16
            //$$ val buffer = provider.getBuffer(
            //$$     if (emissive) getEmissiveLayer(texture.identifier)
            //$$     else getEntityTranslucentCullLayer(texture.identifier)
            //$$ )
            //$$ block(VertexConsumerAdapter(UVertexConsumer.of(buffer)))
            //#else
            val prevDepthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST)
            val prevCull = GL11.glGetBoolean(GL11.GL_CULL_FACE)
            val prevAlphaTest = GL11.glGetBoolean(GL11.GL_ALPHA_TEST)
            val prevBlend = BlendState.active()

            if (!prevDepthTest) GlStateManager.enableDepth()
            if (!prevCull) GlStateManager.enableCull()
            if (!prevAlphaTest) UGraphics.enableAlpha()
            if (prevBlend != BlendState.ALPHA) UGraphics.Globals.blendState(BlendState.ALPHA)

            UGraphics.color4f(1f, 1f, 1f, 1f)
            val prevTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            UGraphics.bindTexture(0, texture.identifier)
            val cleanupEmissive = if (emissive) setupEmissiveRendering() else ({})

            val renderer = UGraphics.getFromTessellator()
            @Suppress("DEPRECATION")
            renderer.beginWithDefaultShader(UGraphics.DrawMode.QUADS, VERTEX_FORMAT)

            block(VertexConsumerAdapter(renderer.asUVertexConsumer()))

            renderer.drawSorted(0, 0, 0)

            cleanupEmissive()
            UGraphics.bindTexture(0, prevTextureId)
            if (prevBlend != BlendState.ALPHA) UGraphics.Globals.blendState(prevBlend)
            if (!prevAlphaTest) UGraphics.disableAlpha()
            if (!prevCull) GlStateManager.disableCull()
            if (!prevDepthTest) GlStateManager.disableDepth()
            //#endif
        }

        //#if MC<11400
        companion object {
            private val VERTEX_FORMAT = VertexFormat().apply {
                addElement(DefaultVertexFormats.POSITION_3F)
                addElement(DefaultVertexFormats.TEX_2F) // texture
                addElement(DefaultVertexFormats.TEX_2S) // lightmap
                addElement(DefaultVertexFormats.NORMAL_3B)
                addElement(DefaultVertexFormats.PADDING_1B)
            }
        }
        //#endif
    }

    //#if MC >= 26.2
    //$$ class ParticleVertexConsumerProvider(val vertexConsumer: VertexConsumer) {
    //#elseif MC >= 1.21.4
    //$$ class ParticleVertexConsumerProvider(val provider: net.minecraft.client.render.VertexConsumerProvider) {
    //#else
    class ParticleVertexConsumerProvider {
    //#endif
        fun provide(renderPass: ParticleEffect.RenderPass, block: (CVertexConsumer) -> Unit) {
            val texture = renderPass.texture
            require(texture is MinecraftTexture)

            class VertexConsumerAdapter(private val inner: UVertexConsumer) : CVertexConsumer {
                override fun pos(stack: CMatrixStack, x: Double, y: Double, z: Double) = apply {
                    val vec = mutableVec4(x.toFloat(), y.toFloat(), z.toFloat(), 1f)
                    vec.timesSelf(stack.peek().model)
                    inner.pos(UMatrixStack.UNIT, vec.x.toDouble(), vec.y.toDouble(), vec.z.toDouble())
                }
                override fun tex(u: Double, v: Double) = apply {
                    inner.tex(u, v)
                }
                override fun norm(stack: CMatrixStack, x: Float, y: Float, z: Float) = this
                override fun color(color: Color): CVertexConsumer = apply {
                    with(color) {
                        inner.color(r.toInt(), g.toInt(), b.toInt(), a.toInt())
                    }
                }
                override fun light(light: Light): CVertexConsumer = apply {
                    inner.light(light.blockLight.toInt(), light.skyLight.toInt())
                }
                override fun endVertex() = apply { inner.endVertex() }
            }

            //#if MC >= 26.2
            //$$ block(VertexConsumerAdapter(UVertexConsumer.of(vertexConsumer)))
            //#elseif MC >= 1.21.4
            //$$ val buffer = provider.getBuffer(getParticleLayer(renderPass))
            //$$ block(VertexConsumerAdapter(UVertexConsumer.of(buffer)))
            //#else
            //#if MC<11700
            val prevAlphaTest = GL11.glGetBoolean(GL11.GL_ALPHA_TEST)
            val prevAlphaTestFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)
            val prevAlphaTestRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)
            //#endif
            val prevBlend = BlendState.active()
            val prevTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            val prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
            val prevDepthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST)

            @Suppress("DEPRECATION")
            when (renderPass.material) {
                Add -> BlendState(BlendState.Equation.ADD, BlendState.Param.SRC_ALPHA, BlendState.Param.ONE)
                Cutout -> BlendState.DISABLED
                Blend -> BlendState.ALPHA
            }.activate()
            //#if MC<11700
            GlStateManager.enableAlpha()
            GlStateManager.alphaFunc(GL11.GL_GREATER, if (renderPass.material == Cutout) 0.5f else 0.0f)
            //#endif
            UGraphics.bindTexture(0, texture.identifier)
            if (!prevCull) GlStateManager.enableCull()
            if (!prevDepthTest) GlStateManager.enableDepth()

            val renderer = UGraphics.getFromTessellator()
            @Suppress("DEPRECATION")
            renderer.beginWithDefaultShader(UGraphics.DrawMode.QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP)
            block(VertexConsumerAdapter(renderer.asUVertexConsumer()))
            renderer.drawDirect()

            if (!prevDepthTest) GlStateManager.disableDepth()
            if (!prevCull) GlStateManager.disableCull()
            UGraphics.bindTexture(0, prevTextureId)
            @Suppress("DEPRECATION")
            prevBlend.activate()
            //#if MC<11700
            if (!prevAlphaTest) GlStateManager.disableAlpha()
            GlStateManager.alphaFunc(prevAlphaTestFunc, prevAlphaTestRef)
            //#endif
            //#endif
        }
    }
}
