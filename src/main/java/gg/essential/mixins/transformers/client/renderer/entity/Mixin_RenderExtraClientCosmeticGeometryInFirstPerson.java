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
package gg.essential.mixins.transformers.client.renderer.entity;

import com.llamalad7.mixinextras.sugar.Local;
import dev.folomeev.kotgl.matrix.vectors.Vec3;
import gg.essential.cosmetics.CosmeticsRenderState;
import gg.essential.cosmetics.EssentialModelRenderer;
import gg.essential.mixins.impl.client.entity.AbstractClientPlayerExt;
import gg.essential.mixins.impl.client.renderer.entity.PlayerEntityRendererExt;
import gg.essential.model.EnumPart;
import gg.essential.model.PlayerMolangQuery;
import gg.essential.model.backend.RenderBackend;
import gg.essential.model.backend.minecraft.MinecraftRenderBackend;
import gg.essential.model.util.Quaternion;
import gg.essential.universal.UMatrixStack;
import kotlin.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC>=12109
//$$ import net.minecraft.client.render.command.OrderedRenderCommandQueue;
//$$ import net.minecraft.client.render.state.WorldRenderState;
//$$ import org.spongepowered.asm.mixin.Unique;
//#endif

//#if MC>=12102
//$$ import net.minecraft.client.render.RenderTickCounter;
//#endif

//#if MC>=11600
//$$ import net.minecraft.client.renderer.ActiveRenderInfo;
//$$ import org.spongepowered.asm.mixin.Final;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import net.minecraft.client.renderer.IRenderTypeBuffer;
//$$ import com.mojang.blaze3d.matrix.MatrixStack;
//$$ import net.minecraft.client.renderer.entity.EntityRendererManager;
//#else
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Unique;
//#endif

import java.util.EnumSet;

//#if MC>=11600
//$$ @Mixin(WorldRenderer.class)
//#else
@Mixin(RenderGlobal.class)
//#endif
public abstract class Mixin_RenderExtraClientCosmeticGeometryInFirstPerson {

    //#if MC>=11600
    //$$ @Shadow @Final private EntityRendererManager renderManager;
    //#endif

    //#if MC>=12109
    //$$ private static final String RENDER_METHOD = "pushEntityRenders";
    //#elseif MC>=12102
    //$$ private static final String RENDER_METHOD = "renderEntities";
    //#elseif MC>=11600
    //$$ private static final String RENDER_METHOD = "updateCameraAndRender";
    //#else
    private static final String RENDER_METHOD = "renderEntities";
    //#endif

    @Inject(method = RENDER_METHOD,
            //#if MC>=12102
            //$$ at = @At(value = "TAIL"))
            //#elseif MC>=11600 || MC==10809
            //$$ at = @At(value = "CONSTANT", args = "stringValue=blockentities"))
            //#else
            // 1.12.2 only, note: goes before the boat noWater multipass
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos$PooledMutableBlockPos;release()V"))
            //#endif
    private void renderExtraClientCosmeticGeometry(final CallbackInfo ci,
                        //#if MC>=12109
                        //$$ // see capturePartialTicks
                        //#elseif MC>=12102
                        //$$ @Local(argsOnly = true) RenderTickCounter tickCounter,
                        //#else
                        @Local(ordinal = 0) float partialTicks, // not argsOnly in 1.21
                        //#endif

                        //#if MC>=11600
                        //#if MC>=12109
                        //$$ @Local(argsOnly = true) WorldRenderState worldRenderState,
                        //$$ @Local(argsOnly = true) OrderedRenderCommandQueue queue,
                        //#else
                        //$$ @Local(argsOnly = true) ActiveRenderInfo activeRenderInfo,
                        //$$ @Local IRenderTypeBuffer.Impl irendertypebuffer$impl,
                        //#endif
                        //$$ @Local MatrixStack matrixstackIn // not argsOnly in 1.20.6+
                        //#else
                        @Local(ordinal = 0) Entity viewEntity,
                        @Local(ordinal = 3) double renderOffsetX, // the already calculated entity position
                        @Local(ordinal = 4) double renderOffsetY,
                        @Local(ordinal = 5) double renderOffsetZ
                        //#endif
    ) {
        //#if MC<=11202
        if (MinecraftForgeClient.getRenderPass() != 0) return; // only render in the first pass
        //#endif

        //#if MC>=12109
        //$$ // FIXME there might be a better place to do this now
        //#if MC >= 26.2
        //$$ Entity viewEntity = Minecraft.getInstance().gameRenderer.mainCamera().entity();
        //#else
        //$$ Entity viewEntity = MinecraftClient.getInstance().gameRenderer.getCamera().getFocusedEntity();
        //#endif
        //#elseif MC>=11600
        //$$ Entity viewEntity = activeRenderInfo.getRenderViewEntity();
        //#endif

        if (!(viewEntity instanceof AbstractClientPlayer)) return;

        // skip rendering if the player was already rendered during this entity pass, is set to null at the start in Mixin_UpdateCosmetics
        if (((AbstractClientPlayerExt) viewEntity).getRenderedPose() != null) return;

        Render renderer = Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(viewEntity);
        if (!(renderer instanceof PlayerEntityRendererExt)) return;


        // render is confirmed, collect transforms and apply

        // get the position and rotation of the player, including renderer modifications, needs to be offset by camera position
        PlayerMolangQuery player = new PlayerMolangQuery((AbstractClientPlayer) viewEntity);
        Pair<Vec3, Quaternion> posRot = player.getPositionAndRotation();

        // get camera position
        //#if MC>=11600
        //#if MC>=12109
        //$$ net.minecraft.util.math.Vec3d cameraPos = worldRenderState.cameraRenderState.pos;
        //#else
        //$$ net.minecraft.util.math.vector.Vector3d cameraPos = activeRenderInfo.getProjectedView();
        //#endif
        //$$ double renderOffsetX = cameraPos.x;
        //$$ double renderOffsetY = cameraPos.y;
        //$$ double renderOffsetZ = cameraPos.z;
        //#endif

        UMatrixStack matrixStack = new UMatrixStack(
                //#if MC>=11600
                //$$ matrixstackIn
                //#endif
        );
        matrixStack.push();

        // offset by reverse camera/entity position
        matrixStack.translate(-renderOffsetX, -renderOffsetY, -renderOffsetZ);

        // offset by entity render position
        Vec3 pos = posRot.getFirst();
        matrixStack.translate(pos.getX(), pos.getY(), pos.getZ());

        // rotation
        Quaternion rot = posRot.getSecond();
        matrixStack.multiply(
        //#if MC>=11600
        //$$ new net.minecraft.util.math.vector.Quaternion(rot.getX(), rot.getY(), rot.getZ(), rot.getW())
        //#else
        new org.lwjgl.util.vector.Quaternion(rot.getX(), rot.getY(), rot.getZ(), rot.getW())
        //#endif
        );

        // scaling and flipping
        matrixStack.scale(-1f, -1f, 1f); // see RenderLivingBase.prepareScale
        matrixStack.scale(0.9375f, 0.9375f, 0.9375f); // see RenderPlayer.preRenderCallback

        // 1.5 from the 1.501 in RenderLivingBase.prepareScale (the .001 is already taken care of by PlayerMolangQuery)
        matrixStack.translate(0, -1.5f, 0);

        //#if MC>=11600
        //$$ int light = this.renderManager.getPackedLight(viewEntity,
            //#if MC>=12102 && MC<12109
            //$$ tickCounter.getTickDelta(true)
            //#else
            //$$ partialTicks
            //#endif
        //$$ );
        //#elseif MC >= 1.12
        int light = viewEntity.getBrightnessForRender();
        //#else
        //$$ int light = viewEntity.getBrightnessForRender(partialTicks);
        //#endif

        EssentialModelRenderer modelRenderer = ((PlayerEntityRendererExt) renderer).essential$getEssentialModelRenderer();
        CosmeticsRenderState cState = new CosmeticsRenderState.Live((AbstractClientPlayer) viewEntity);
        //#if MC>=12109
        //$$ RenderBackend.CommandQueue vertexConsumerProvider = new MinecraftRenderBackend.MinecraftCommandQueue(queue);
        //#else
        RenderBackend.VertexConsumerProvider vertexConsumerProvider =
                new MinecraftRenderBackend.VertexConsumerProvider(
                        //#if MC>=11600
                        //$$ irendertypebuffer$impl
                        //#endif
                );
        //#endif

        // render only parts connected to ROOT and no other EnumParts
        EnumSet<EnumPart> parts = EnumSet.of(EnumPart.ROOT);

        //#if MC>=11600
        //$$ modelRenderer.render(matrixStack, vertexConsumerProvider, null, cState, light, parts, false, false);
        //#else
        matrixStack.runWithGlobalState(() -> {
            modelRenderer.render(new UMatrixStack(), vertexConsumerProvider, null, cState, light, parts, false, false);
        });
        //#endif

        matrixStack.pop();
    }

    //#if MC>=12109
    //$$ @Unique private float partialTicks;
    //$$ private void capturePartialTicks(CallbackInfo ci, @Local(argsOnly = true) RenderTickCounter tickCounter) {
    //$$     partialTicks = tickCounter.getTickProgress(true);
    //$$ }
    //#endif
}