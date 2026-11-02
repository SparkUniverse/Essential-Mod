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
package gg.essential.mixins.transformers.events;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import gg.essential.Essential;
import gg.essential.event.gui.GuiDrawScreenEvent;
import gg.essential.universal.UMatrixStack;
import gg.essential.util.UDrawContext;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC>=12000
//$$ import net.minecraft.client.gui.DrawContext;
//#endif

//#if MC >= 11400
//$$ import com.mojang.blaze3d.matrix.MatrixStack;
//#endif

@Mixin(
    //#if MC >= 26.2
    //$$ value = net.minecraft.client.gui.Gui.class,
    //#else
    value = net.minecraft.client.renderer.EntityRenderer.class,
    //#endif
    priority = 1500
)
public abstract class Mixin_GuiDrawScreenEvent_Priority {
    //#if MC >= 26.2
    //$$ private static final String RENDER = "extractRenderState";
    //#elseif MC >= 26.1
    //$$ private static final String RENDER = "extractGui";
    //#else
    private static final String RENDER = "updateCameraAndRender";
    //#endif

    //#if NEOFORGE
    //#if MC >= 26.2
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/neoforged/neoforge/client/ClientHooks;extractScreen(Lnet/minecraft/client/gui/screens/Screen;Ljava/util/Stack;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";
    //#elseif MC >= 26.1
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";
    //#else
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V";
    //#endif
    //#elseif FORGE
    //#if MC >= 26.2
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/minecraft/client/gui/Gui;drawScreen(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";
    //#elseif MC >= 26.1
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";
    //#elseif MC >= 1.20
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V";
    //#elseif MC>=11700
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V";
    //#elseif MC>=11400
    //$$ private static final String FORGE_DRAW_SCREEN = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screen/Screen;Lcom/mojang/blaze3d/matrix/MatrixStack;IIF)V";
    //#else
    private static final String FORGE_DRAW_SCREEN = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V";
    //#endif
    //#else
    //#if MC>=12000
    //$$ private static final String VANILLA_DRAW_SCREEN = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V";
    //#elseif MC>=11903
    //$$ private static final String VANILLA_DRAW_SCREEN = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/client/util/math/MatrixStack;IIF)V";
    //#else
    //$$ private static final String VANILLA_DRAW_SCREEN = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V";
    //#endif
    //#endif

    private static final String OPTIFINE_DRAW_SCREEN = "Lnet/optifine/reflect/Reflector;ForgeHooksClient_drawScreen:Lnet/optifine/reflect/ReflectorMethod;";
    private static final String OPTIFINE_CALL_VOID = "Lnet/optifine/reflect/Reflector;callVoid(Lnet/optifine/reflect/ReflectorMethod;[Ljava/lang/Object;)V";

    @Unique
    private GuiScreen screen;

    @Unique
    private UDrawContext drawContext;

    @Unique
    private int mouseX;

    @Unique
    private int mouseY;

    @Unique
    private float partialTicks;

    // (Ab)using `WrapWithCondition` to record the arguments because individual `ModifyArg`s can break due to
    // https://github.com/SpongePowered/Mixin/issues/544
    // and `ModifyArgs` are broken on modern Forge in general: https://github.com/SpongePowered/Mixin/issues/584

    @Group(name = "capture_args", min = 1)
    //#if FORGELIKE
    @WrapWithCondition(method = RENDER, at = @At(value = "INVOKE", target = FORGE_DRAW_SCREEN, remap = false))
    //#else
    //$$ @WrapWithCondition(method = RENDER, at = @At(value = "INVOKE", target = VANILLA_DRAW_SCREEN))
    //#endif
    private boolean recordArgsForPriorityDrawScreenEvent(
        //#if FORGE && MC >= 26.2
        //$$ net.minecraft.client.gui.Gui self,
        //#else
        GuiScreen screen,
        //#endif
        //#if NEOFORGE && MC >= 26.2
        //$$ java.util.Stack<Screen> backgroundLayers,
        //#endif
        //#if MC>=12000
        //$$ DrawContext context,
        //#elseif MC>=11400
        //$$ MatrixStack vMatrixStack,
        //#endif
        int mouseX,
        int mouseY,
        float partialTicks
    ) {
        //#if FORGE && MC >= 26.2
        //$$ this.screen = self.screen();
        //#else
        this.screen = screen;
        //#endif
        //#if MC>=12000
        //$$ this.drawContext = new UDrawContext(context, new UMatrixStack(context.getMatrices()));
        //#elseif MC>=11400
        //$$ this.drawContext = new UDrawContext(new UMatrixStack(vMatrixStack));
        //#else
        this.drawContext = new UDrawContext(new UMatrixStack());
        //#endif
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        return true;
    }

    @Group(name = "capture_args", min = 1)
    @Dynamic("OptiFine calls ForgeHooksClient.drawScreen via reflection")
    @ModifyArg(
        method = "updateCameraAndRender",
        at = @At(value = "INVOKE", target = OPTIFINE_CALL_VOID, ordinal = 0, remap = false)
    )
    private Object[] recordArgsOptiFine(Object[] args) {
        if (!this.isDrawScreenCall) return args;

        //#if MC>=11400
        //$$ if (args.length < 5) return args;
        //#else
        if (args.length < 4) return args;
        //#endif

        int i = -1;
        if (args[++i] instanceof GuiScreen) {
            this.screen = (GuiScreen) args[i];
        }
        //#if MC>=12000
        //$$ if (args[++i] instanceof DrawContext) {
        //$$     this.drawContext = new UDrawContext((DrawContext) args[i], new UMatrixStack(((DrawContext) args[i]).getMatrices()));
        //$$ }
        //#elseif MC>=11400
        //$$ if (args[++i] instanceof MatrixStack) {
        //$$     this.drawContext = new UDrawContext(new UMatrixStack((MatrixStack) args[i]));
        //$$ }
        //#else
        this.drawContext = new UDrawContext(new UMatrixStack());
        //#endif
        if (args[++i] instanceof Integer) {
            this.mouseX = (Integer) args[i];
        }
        if (args[++i] instanceof Integer) {
            this.mouseY = (Integer) args[i];
        }
        if (args[++i] instanceof Float) {
            this.partialTicks = (Float) args[i];
        }
        return args;
    }

    @Group(name = "post_event", min = 1)
    //#if FORGELIKE
    @Inject(method = RENDER, at = @At(value = "INVOKE", target = FORGE_DRAW_SCREEN, remap = false, shift = At.Shift.AFTER))
    //#else
    //$$ @Inject(method = RENDER, at = @At(value = "INVOKE", target = VANILLA_DRAW_SCREEN, shift = At.Shift.AFTER))
    //#endif
    private void updateCameraAndRender(CallbackInfo ci) {
        Essential.EVENT_BUS.post(new GuiDrawScreenEvent.Priority(this.screen, this.drawContext, this.mouseX, this.mouseY, this.partialTicks, true));
    }

    @Group(name = "post_event", min = 1)
    @Dynamic("OptiFine calls ForgeHooksClient.drawScreen via reflection")
    @Inject(
        method = "updateCameraAndRender",
        at = @At(value = "INVOKE", target = OPTIFINE_CALL_VOID, ordinal = 0, remap = false, shift = At.Shift.AFTER)
    )
    private void drawScreenEventOptiFine(CallbackInfo ci) {
        if (this.isDrawScreenCall) this.isDrawScreenCall = false;
        else return;
        Essential.EVENT_BUS.post(new GuiDrawScreenEvent.Priority(this.screen, this.drawContext, this.mouseX, this.mouseY, this.partialTicks, true));
    }


    // @Slice throws if the target does not exist, and given it may very well not exist if either Optifine isn't
    // installed or Patcher optimized away its reflection, we cannot use it here. So instead we "slice" at runtime.
    @Unique
    private boolean isDrawScreenCall;

    @Dynamic("OptiFine calls ForgeHooksClient.drawScreen via reflection")
    @Inject(method = "updateCameraAndRender", at = @At(value = "FIELD", target = OPTIFINE_DRAW_SCREEN, ordinal = 0, remap = false), require = 0, expect = 0)
    private void toggleSubsequentInjectors(CallbackInfo ci) {
        this.isDrawScreenCall = true;
    }
}
