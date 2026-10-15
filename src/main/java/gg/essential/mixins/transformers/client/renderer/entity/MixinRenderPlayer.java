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

import dev.folomeev.kotgl.matrix.matrices.Mat4;
import gg.essential.cosmetics.CosmeticsRenderState;
import gg.essential.cosmetics.EssentialModelRenderer;
import gg.essential.gui.emotes.EmoteWheel;
import gg.essential.mixins.impl.client.renderer.entity.PlayerEntityRendererExt;
import gg.essential.model.EnumPart;
import gg.essential.model.backend.RenderBackend;
import gg.essential.model.backend.minecraft.MinecraftRenderBackend;
import gg.essential.universal.UMatrixStack;
import gg.essential.util.GLUtil;
import net.minecraft.client.model.ModelPlayer;
import gg.essential.handlers.RenderPlayerBypass;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

//#if MC>=12109
//$$ import net.minecraft.client.render.command.OrderedRenderCommandQueue;
//$$ import net.minecraft.entity.PlayerLikeEntity;
//#endif

//#if MC>=12102
//$$ import gg.essential.mixins.impl.client.model.PlayerEntityRenderStateExt;
//$$ import net.minecraft.client.MinecraftClient;
//$$ import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
//$$ import net.minecraft.util.Identifier;
//$$ import gg.essential.mixins.impl.client.model.ModelBipedUtil;
//#endif

//#if MC>=11700
//$$ import net.minecraft.client.render.entity.EntityRendererFactory.Context;
//$$ import net.minecraft.text.Text;
//#endif

//#if MC>=11400
//$$ import com.mojang.blaze3d.matrix.MatrixStack;
//$$ import net.minecraft.client.renderer.IRenderTypeBuffer;
//$$ import net.minecraft.client.renderer.model.ModelRenderer;
//#else
import net.minecraft.client.renderer.OpenGlHelper;
//#endif

//#if MC<=10809
//$$ import net.minecraft.client.renderer.entity.RendererLivingEntity;
//$$ import net.minecraft.entity.Entity;
//#else
import net.minecraft.client.renderer.entity.RenderLivingBase;
//#endif

//#if FORGE && MC<11700
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
//#endif

@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer
    //#if MC>=12102
    //$$ extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel>
    //#elseif MC>=11400
    //$$ extends LivingRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>>
    //#elseif MC>=12000
    extends RenderLivingBase<AbstractClientPlayer>
    //#else
    //$$ extends RendererLivingEntity<AbstractClientPlayer>
    //#endif
    implements PlayerEntityRendererExt
{

    //#if MC<11400
    @Shadow public abstract ModelPlayer getMainModel();
    //#endif

    @Unique
    protected EssentialModelRenderer essentialModelRenderer;

    //#if MC>=11700
    //$$ @Inject(method = "<init>", at = @At("RETURN"))
    //#else
    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/RenderManager;Z)V", at = @At("RETURN"))
    //#endif
    private void initEssentialCosmeticsLayer(CallbackInfo ci) {
        essentialModelRenderer = new EssentialModelRenderer((RenderPlayer) (Object) this);
        this.layerRenderers.add(essentialModelRenderer);
    }

    @Override
    public EssentialModelRenderer essential$getEssentialModelRenderer(){
        return essentialModelRenderer;
    }

    @Override
    public Iterable<?> essential$getFeatures() {
        return this.layerRenderers;
    }

    //#if FORGE && MC<11700
    @Redirect(
            //#if MC>=11400
            //$$ method = "render(Lnet/minecraft/client/entity/player/AbstractClientPlayerEntity;FFLcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V",
            //$$ at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z", remap = false)
            //#else
            method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z", remap = false)
            //#endif
    )
    private boolean cancelPostIfBypass(EventBus eventBus, Event event) {
        return !RenderPlayerBypass.bypass && eventBus.post(event);
    }
    //#endif

    @Inject(method = "renderLeftArm", at = @At("HEAD"))
    private void isRenderingLeftArm(CallbackInfo ci) {
        EmoteWheel.isPlayerArmRendering = true;
        resetTransforms();
    }

    @Inject(method = "renderLeftArm", at = @At("RETURN"))
    //#if MC>=11400
    //#if MC>=12102
    //#if MC>=12109
    //$$ private void renderLeftArm(MatrixStack vMatrixStack, OrderedRenderCommandQueue queue, int light, Identifier skinTexture, boolean sleeveVisible, CallbackInfo ci) {
    //#else
    //$$ private void renderLeftArm(MatrixStack vMatrixStack, VertexConsumerProvider buffers, int light, Identifier skinTexture, boolean sleeveVisible, CallbackInfo ci) {
    //#endif
    //$$     AbstractClientPlayerEntity player = MinecraftClient.getInstance().player;
    //$$     if (player == null) return;
    //#else
    //$$ private void renderLeftArm(MatrixStack vMatrixStack, IRenderTypeBuffer buffers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
    //#endif
    //$$     UMatrixStack matrixStack = new UMatrixStack(vMatrixStack);
    //#if MC>=12109
    //$$     RenderBackend.CommandQueue vertexConsumerProvider = new MinecraftRenderBackend.MinecraftCommandQueue(queue);
    //#else
    //$$     RenderBackend.VertexConsumerProvider vertexConsumerProvider = new MinecraftRenderBackend.VertexConsumerProvider(buffers);
    //#endif
    //#else
    private void renderLeftArm(AbstractClientPlayer player, CallbackInfo ci) {
        UMatrixStack matrixStack = new UMatrixStack();
        RenderBackend.VertexConsumerProvider vertexConsumerProvider = new MinecraftRenderBackend.VertexConsumerProvider();
        int light = ((int) OpenGlHelper.lastBrightnessX) | (((int) OpenGlHelper.lastBrightnessY) << 16);
    //#endif
        //#if MC<12102
        getMainModel().isChild = false;
        //#endif
        CosmeticsRenderState cState = new CosmeticsRenderState.Live(player);
        // render only cosmetics connected to LEFT_ARM and no other EnumParts
        essentialModelRenderer.render(matrixStack, vertexConsumerProvider, null, cState, light, EnumSet.of(EnumPart.LEFT_ARM), false, false);
        EmoteWheel.isPlayerArmRendering = false;
    }

    @Inject(method = "renderRightArm", at = @At("HEAD"))
    private void isRenderingRightArm(CallbackInfo ci) {
        EmoteWheel.isPlayerArmRendering = true;
        resetTransforms();
    }

    @Inject(method = "renderRightArm", at = @At("RETURN"))
    //#if MC>=11400
    //#if MC>=12102
    //#if MC>=12109
    //$$ private void renderRightArm(MatrixStack vMatrixStack, OrderedRenderCommandQueue queue, int light, Identifier skinTexture, boolean sleeveVisible, CallbackInfo ci) {
    //#else
    //$$ private void renderRightArm(MatrixStack vMatrixStack, VertexConsumerProvider buffers, int light, Identifier skinTexture, boolean sleeveVisible, CallbackInfo ci) {
    //#endif
    //$$     AbstractClientPlayerEntity player = MinecraftClient.getInstance().player;
    //$$     if (player == null) return;
    //#else
    //$$ private void renderRightArm(MatrixStack vMatrixStack, IRenderTypeBuffer buffers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
    //#endif
    //$$     UMatrixStack matrixStack = new UMatrixStack(vMatrixStack);
    //#if MC>=12109
    //$$     RenderBackend.CommandQueue vertexConsumerProvider = new MinecraftRenderBackend.MinecraftCommandQueue(queue);
    //#else
    //$$     RenderBackend.VertexConsumerProvider vertexConsumerProvider = new MinecraftRenderBackend.VertexConsumerProvider(buffers);
    //#endif
    //#else
    private void renderRightArm(AbstractClientPlayer player, CallbackInfo ci) {
        UMatrixStack matrixStack = new UMatrixStack();
        RenderBackend.VertexConsumerProvider vertexConsumerProvider = new MinecraftRenderBackend.VertexConsumerProvider();
        int light = ((int) OpenGlHelper.lastBrightnessX) | (((int) OpenGlHelper.lastBrightnessY) << 16);
    //#endif
        //#if MC<12102
        getMainModel().isChild = false;
        //#endif
        CosmeticsRenderState cState = new CosmeticsRenderState.Live(player);
        // render only cosmetics connected to RIGHT_ARM and no other EnumParts
        essentialModelRenderer.render(matrixStack, vertexConsumerProvider, null, cState, light, EnumSet.of(EnumPart.RIGHT_ARM), false, false);
        EmoteWheel.isPlayerArmRendering = false;
    }

    @Override
    public Mat4 essential$getTransform(AbstractClientPlayer player, float yaw, float partialTicks) {
        // FIXME 1.21.2 this is getting unreasonably expensive
        //#if MC>=11400
        //$$ MatrixStack stack = new MatrixStack();
        //#if MC>=12102
        //$$ PlayerEntityRenderState state = this.createRenderState();
        //$$
        //$$ // store and override the player yaw values with our own
        //$$ var oldYaw = player.bodyYaw;
        //$$ var oldPrevYaw = player.prevBodyYaw;
        //$$ player.bodyYaw = yaw;
        //$$ player.prevBodyYaw = yaw;
        //$$
        //$$ // process the transforms with our modified yaw
        //$$ this.updateRenderState(player, state, partialTicks);
        //$$ this.setupTransforms(state, stack, state.bodyYaw, state.baseScale);
        //$$
        //$$ // With 1.21.2 now properly rendering all body parts relative to the root part, some third party mods might now
        //$$ // apply transformations to the root part, we need to account for that.
        //$$
        //$$ // To do this properly we first need to replicate all the transforms applied by minecraft between the vanilla
        //$$ // setupTransforms() call and the vanilla model rendering call (which is where the root transforms apply).
        //$$ // These values come from, and are applied by, the LivingEntityRenderer's render method & PlayerRenderer
        //$$ stack.scale(-1.0F, -1.0F, 1.0F);
        //$$ stack.scale(0.9375f, 0.9375f, 0.9375f);
        //$$ stack.translate(0.0f, -1.501f, 0.0f);
        //$$
        //$$ // Now we apply the root transform to the correctly prepared stack
        //$$ getModel().setAngles(state);
        //$$ getModel().getRootPart().rotate(stack);
        //$$
        //$$ // Now we revert those vanilla transforms except for the ones from the root part.
        //$$ stack.translate(0.0f, 1.501f, 0.0f);
        //$$ stack.scale(1 / 0.9375f, 1 / 0.9375f, 1 / 0.9375f);
        //$$ stack.scale(-1.0F, -1.0F, 1.0F);
        //$$
        //$$ // reset the yaw to the original value
        //$$ player.bodyYaw = oldYaw;
        //$$ player.prevBodyYaw = oldPrevYaw;
        //#else
        //$$ this.applyRotations(
        //$$     player, stack, player.ticksExisted + partialTicks, yaw, partialTicks
            //#if MC>=12006
            //$$ , player.getScale()
            //#endif
        //$$ );
        //#endif
        //$$ return GLUtil.INSTANCE.glGetMatrix(stack, 1f);
        //#else
        return new UMatrixStack().runReplacingGlobalState(() -> {
            this.applyRotations(player, player.ticksExisted + partialTicks, yaw, partialTicks);
            return GLUtil.INSTANCE.glGetMatrix(1f);
        });
        //#endif
    }

    @Unique
    private void resetTransforms(){
        //#if MC>=12102
        //$$ // This will reset any transforms that are not reset to defaults by the vanilla code before arm rendering
        //$$ // Only required for 1.21.2+ as emote scaling starts to affect the first person arms then, all other transforms are reset by the vanilla code
        //$$ ModelBipedUtil.resetPose(this.getModel());
        //#endif
    }

    //#if MC<12102
    @Shadow protected abstract void applyRotations(
        AbstractClientPlayer player,
        //#if MC>=11400
        //$$ MatrixStack stack,
        //#endif
        float lifeTime,
        float yaw,
        float partialTicks
        //#if MC>=12006
        //$$ , float scale
        //#endif
    );
    //#endif

    public MixinRenderPlayer() { super(null, null, 0f); }
}
