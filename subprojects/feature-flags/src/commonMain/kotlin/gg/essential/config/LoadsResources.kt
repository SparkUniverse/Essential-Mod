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
package gg.essential.config

/**
 * Marks the method as accessing certain resource files, preventing these resources from being removed by the
 * feature-flags-processor if the marked method is called.
 *
 * [.value] must be a regular expression that matches the paths of all files used by the method. The path is
 * relative to `src/main/resources` and must begin with a leading `/`.
 * It may additionally contain placeholders of the form `%name%`. For each invocation of the method, these will
 * be replaced with the string literal passed in as the argument with the respective name (though keyword arguments are
 * not currently supported). If the argument passed in is not a string constant, the invocation is ignored.
 *
 * E.g.
 * ```
 * @LoadsResources("/assets/mine/%name%.png")
 * InputStream loadMyPng(String name) {
 *     return getClass().getResourceAsStream("/assets/mine/" + name + ".png");
 * }
 *
 * @LoadsResources("/assets/mine/icon/[0-9]+.png")
 * InputStream loadRandomIcon(int seed) {
 *     return getClass().getResourceAsStream("/assets/mine/icon" + randomUInt(seed) + ".png");
 * }
 *
 * void main() {
 *     loadMyPng("example");
 *     loadMyPng(SOME_CONSTANT); // this invocation will be ignored, only string literals are supported
 *     loadRandomIcon(randomInt()); // if the argument isn't used, it doesn't need to be a string constant; this works
 * }
 * ```
 */
@Target(
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class LoadsResources(val value: String) {
    /**
     * Adds LoadsResources behavior to certain builtin / third-party methods, taking advantage of the fact that the
     * feature-flags-processor only care about the simple name, not the fully qualified name nor the type.
     */
    @AccessedViaReflection("LoadsResources")
    class LoadsResourcesBuiltins {
        @AccessedViaReflection("LoadsResources")
        @LoadsResources("%path%")
        fun getResource(path: String) {
        }

        @AccessedViaReflection("LoadsResources")
        @LoadsResources("%path%")
        fun getResources(path: String) {
        }

        @AccessedViaReflection("LoadsResources")
        @LoadsResources("%path%")
        fun getResourceAsStream(path: String) {
        }

        @AccessedViaReflection("LoadsResources")
        @LoadsResources("/assets/%namespace%/%path%(\\.[a-z]+)?")
        fun Identifier(namespace: String, path: String) {
        }

        @AccessedViaReflection("LoadsResources")
        @LoadsResources("/assets/%namespace%/%path%(\\.[a-z]+)?")
        fun ResourceLocation(namespace: String, path: String) {
        }
    }
}
