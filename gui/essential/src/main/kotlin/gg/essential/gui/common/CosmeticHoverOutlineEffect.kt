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

import gg.essential.cosmetics.CosmeticId
import gg.essential.elementa.components.Window
import gg.essential.elementa.effects.Effect
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMinecraft
import gg.essential.universal.UMouse
import gg.essential.universal.render.SharedIndexBuffers
import gg.essential.universal.render.UGpuBuffer
import gg.essential.universal.render.UGpuDevice
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPassDescriptor
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.NEAREST
import gg.essential.util.image.GpuTexture
import kotlin.math.roundToInt

class CosmeticHoverOutlineEffect(
    private val outlineCosmetic: State<List<CosmeticId>>,
) : Effect() {

    private val mutableHoveredCosmetic = mutableStateOf<CosmeticId?>(null)
    val hoveredCosmetic: State<CosmeticId?> = mutableHoveredCosmetic

    override fun extractBefore(extractor: ElementaExtractor) {
        var hoveredX = (UMouse.Scaled.x * extractor.guiScale).roundToInt()
        var hoveredY = (UMouse.Scaled.y * extractor.guiScale).roundToInt()
        if (!extractor.isVisible(hoveredX, hoveredY, hoveredX + 1, hoveredY + 1)) {
            hoveredX = -1
            hoveredY = -1
        }

        val window = Window.of(boundComponent)
        // While the player itself is always within the bounds of this component, cosmetics and particles are not,
        // so we'll size the component to be the entire screen, and let the scissor reduce it where possible.
        extractor.special(
            0,
            0,
            (window.getWidth() * extractor.guiScale).roundToInt(),
            (window.getHeight() * extractor.guiScale).roundToInt(),
            CosmeticHoverOutlineEffectRenderer.Factory,
            CosmeticHoverOutlineRenderState(
                UMinecraft.guiScale * 2,
                outlineCosmetic.getUntracked(),
                hoveredX,
                hoveredY,
                mutableHoveredCosmetic,
                (boundComponent as UIPlayer).extractRenderState(),
            ),
        )

        // Skip rendering underlying UI3DPlayer
        extractor.pushScissor(0, 0, 0, 0)
    }

    override fun extractAfter(extractor: ElementaExtractor) {
        extractor.popScissor()
    }

    companion object {
        internal val compositeFragmentShaderSource = """
            #version 120
            uniform sampler2D ColorSampler;
            uniform sampler2D DepthSampler;
            varying vec2 texCoord;
            void main() {
                vec4 color = texture2D(ColorSampler, texCoord);
                if (color.a == 0.0) {
                    discard;
                }
                gl_FragColor = color;
                gl_FragDepth = texture2D(DepthSampler, texCoord).r;
            }
        """.trimIndent()

        internal val outlineFragmentShaderSource = """
            #version 120
            uniform sampler2D CompositeSampler;
            uniform sampler2D TargetSampler;
            uniform vec2 OneTexel;
            uniform int OutlineWidth;
            uniform int ReversedZ;
            varying vec2 texCoord;
            
            float depth2D(sampler2D s, vec2 coord) {
                float z = texture2D(s, coord).r;
                if (ReversedZ == 1) {
                    return 1 - z;
                } else {
                    return z;
                }
            }
            
            vec4 query(vec2 offset) {
                float composite = depth2D(CompositeSampler, texCoord + offset);
                float depth = depth2D(TargetSampler, texCoord + offset);
                if (depth > 0.99 || composite < depth) {
                    return vec4(0, 0, 0, 0);
                } else {
                    return vec4(1, 1, 1, 1);
                }
            }
            void main() {
                vec4 fragColor;
                
                bool isInside = false;
                bool shouldRender = false;
                
                for (int x = -OutlineWidth; x < OutlineWidth; x++) {
                    for (int y = -OutlineWidth; y < OutlineWidth; y++) {
                        vec2 d = vec2(float(x) * OneTexel.x, float(y) * OneTexel.y);
                        float value = query(d).a;
                        if (x == 0 && y == 0 && value == 1) {
                            isInside = true;
                        }
                        if (value == 1) {
                            shouldRender = true;
                        }
                    }
                }
                if (shouldRender && !isInside) {
                    fragColor = vec4(1, 1, 1, 1);
                } else {
                    fragColor = vec4(0, 0, 0, 0);
                }
                
                float fragDepth;
                {
                    float center = depth2D(TargetSampler, texCoord);
                    float left = depth2D(TargetSampler, texCoord - vec2(OneTexel.x, 0.0));
                    float right = depth2D(TargetSampler, texCoord + vec2(OneTexel.x, 0.0));
                    float up = depth2D(TargetSampler, texCoord - vec2(0.0, OneTexel.y));
                    float down = depth2D(TargetSampler, texCoord + vec2(0.0, OneTexel.y));
                    fragDepth = min(center, min(min(left, right), min(up, down)));
                }
                
                gl_FragColor = fragColor;
                gl_FragDepth = fragDepth;
            }
        """.trimIndent()
    }
}

interface CosmeticHoverOutlineHook {
    fun beginOutlineRender(cosmetic: CosmeticId)
    fun endOutlineRender(cosmetic: CosmeticId)

    companion object {
        var active: CosmeticHoverOutlineHook? = null
    }
}

private class CosmeticHoverOutlineRenderState(
    val outlineWidth: Int,
    val outlineCosmetics: List<CosmeticId>,

    val hoveredX: Int,
    val hoveredY: Int,
    // FIXME needs to be made safe to use from render thread once that becomes a thing
    // FIXME current implementation also blocks the render thread until the gpu is done, should do async readback
    val hoveredCosmetic: MutableState<CosmeticId?>,

    val player: UIPlayer.RenderState,
)

class CosmeticHoverOutlineEffectPass(
    val width: Int,
    val height: Int,
    private val unusedRenderResults: MutableList<RenderResult>,
) : CosmeticHoverOutlineHook {
    val composite: RenderResult = allocRenderResult()
    val cosmetics: MutableMap<CosmeticId, RenderResult> = mutableMapOf()

    private fun allocRenderResult(): RenderResult {
        val device = UGraphics.getDevice()
        val result = unusedRenderResults.removeLastOrNull() ?: RenderResult(device, width, height)
        device.clearColor(result.color, 0f, 0f, 0f, 0f)
        device.clearDepth(result.depth, if (platform.usesReversedZ) 0.0 else 1.0)
        return result
    }

    override fun beginOutlineRender(cosmetic: CosmeticId) {
        val renderResult = cosmetics.getOrPut(cosmetic) { allocRenderResult() }
        platform.overrideUIPlayerRenderTarget(renderResult.colorView, renderResult.depthView)
    }

    override fun endOutlineRender(cosmetic: CosmeticId) {
        platform.overrideUIPlayerRenderTarget(composite.colorView, composite.depthView)
    }

    class RenderResult(
        device: UGpuDevice,
        val width: Int,
        val height: Int
    ) : AutoCloseable {
        val color = device.createTexture(
            null,
            UGpuTexture.Usage.COPY_DST + UGpuTexture.Usage.RENDER_ATTACHMENT + UGpuTexture.Usage.TEXTURE_BINDING,
            UGpuFormat.DEFAULT_RGBA,
            width, height,
        )
        val depth = device.createTexture(
            null,
            UGpuTexture.Usage.COPY_DST + UGpuTexture.Usage.RENDER_ATTACHMENT + UGpuTexture.Usage.TEXTURE_BINDING + UGpuTexture.Usage.COPY_SRC,
            UGpuFormat.DEFAULT_DEPTH,
            width, height,
        )

        val colorView = device.createTextureView(color)
        val depthView = device.createTextureView(depth)

        override fun close() {
            colorView.close()
            depthView.close()
            color.close()
            depth.close()
        }
    }
}

private class CosmeticHoverOutlineEffectRenderer : SpecialRenderer<CosmeticHoverOutlineRenderState> {
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var lastRenderResults = mutableListOf<CosmeticHoverOutlineEffectPass.RenderResult>()

    override val supportsScissor: Boolean
        get() = false
    override val onlyDrawsInBounds: Boolean
        get() = false

    override fun render(destination: UGpuTextureView, instances: List<SpecialRenderer.Instance<CosmeticHoverOutlineRenderState>>) =
        instances.forEach { render(destination, it) }

    private fun render(
        destination: UGpuTextureView,
        instance: SpecialRenderer.Instance<CosmeticHoverOutlineRenderState>,
    ) {
        val device = UGraphics.getDevice()

        if (lastWidth != destination.texture.width || lastHeight != destination.texture.height) {
            lastRenderResults.forEach { it.close() }
            lastRenderResults.clear()
        }
        lastWidth = destination.texture.width
        lastHeight = destination.texture.height

        //
        // Render player and cosmetics
        //
        val pass = CosmeticHoverOutlineEffectPass(destination.texture.width, destination.texture.height, lastRenderResults)
        CosmeticHoverOutlineHook.active = pass
        try {
            platform.renderUIPlayer(pass.composite.colorView, pass.composite.depthView, SpecialRenderer.Instance(
                instance.dstX, instance.dstY, instance.width, instance.height,
                instance.scissorX, instance.scissorY, instance.scissorWidth, instance.scissorHeight,
                instance.args.player,
            ))
        } finally {
            CosmeticHoverOutlineHook.active = null

            lastRenderResults.add(pass.composite)
            lastRenderResults.addAll(pass.cosmetics.values)
        }

        //
        // Read back hovered pixel
        //
        val hoveredCosmetic = computeHoveredCosmetic(instance, pass)
        instance.args.hoveredCosmetic.set(hoveredCosmetic)

        //
        // Copy composite to destination and render outlines
        //
        val (indexBuffer, indexType) = SharedIndexBuffers.quads(4)
        createVertexBuffer(destination.texture, instance).use { vertexBuffer ->
            device.createRenderPass(
                URenderPassDescriptor { "CosmeticHoverOutlineEffect" }
                    .withColorAttachment(destination)
            ).use { renderPass ->
                renderPass.indexBuffer(indexBuffer, indexType)
                renderPass.vertexBuffer(0, vertexBuffer.slice())

                renderPass.pipeline(COMPOSITE_PIPELINE)
                renderPass.texture("ColorSampler", pass.composite.colorView, UGpuSampler.NEAREST)
                renderPass.texture("DepthSampler", pass.composite.depthView, UGpuSampler.NEAREST)
                renderPass.drawIndexed(6)

                for (cosmeticId in instance.args.outlineCosmetics) {
                    val cosmetic = pass.cosmetics[cosmeticId] ?: continue
                    renderPass.pipeline(OUTLINE_PIPELINE)
                    renderPass.texture("CompositeSampler", pass.composite.depthView, UGpuSampler.NEAREST)
                    renderPass.texture("TargetSampler", cosmetic.depthView, UGpuSampler.NEAREST)
                    renderPass.uniform("OneTexel", 1f / cosmetic.width, 1f / cosmetic.height)
                    renderPass.uniform("OutlineWidth", instance.args.outlineWidth)
                    renderPass.uniform("ReversedZ", if (platform.usesReversedZ && !platform.irisReversesZ) 1 else 0)
                    renderPass.drawIndexed(6)
                }
            }
        }
    }

    private fun createVertexBuffer(
        destination: UGpuTexture,
        instance: SpecialRenderer.Instance<CosmeticHoverOutlineRenderState>,
    ): UGpuBuffer {
        val u1 = instance.dstX / destination.width.toDouble()
        val v1 = instance.dstY / destination.height.toDouble()
        val u2 = (instance.dstX + instance.width) / destination.width.toDouble()
        val v2 = (instance.dstY + instance.height) / destination.height.toDouble()
        val x1 = (u1 * 2) - 1
        val y1 = (v1 * 2) - 1
        val x2 = (u2 * 2) - 1
        val y2 = (v2 * 2) - 1
        return UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE).apply {
            pos(UMatrixStack.UNIT, x1, y1, 0.0).tex(u1, v1).endVertex()
            pos(UMatrixStack.UNIT, x2, y1, 0.0).tex(u2, v1).endVertex()
            pos(UMatrixStack.UNIT, x2, y2, 0.0).tex(u2, v2).endVertex()
            pos(UMatrixStack.UNIT, x1, y2, 0.0).tex(u1, v2).endVertex()
        }.build()!!.use { builtBuffer ->
            UGraphics.getDevice().createBuffer(UGpuBuffer.Usage.VERTEX, builtBuffer.toByteBuffer())
        }
    }

    private fun computeHoveredCosmetic(
        instance: SpecialRenderer.Instance<CosmeticHoverOutlineRenderState>,
        pass: CosmeticHoverOutlineEffectPass,
    ): CosmeticId? {
        if (instance.args.hoveredX == -1 || instance.args.hoveredY == -1) {
            return null
        }

        fun UGpuTextureView.readHoveredDepth(): Float {
            val gpuTexture = platform.wrapGpuTexture(GpuTexture.Format.DEPTH32, this)
            val depth = gpuTexture.readPixelDepth(
                instance.args.hoveredX + instance.dstX,
                texture.height - (instance.args.hoveredY + instance.dstY),
            )
            return if (platform.usesReversedZ) 1 - depth else depth
        }

        val (hoveredCosmetic, hoveredDepth) = pass.cosmetics.entries.associate {
            it.key to it.value.depthView.readHoveredDepth()
        }.minByOrNull { it.value } ?: return null

        val compositeDepth = pass.composite.depthView.readHoveredDepth()
        if (hoveredDepth - 0.0001f >= compositeDepth.coerceAtMost(0.999f)) {
            return null // player is obstructing the cosmetic
        }

        return hoveredCosmetic
    }

    override fun close() {
        lastRenderResults.forEach { it.close() }
        lastRenderResults.clear()
    }

    object Factory : SpecialRenderer.Factory<CosmeticHoverOutlineRenderState> {
        override fun create(): SpecialRenderer<CosmeticHoverOutlineRenderState> =
            CosmeticHoverOutlineEffectRenderer()
    }

    companion object {
        private val vertexShaderSource = """
            #version 120
            varying vec2 texCoord;
            void main(){
                gl_Position = gl_Vertex;
                texCoord = gl_MultiTexCoord0.st;
            }
        """.trimIndent()

        private val COMPOSITE_PIPELINE = URenderPipeline.builderWithLegacyShader(
            "essential:cosmetic_hover_outline_composite",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE,
            vertexShaderSource,
            CosmeticHoverOutlineEffect.compositeFragmentShaderSource,
        ).apply {
            blendState = BlendState.PREMULTIPLIED_ALPHA
        }.build()

        private val OUTLINE_PIPELINE = URenderPipeline.builderWithLegacyShader(
            "essential:cosmetic_hover_outline",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE,
            vertexShaderSource,
            CosmeticHoverOutlineEffect.outlineFragmentShaderSource,
        ).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}
