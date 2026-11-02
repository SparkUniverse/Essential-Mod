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
package gg.essential.asm;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;

import java.util.List;

public class MixinTransformerWrapper implements IMixinTransformer {
    private final IMixinTransformer delegate;
    private final List<Transformer> extraTransformers;

    public MixinTransformerWrapper(IMixinTransformer delegate, List<Transformer> extraTransformers) {
        this.delegate = delegate;
        this.extraTransformers = extraTransformers;
    }

    //#if FABRIC || NEOFORGE && MC >= 1.21.11
    //$$ @Override
    //$$ public boolean couldTransformClass(MixinEnvironment environment, String name) {
    //$$     return true;
    //$$ }
    //#endif

    // KnotClassDelegate exclusively uses this method, we don't need to bother intercepting any other ones
    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] bytes) {
        bytes = delegate.transformClassBytes(name, transformedName, bytes);
        for (Transformer transformer : extraTransformers) {
            bytes = transformer.transform(name, transformedName, bytes);
        };
        return bytes;
    }

    @Override
    public void audit(MixinEnvironment environment) {
        delegate.audit(environment);
    }

    @Override
    public List<String> reload(String mixinClass, ClassNode classNode) {
        return delegate.reload(mixinClass, classNode);
    }

    @Override
    public boolean computeFramesForClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return delegate.computeFramesForClass(environment, name, classNode);
    }

    @Override
    public byte[] transformClass(MixinEnvironment environment, String name, byte[] bytes) {
        return delegate.transformClass(environment, name, bytes);
    }

    @Override
    public boolean transformClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return delegate.transformClass(environment, name, classNode);
    }

    @Override
    public byte[] generateClass(MixinEnvironment environment, String name) {
        return delegate.generateClass(environment, name);
    }

    @Override
    public boolean generateClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return delegate.generateClass(environment, name, classNode);
    }

    @Override
    public IExtensionRegistry getExtensions() {
        return delegate.getExtensions();
    }

    public interface Transformer
        //#if MC<11400
        extends net.minecraft.launchwrapper.IClassTransformer
        //#endif
    {
        byte[] transform(String name, String transformedName, byte[] bytes);
    }
}
