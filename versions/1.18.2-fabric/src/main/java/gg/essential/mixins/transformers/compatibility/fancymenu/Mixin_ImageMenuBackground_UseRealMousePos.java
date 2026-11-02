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
package gg.essential.mixins.transformers.compatibility.fancymenu;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.keksuccino.fancymenu.customization.background.backgrounds.image.ImageMenuBackground;
import gg.essential.gui.overlay.OverlayManagerImpl;
import gg.essential.universal.UMouse;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Pseudo;

// Similar to [Mixin_AbstractElement_CacheRealMousePos] except this background object implements its own separate parallax effect

@Pseudo
@Mixin(ImageMenuBackground.class) // https://github.com/Keksuccino/FancyMenu/blob/d30f69792a6e7d27c7e60e07971df6b99367d940/common/src/main/java/de/keksuccino/fancymenu/customization/background/backgrounds/image/ImageMenuBackground.java
public class Mixin_ImageMenuBackground_UseRealMousePos {

    // FIXME remap bug: should be able to remap this because it overrides a remapped method
    //#if MC >= 26.1
    //$$ @WrapMethod(method = "extractRenderState"
    //#else
    @WrapMethod(method = "render"
    //#endif
            //#if MC < 1.20
            , remap = false
            //#endif
    )
    private void useRealMousePos(
            //#if MC >= 1.20
            //$$ net.minecraft.client.gui.DrawContext graphics,
            //#else
            de.keksuccino.fancymenu.util.rendering.gui.GuiGraphics graphics,
            //#endif
            int mouseX, int mouseY, float partial, Operation<Void> original
    ) {
        // No-op if we haven't overridden the mouse pos
        if (!OverlayManagerImpl.isOverridingMousePos()) {
            original.call(graphics, mouseX, mouseY, partial);
            return;
        }

        OverlayManagerImpl.withRealMousePos(()-> {
            original.call(graphics, (int) UMouse.Scaled.getX(), (int) UMouse.Scaled.getY(), partial);
            return Unit.INSTANCE;
        });
    }

}
