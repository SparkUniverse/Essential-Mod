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
 * Marker for classes/members which are only accessed via reflection and as such may otherwise appear as unused to the
 * feature-flags-processor.
 * 
 * [.value] denotes the class/method that is accessing the annotated element:
 * `"SomeClass.someMethod"` or just `"SomeClass"` for the constructor / static initializer<br></br>
 * This value must **not** be fully qualified. The processor does not resolves references in code, it operates on the
 * surface-level tokens only, so it does not know the fully qualified names either.
 * It **must** however include the parent class for inner classes: `"Outer.Inner.method"`.
 * 
 * Note that the referenced element need not necessarily be the actual caller.
 * Annotating an element with this annotation is for the purposes of dead code elimination equivalent to inserting a
 * regular class/field/method reference into the target referenced by [.value] (regardless of whether such a
 * change would actually compile).
 * As such, it may sometimes be more convenient to intentionally reference another piece of code if that piece is more
 * indicative of whether the annotated element is unused.
 * E.g. Instead of referencing Lwjgl3Loader in NativeImageReaderImpl, we reference NativeImageReader because that is
 * more narrow, and whenever it exists, so should the impl.
 * 
 * Also note that it will insert a reference to the annotated element only. If it is contained within an otherwise
 * unused class, that class must be annotated as well.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class AccessedViaReflection(val value: String)
