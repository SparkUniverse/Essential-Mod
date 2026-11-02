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
package gg.essential.mixins;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import gg.essential.asm.EssentialTransformer;

import gg.essential.asm.GlErrorCheckingTransformer;
import gg.essential.asm.MixinTransformerWrapper;
import gg.essential.data.VersionInfo;
import gg.essential.mixins.injection.points.AfterInvokeInInit;
import gg.essential.mixins.injection.points.BeforeConstantInInit;
import gg.essential.mixins.injection.points.BeforeFieldAccessInInit;
import gg.essential.mixins.injection.points.BeforeInvokeInInit;
import gg.essential.util.MixinUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

//#if FABRIC
//$$ import java.lang.management.ManagementFactory;
//$$ import java.lang.management.RuntimeMXBean;
//#endif

//#if MC>=11600
//$$ import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
//#else
import net.minecraft.launchwrapper.IClassTransformer;
//#endif

public class Plugin implements IMixinConfigPlugin {
    static { IntegrationTestsPlugin.registerUncaughtExceptionHandler(); }
    private static final Logger logger = LogManager.getLogger("Essential Logger - Plugin");

    private final boolean inOurDevEnv = Boolean.getBoolean("essential.feature.dev_only");

    // Cannot just check "Config", it's too early for that
    private final boolean hasOptifine = hasClass("optifine.OptiFineForgeTweaker") || hasClass("me.modmuss50.optifabric.mod.OptifineInjector");

    private final EssentialTransformer[] transformers = new EssentialTransformer[]{
            //#if MC>=11600 && MC<11700
            //$$ new gg.essential.asm.compat.RandomPatchesTransformer(),
            //#endif
            //#if MC >= 26.2
            //$$ new gg.essential.asm.compat.ImmediatelyFastTransformer(),
            //#endif
    };

    static {
        VersionInfo info = new VersionInfo();

        logger.info(
            "Starting Essential v" + info.getEssentialVersion()
                + " (#" + info.getEssentialCommit() + ")"
                + " [" + info.getEssentialBranch() + "]"
        );

        // Forge already prints out this info, Fabric doesn't
        //#if FABRIC
        //$$ RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        //$$ List<String> arguments = runtimeMxBean.getInputArguments();
        //$$
        //$$ logger.info(
        //$$     "Java: " + System.getProperty("java.vm.name")
        //$$         + " (v" + System.getProperty("java.version") + ")"
        //$$         + " by " + System.getProperty("java.vm.vendor")
        //$$         + " (" + System.getProperty("java.vendor") + ")"
        //$$ );
        //$$
        //$$ logger.info("Java Path: " + System.getProperty("sun.boot.library.path"));
        //$$ logger.info("Java Info: " + System.getProperty("java.vm.info"));
        //$$
        //$$ logger.info("JVM Arguments: \n  - " + String.join("\n  - ", arguments));
        //$$ logger.info(
        //$$     "OS: " + System.getProperty("os.name")
        //$$         + " (v" + System.getProperty("os.version") + ")"
        //$$         + " (Arch: " + System.getProperty("os.arch") + ")"
        //$$ );
        //#endif

        //#if FORGE && MC >= 1.16 && MC < 1.21.6
        //$$ gg.essential.util.WorkaroundBrokenEventSubclassTransformer.apply();
        //#endif


        // Note: These are not properly supported! Use only for debugging!
        List<MixinTransformerWrapper.Transformer> globalTransformers = new ArrayList<>();
        if (Boolean.getBoolean("essential.gl_debug.asm")) {
            globalTransformers.add(new GlErrorCheckingTransformer());
        }
        if (!globalTransformers.isEmpty()) {
            try {
                registerGlobalTransformers(globalTransformers);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private final Multimap<String, EssentialTransformer> transformerMap = ArrayListMultimap.create();

    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();

        MixinUtils.registerInjectionPoint(AfterInvokeInInit.class);
        MixinUtils.registerInjectionPoint(BeforeConstantInInit.class);
        MixinUtils.registerInjectionPoint(BeforeFieldAccessInInit.class);
        MixinUtils.registerInjectionPoint(BeforeInvokeInInit.class);

        for (EssentialTransformer transformer : transformers) {
            for (String target : transformer.getTargets()) {
                transformerMap.put(target, transformer);
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // We don't need to apply anything to the dummy target.
        if (targetClassName.equals("gg.essential.mixins.DummyTarget")) {
            return false;
        }

        //#if FORGE
        if (mixinClassName.endsWith("Mixin_FixKeyBindingCategorySortingNPE")) {
            return false; // Forge already includes this patch
        }
        //#endif

        //#if FABRIC
        //$$ if (mixinClassName.endsWith("Mixin_PreventExtraPlayerLoadEvent")) {
        //$$    return false;
        //$$ }
        //#endif

        // RP throws a NPE in `KeyBinding.conflicts` if called before RP is initialized: https://i.johni0702.de/3VZU8.png
        // https://github.com/TheRandomLabs/RandomPatches/blob/acec31ba25abd8d288d9103c6dd650de9ae6cbd2/src/main/java/com/therandomlabs/randompatches/mixin/client/keybindings/KeyBindingMixin.java#L46
        if (mixinClassName.endsWith("Mixin_UnbindConflictingKeybinds") && hasClass("com.therandomlabs.randompatches.RandomPatches")) {
            return false;
        }

        if (!hasOptifine && mixinClassName.endsWith("_Optifine")) {
            return false;
        }
        if (hasOptifine && mixinClassName.endsWith("_Zoom")) {
            return false;
        }
        if (mixinClassName.contains("compatibility")) {
            // Most of these target classes are unlikely to be present, so let's avoid making Mixin unhappy
            if (!hasClass(targetClassName)) {
                return false;
            }
        }

        //#if MC>=12006 && MC<12102
        //$$ // mixin is already dummy targeted outside this range
        //$$ if (mixinClassName.endsWith("Mixin_FancyMainMenu_3_2_1_GuiDrawScreenEvent")) {
        //$$     // underlying issue was fixed in FancyMenu v3.4.0, but we still need to apply this mixin for 1.20.6 and 1.21, as they do not have v3.4.0+
        //$$     // this is a check just in case v3.4.0 ever gets backported to 1.20.6 or 1.21, so we don't apply this mixin in that case.
        //$$     // there were no classes removed in 3.4.0 that could be used to detect the affected version so lets check the mixin directly
        //$$     return testClass("de.keksuccino.fancymenu.mixin.mixins.common.client.MixinTitleScreen",
        //$$             clazz -> {
        //$$                 for (MethodNode method : clazz.methods) {
        //$$                     if (method.name.equals("wrap_super_render_in_render_FancyMenu")) {
        //$$                         // this is the mixin that cancels the super.render call in TitleScreen, which is the cause of the issue
        //$$                         return true;
        //$$                     }
        //$$                 }
        //$$                 return false;
        //$$             }
        //$$     );
        //$$ }
        //#endif

        // All versions 1.21.5+ are v3.4.0+ of fancy menu so we no longer want this mixin to be capable of silently failing
        //#if MC >= 1.18.2 && MC < 1.21.5
        //$$ if (mixinClassName.endsWith("Mixin_AbstractElement_CacheRealMousePos")) {
        //$$     // Fields, and reason for mixin existing, added in v3.4.0
        //$$     return testClass("de.keksuccino.fancymenu.customization.element.AbstractElement",
        //$$             clazz -> {
        //$$                 for (org.objectweb.asm.tree.FieldNode field : clazz.fields) {
        //$$                     if ("cachedMouseX".equals(field.name)) {
        //$$                         return true;
        //$$                     }
        //$$                 }
        //$$                 return false;
        //$$             }
        //$$     );
        //$$ }
        //#endif

        // Due to changes in FancyMenu, we need a different Mixin for v2.14.10 and above. This version can be identified
        // by the ScreenBackgroundRenderedEvent class.
        if (mixinClassName.endsWith("Mixin_FancyMainMenu_GuiDrawScreenEvent_Pre") && hasClass("de.keksuccino.fancymenu.events.ScreenBackgroundRenderedEvent")) {
            return false;
        }
        if (mixinClassName.endsWith("Mixin_FancyMainMenu_2_14_10_GuiDrawScreenEvent_Pre") && !hasClass("de.keksuccino.fancymenu.events.ScreenBackgroundRenderedEvent")) {
            return false;
        }

        // Patcher has this fix too, and applying it twice would break it again
        if (mixinClassName.contains("Mixin_FixSelfieNameplateOrientation") && hasClass("club.sk1er.patcher.Patcher")) {
            return false;
        }

        // We want this mixin to only apply during integration tests. See mixin for more info.
        if (mixinClassName.contains("ImmediatelyFastGlyphRendererCompatIntegrationTest")) {
            return IntegrationTestsPlugin.ENABLED;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (inOurDevEnv || IntegrationTestsPlugin.ENABLED) {
            IntegrationTestsPlugin.enableInjectionCounting(mixinInfo);
        }

        for (EssentialTransformer transformer : transformerMap.get(targetClassName)) {
            transformer.preApply(targetClass);
        }

        //#if MC==11605 && FABRIC || MC==12004 && FABRIC
        //$$ if (inOurDevEnv && mixinClassName.endsWith("Mixin_RenderParticleSystemOfClientWorld")) {
        //$$     // Workaround for a mixin """feature""" where in a development environment it'll strip the descriptor from
        //$$     // target references and thereby match methods that were never meant to be matched.
        //$$     // See https://github.com/FabricMC/Mixin/blob/b07a8b2b1548a0b94ec9b9cb3f2ed3bae7c90de9/src/main/java/org/spongepowered/asm/mixin/injection/struct/InjectionInfo.java#L597-L601
        //$$     // In our case, we have two injectors, one for vanilla and one for optifine (via optifabric), differentiated
        //$$     // by their arguments. The second one usually won't match in dev because OF isn't installed there, and so
        //$$     // Mixin will helpfully re-try it without the arguments and because the name of the two targets happens
        //$$     // to match in 1.16, it'll find the vanilla target and subsequently complain that our method signature is
        //$$     // wrong.
        //$$     // To work around this, we'll add a dummy for the OF method, so it finds something on the first pass:
        //$$     createDummyIfMissing(targetClass, "renderParticles", "(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/render/Frustum;)V");
        //$$ }
        //#endif
        //#if MC<11400
        if (inOurDevEnv && (mixinClassName.endsWith("Mixin_SoundSystemExt_SoundManager") || mixinClassName.endsWith("Mixin_UpdateWhilePaused"))) {
            // Similar issue as above, one injector is for Forge pre-2646, one for 2646+.
            createDummyIfMissing(targetClass, "setListener", "(Lnet/minecraft/entity/Entity;F)V");
        }
        //#endif
    }

    private void createDummyIfMissing(ClassNode targetClass, String name, String desc) {
        for (MethodNode method : targetClass.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return;
            }
        }
        MethodNode dummyMethod = new MethodNode(0, name, desc, null, null);
        dummyMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        targetClass.methods.add(dummyMethod);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (EssentialTransformer transformer : transformerMap.get(targetClassName)) {
            transformer.postApply(targetClass);
        }
    }

    private static void registerGlobalTransformers(List<MixinTransformerWrapper.Transformer> extraTransformers) throws ReflectiveOperationException {
        //#if FABRIC
        //$$ ClassLoader classLoader = Plugin.class.getClassLoader();
        //$$ Field delegateField = classLoader.getClass().getDeclaredField("delegate");
        //$$ delegateField.setAccessible(true);
        //$$ Object delegate = delegateField.get(classLoader);
        //$$ Field transformerField = delegate.getClass().getDeclaredField("mixinTransformer");
        //$$ transformerField.setAccessible(true);
        //$$ IMixinTransformer transformer = (IMixinTransformer) transformerField.get(delegate);
        //$$ transformer = new MixinTransformerWrapper(transformer, extraTransformers);
        //$$ transformerField.set(delegate, transformer);
        //#elseif MC>=11600
        //$$ throw new UnsupportedOperationException(); // not yet implemented for modern forge
        //#else
        ClassLoader classLoader = net.minecraft.launchwrapper.Launch.classLoader;
        Field transformersField = classLoader.getClass().getDeclaredField("transformers");
        transformersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<IClassTransformer> transformers = (List<IClassTransformer>) transformersField.get(classLoader);
        transformers.addAll(extraTransformers);
        //#endif
    }

    static boolean hasClass(String name) {
        return testClass(name, cls -> true);
    }

    static boolean testClass(String className, Predicate<ClassNode> test) {
        try {
            ClassNode classNode = MixinService.getService().getBytecodeProvider().getClassNode(className);
            return test.test(classNode);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            logger.error("Exception when testing class {}: ", className, e);
            return false;
        }
    }
}
