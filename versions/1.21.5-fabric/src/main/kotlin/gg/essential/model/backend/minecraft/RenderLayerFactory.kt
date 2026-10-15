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

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import gg.essential.model.ParticleEffect
import gg.essential.model.backend.minecraft.MinecraftRenderBackend.MinecraftTexture
import gg.essential.model.file.ParticlesFile.Material.Add
import gg.essential.model.file.ParticlesFile.Material.Blend
import gg.essential.model.file.ParticlesFile.Material.Cutout
import gg.essential.util.ModLoaderUtil
//#if FABRIC
import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.api.v0.IrisProgram
//#endif
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import net.minecraft.util.TriState

//#if MC >= 26.3
//$$ import com.mojang.renderpearl.api.pipeline.ShaderType
//#endif

//#if MC >= 26.2
//#else
import com.mojang.blaze3d.vertex.VertexFormat
//#endif

//#if MC >= 26.1
//$$ import com.mojang.blaze3d.pipeline.ColorTargetState
//#endif

//#if MC>=12111
//$$ import net.minecraft.client.render.LayeringTransform
//#if MC < 26.3
//$$ import net.minecraft.client.render.OutputTarget
//#endif
//$$ import net.minecraft.client.render.RenderSetup
//#endif

/** see usage in [MinecraftRenderBackend] for further context
 * prior to 1.21.5 we used a wrapper [RenderLayer] class to handle the creation of these render layers, however with each
 * version mojang has made it more difficult to create custom render layers, so we now use the actual [RenderLayer] classes
 * and builders directly.
 *
 * This is also required in 1.21.6+ for compatibility with Iris shaders, as they now require additional implementation if
 * we had continued using [RenderLayer] wrapper classes rather than building and using a [RenderLayer.MultiPhase] normally
 */
//#if MC>=12111
//$$ abstract class RenderLayerFactory {
//#else
abstract class RenderLayerFactory : RenderLayer("dummy", 0, false, false, {}, {}) {
//#endif
    companion object {

        //#if FABRIC || FORGE
        private fun RenderPipeline.toBuilder(): RenderPipeline.Builder = RenderPipeline.builder().copyFrom(this)

        private fun RenderPipeline.Builder.copyFrom(pipeline: RenderPipeline): RenderPipeline.Builder = apply {
            withLocation(pipeline.location)
            //#if MC >= 26.3
            //$$ withVertexShader(pipeline.shaders.getValue(ShaderType.VERTEX))
            //$$ withFragmentShader(pipeline.shaders.getValue(ShaderType.FRAGMENT))
            //#else
            withVertexShader(pipeline.vertexShader)
            withFragmentShader(pipeline.fragmentShader)
            //#endif
            pipeline.shaderDefines.values.forEach { (key, value) ->
                if ("." in value) withShaderDefine(key, value.toFloat())
                else withShaderDefine(key, value.toInt())
            }
            pipeline.shaderDefines.flags.forEach { withShaderDefine(it) }
            //#if MC >= 26.2
            //$$ pipeline.bindGroupLayouts.forEach { withBindGroupLayout(it) }
            //#else
            pipeline.samplers.forEach { sampler ->
                withSampler(sampler)
            }
            pipeline.uniforms.forEach { uniform ->
                withUniform(uniform.name(), uniform.type())
            }
            //#endif
            withPolygonMode(pipeline.polygonMode)
            withCull(pipeline.isCull)
            //#if MC >= 26.2
            //$$ pipeline.colorTargetStates.forEachIndexed { i, it -> if (it != null) withColorTargetState(i, it) }
            //#elseif MC >= 26.1
            //$$ withColorTargetState(pipeline.colorTargetState)
            //#else
            if (pipeline.blendFunction.isPresent) {
                withBlend(pipeline.blendFunction.get())
            } else {
                withoutBlend()
            }
            withColorWrite(pipeline.isWriteColor, pipeline.isWriteAlpha)
            //#endif
            //#if MC >= 26.1
            //$$ withDepthStencilState(java.util.Optional.ofNullable(pipeline.depthStencilState))
            //#else
            withDepthTestFunction(pipeline.depthTestFunction)
            withDepthBias(pipeline.depthBiasScaleFactor, pipeline.depthBiasConstant)
            withDepthWrite(pipeline.isWriteDepth)
            //#endif
            //#if MC < 26.1
            withColorLogic(pipeline.colorLogic)
            //#endif
            //#if MC >= 26.2
            //$$ withPrimitiveTopology(pipeline.primitiveTopology)
            //$$ pipeline.vertexFormatBindings.forEachIndexed { i, it -> if (it != null) withVertexBinding(i, it) }
            //#else
            withVertexFormat(pipeline.vertexFormat, pipeline.vertexFormatMode)
            //#endif
        }
        //#endif

        //#if FABRIC
        // Note: Cannot pass `program` directly because Iris might not be installed
        private fun RenderPipeline.assignIrisProgram(program: () -> () -> IrisProgram): RenderPipeline = apply {
            if (ModLoaderUtil.isModLoaded("iris")) {
                // Separate method for class loading reasons
                fun doIt() {
                    IrisApi.getInstance().assignPipeline(this, program()())
                }
                doIt()
            }
        }
        //#endif

        fun createEmissiveArmorLayer(texture: Identifier): RenderLayer =
            createRenderLayer(
                "armor_translucent_emissive",
                //#if MC>=12111
                //$$ RenderSetup.builder(RenderPipelines.ENTITY_EYES)
                    //#if MC >= 26.3
                    //$$ .setOitPipelines(RenderPipelines.OIT_EYES)
                    //#endif
                //$$     .crumbling()
                //$$     .translucent()
                //$$     .texture("Sampler0", texture)
                //$$     .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                //$$     .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                //$$     .build()
                //#else
                DEFAULT_BUFFER_SIZE,
                true,
                true,
                RenderPipelines.ENTITY_EYES,
                MultiPhaseParameters.builder()
                    .texture(texture, false)
                    .set(VIEW_OFFSET_Z_LAYERING, Layering::class.java)
                    .build(true)
                //#endif
            )

        private val entityTranslucentCullPipeline = RenderPipelines.ENTITY_TRANSLUCENT.toBuilder()
            .withCull(true)
            .apply {
                // The following Iris mixin substitutes a different vertex format while shaders are active, which
                // would prevent our pipeline from working without shaders (such is in-GUI) if we copied it.
                // https://github.com/IrisShaders/Iris/blob/83c2be16151f4e65fb477263c73b4170da72b4aa/common/src/main/java/net/irisshaders/iris/mixin/MixinRenderPipeline.java
                val expectedFormat = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                if (ModLoaderUtil.isModLoaded("iris")) {
                    //#if MC >= 26.2
                    //$$ withVertexBinding(0, expectedFormat)
                    //#else
                    withVertexFormat(expectedFormat, VertexFormat.DrawMode.QUADS)
                    //#endif
                }
            }
            .build()
            //#if FABRIC
            .assignIrisProgram {{ IrisProgram.ENTITIES_TRANSLUCENT }}
            //#endif

        fun createEntityTranslucentCullLayer(texture: Identifier): RenderLayer =
            createRenderLayer(
                "entity_translucent_cull",
                //#if MC>=12111
                //$$ RenderSetup.builder(entityTranslucentCullPipeline)
                    //#if MC >= 26.3
                    //$$ .setOitPipelines(RenderPipelines.OIT_ENTITY_CULL)
                    //#endif
                //$$     .crumbling()
                //$$     .translucent()
                //$$     .texture("Sampler0", texture)
                //$$     .useOverlay()
                //$$     .useLightmap()
                //$$     .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                //$$     .build()
                //#else
                DEFAULT_BUFFER_SIZE,
                true,
                true,
                entityTranslucentCullPipeline,
                MultiPhaseParameters.builder()
                    .texture(texture, false)
                    .set(ENABLE_OVERLAY_COLOR, Overlay::class.java)
                    .set(ENABLE_LIGHTMAP, Lightmap::class.java)
                    .build(true)
                //#endif
            )

        private val particleAdditivePipeline = RenderPipelines.TRANSLUCENT_PARTICLE.toBuilder()
            //#if MC >= 26.1
            //$$ .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
            //#else
            .withBlend(BlendFunction.LIGHTNING)
            //#endif
            .build()
            //#if FABRIC
            .assignIrisProgram {{ IrisProgram.PARTICLES_TRANSLUCENT }}
            //#endif

        //#if MC >= 1.21.9 && MC < 26.3
        //#if MC>=12111
        //$$ private val PARTICLES_TARGET = OutputTarget("particles") {
        //#else
        //$$ private val PARTICLES_TARGET = Target("particles") {
        //#endif
        //$$     val mc = net.minecraft.client.MinecraftClient.getInstance()
            //#if MC >= 26.2
            //$$ mc.levelRenderer.particlesTarget() ?: mc.gameRenderer.mainRenderTarget()
            //#else
            //$$ mc.worldRenderer.particlesFramebuffer ?: mc.framebuffer
            //#endif
        //$$ }
        //#endif

        fun createParticleLayer(renderPass: ParticleEffect.RenderPass): RenderLayer {
            val texture = (renderPass.texture as MinecraftTexture).identifier
            val pipeline = when (renderPass.material) {
                Cutout -> RenderPipelines.OPAQUE_PARTICLE
                Blend -> RenderPipelines.TRANSLUCENT_PARTICLE
                Add -> particleAdditivePipeline
            }

            //#if MC>=12111
            //$$ val builder = RenderSetup.builder(pipeline)
            //$$     .texture("Sampler0", texture)
            //$$     .useLightmap()
            //$$
            //#if MC >= 26.3
            //$$ if (renderPass.material != Cutout) builder.setOitPipelines(RenderPipelines.OIT_PARTICLE)
            //#else
            //$$ if (renderPass.material != Cutout) builder.outputTarget(PARTICLES_TARGET)
            //#endif
            //#else
            val builder = MultiPhaseParameters.builder()
                .texture(texture, false)
                .set(ENABLE_LIGHTMAP, Lightmap::class.java)

            if (renderPass.material != Cutout) builder.set(PARTICLES_TARGET, Target::class.java) // only diff between cutout and translucent
            //#endif


            return createRenderLayer(
                renderPass.material.name.lowercase() + "_particle",
                //#if MC>=12111
                //$$ builder.build(),
                //#else
                DEFAULT_BUFFER_SIZE,
                false,
                false,
                pipeline,
                builder.build(false)
                //#endif
            )
        }

        //#if MC>=12111
        //$$ // `of` is package-private, so we use reflection to invoke it
        //$$ private fun createRenderLayer(name: String, renderSetup: RenderSetup) =
        //$$     RenderLayer::class.java.declaredMethods.first {
        //$$         it.returnType == RenderLayer::class.java && it.parameterTypes.contentEquals(arrayOf(
        //$$             String::class.java,
        //$$             RenderSetup::class.java,
        //$$         ))
        //$$     }.apply { isAccessible = true }.invoke(null, name, renderSetup) as RenderLayer
        //#else
        // [RenderLayer.MultiPhase] is inaccessible, so we use reflection to invoke the static method "of(): RenderLayer.MultiPhase"
        private fun createRenderLayer(name: String, size: Int, hasCrumbling: Boolean, translucent: Boolean, pipeline: RenderPipeline, params: MultiPhaseParameters) =
            RenderLayer::class.java.declaredMethods
                .first { RenderLayer::class.java.isAssignableFrom(it.returnType)
                    && it.parameterTypes.contentEquals(arrayOf(
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        RenderPipeline::class.java,
                        MultiPhaseParameters::class.java
                    ))
                }
                .apply { isAccessible = true }
                .invoke(null, name, size, hasCrumbling, translucent, pipeline, params)
                    as RenderLayer // is [RenderLayer.MultiPhase]


        private fun MultiPhaseParameters.Builder.texture(texture: Identifier, mipmap: Boolean = false): MultiPhaseParameters.Builder =
            //#if MC>=12106
            //$$ set(Texture(texture, mipmap), TextureBase::class.java)
            //#else
            set(Texture(texture, TriState.FALSE, mipmap), TextureBase::class.java)
            //#endif

        // Return values and parameter types are inaccessible via invoker/accessor, so we use reflection to invoke the methods
        private fun <T> MultiPhaseParameters.Builder.set(arg: T, clss: Class<T>): MultiPhaseParameters.Builder =
             MultiPhaseParameters.Builder::class.java.declaredMethods
                    .first { it.returnType == MultiPhaseParameters.Builder::class.java
                            && it.parameterTypes.contentEquals(arrayOf(clss)) }
                    .apply { isAccessible = true }
                    .invoke(this, arg) as MultiPhaseParameters.Builder

        // Return value is inaccessible via invoker/accessor, so we use reflection to invoke the method
        private fun MultiPhaseParameters.Builder.build(affectsOutline: Boolean): MultiPhaseParameters =
            MultiPhaseParameters.Builder::class.java.declaredMethods
                .first { it.returnType == MultiPhaseParameters::class.java
                        && it.parameterTypes.contentEquals(arrayOf(Boolean::class.java)) }
                .apply { isAccessible = true }
                .invoke(this, affectsOutline) as MultiPhaseParameters
        //#endif
    }
}