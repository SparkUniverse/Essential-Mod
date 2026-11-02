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
package gg.essential.asm.compat;

import gg.essential.asm.EssentialTransformer;
import gg.essential.mixins.IntegrationTestsPlugin;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.util.Annotations;

import java.util.Collections;
import java.util.Set;

/**
 * ImmediatelyFast optimizes text rendering for various things, including the nametag since 26.2.
 * This optimization breaks our nametag rendering, resulting in a crash.
 * This transformer undoes their caching by rewriting their mixin's method to directly call the original method,
 * instead of doing the caching, effectively undoing their mixin.
 * <a href="https://github.com/RaphiMC/ImmediatelyFast/blob/52b3a58a15b9693d2782f1c4bf91a062624e697f/common/src/main/java/net/raphimc/immediatelyfast/injection/mixins/fast_text_lookup/MixinNameTagFeatureRenderer_GlyphRenderer.java">Link to Mixin</a>
 */
public class ImmediatelyFastTransformer implements EssentialTransformer {

    //#if MC >= 26.3
    //$$ private static final String rendererClassName = "TextFeatureRenderer";
    //#else
    private static final String rendererClassName = "NameTagFeatureRenderer";
    //#endif

    private static final String featureRenderer = "net/minecraft/client/renderer/feature/" + rendererClassName;
    private static final String renderTypeDescriptor = "Lnet/minecraft/client/renderer/rendertype/RenderType;";
    private static final String vertexConsumerDescriptor = "Lcom/mojang/blaze3d/vertex/VertexConsumer;";

    private static final String getVertexBuilderName = "getVertexBuilder";
    private static final String getVertexBuilderDescriptor = "(" + renderTypeDescriptor + ")" + vertexConsumerDescriptor;
    private static final String handlerDescriptor = "(L" + featureRenderer + ";" + renderTypeDescriptor + ")" + vertexConsumerDescriptor;

    private static final String mixinMergedDescriptor = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    // During integration tests, we instead override the dummy mixin, copied over from ImmediatelyFast, which, if not removed will error.
    private static final String immediatelyFastMixinName =
            IntegrationTestsPlugin.ENABLED
                    ? "gg.essential.mixins.transformers.compatibility.immediatelyfast.Mixin_ImmediatelyFastGlyphRendererCompatIntegrationTest"
                    : "net.raphimc.immediatelyfast.injection.mixins.fast_text_lookup.Mixin" + rendererClassName + "_GlyphRenderer";

    @Override
    public Set<String> getTargets() {
        return Collections.singleton("net.minecraft.client.renderer.feature." + rendererClassName + "$GlyphRenderer");
    }

    @Override
    public void preApply(ClassNode classNode) {}

    @Override
    public void postApply(ClassNode classNode) {
        for (MethodNode methodNode : classNode.methods) {
            if (!isImmediatelyFastGlyphRendererMixin(methodNode)) continue;

            methodNode.instructions = callGetVertexBuilder();
            methodNode.tryCatchBlocks.clear();
            if (methodNode.localVariables != null) methodNode.localVariables.clear();
            methodNode.maxStack = 2;
        }
    }

    private static boolean isImmediatelyFastGlyphRendererMixin(MethodNode methodNode) {
        if (methodNode.visibleAnnotations == null || !methodNode.name.contains("reduceGetVertexBuilderCalls")) return false;
        // Bail out if the handler signature ever changes, rather than emitting bytecode that fails verification
        if (!handlerDescriptor.equals(methodNode.desc) || (methodNode.access & Opcodes.ACC_STATIC) != 0) return false;
        for (AnnotationNode annotation : methodNode.visibleAnnotations) {
            if (!annotation.desc.equals(mixinMergedDescriptor)) continue;
            if (immediatelyFastMixinName.equals(Annotations.getValue(annotation, "mixin"))) {
                return true;
            }
        }
        return false;
    }

    private InsnList callGetVertexBuilder() {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new VarInsnNode(Opcodes.ALOAD, 2));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, featureRenderer, getVertexBuilderName, getVertexBuilderDescriptor, false));
        list.add(new InsnNode(Opcodes.ARETURN));
        return list;
    }

}
