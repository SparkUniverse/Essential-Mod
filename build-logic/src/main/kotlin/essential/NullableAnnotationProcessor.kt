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
package essential

import net.fabricmc.loom.api.processor.MinecraftJarProcessor
import net.fabricmc.loom.api.processor.ProcessorContext
import net.fabricmc.loom.api.processor.SpecContext
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.TypeReference
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.FileSystems
import java.nio.file.Path
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

abstract class NullableAnnotationProcessor @Inject constructor(
    private val name: String,
    private val spec: Spec,
) : MinecraftJarProcessor<NullableAnnotationProcessor.Spec> {
    override fun buildSpec(context: SpecContext): Spec? = spec

    override fun processJar(path: Path, spec: Spec, context: ProcessorContext) {
        if (spec.isEmpty) {
            return
        }

        FileSystems.newFileSystem(path, null as ClassLoader?).use { fileSystem ->
            for ((className, classSpec) in spec.classes) {
                val classFile = fileSystem.getPath(className.replace('.', '/') + ".class")
                val classBytes = classFile.readBytes()
                val classReader = ClassReader(classBytes)
                val classWriter = ClassWriter(classReader, 0)
                classReader.accept(makeClassVisitor(classWriter, classFile, classSpec), 0)

                classFile.writeBytes(classWriter.toByteArray())
            }
        }
    }

    private fun makeClassVisitor(classWriter: ClassWriter, classFile: Path, classSpec: ClassSpec): ClassVisitor {
        return object : ClassVisitor(Opcodes.ASM9, classWriter) {
            val fields = classSpec.fields.toMutableMap()
            val methods = classSpec.methods.toMutableMap()

            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                val fieldVisitor = super.visitField(access, name, descriptor, signature, value)

                val fieldSpec = fields.remove(name)
                if (fieldSpec != null) {
                    fieldVisitor.visitTypeAnnotation(
                        TypeReference.newTypeReference(TypeReference.FIELD).value,
                        fieldSpec.typePath?.typePath,
                        NULLABLE,
                        true,
                    ).visitEnd()
                }

                return fieldVisitor
            }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                val methodSpec = methods.remove(name)

                var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (methodSpec == null) return methodVisitor

                // Need to wrap the method visitor in a custom class, otherwise the ClassWriter will just copy the
                // source method, even if we add additional annotations.
                methodVisitor = object : MethodVisitor(Opcodes.ASM9, methodVisitor) {}

                if (methodSpec.returnType != null) {
                    methodVisitor.visitTypeAnnotation(
                        TypeReference.newTypeReference(TypeReference.METHOD_RETURN).value,
                        methodSpec.returnType.typePath?.typePath,
                        NULLABLE,
                        true,
                    ).visitEnd()
                }

                if (methodSpec.parameters.isNotEmpty()) {
                    val parameters = methodSpec.parameters.toMutableMap()
                    methodVisitor = object : MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        private val paramNames = mutableListOf<String?>()
                        override fun visitParameter(name: String?, access: Int) {
                            super.visitParameter(name, access)

                            if (access and Opcodes.ACC_SYNTHETIC != 0) {
                                return // synthetic parameters do not have annotations
                            }

                            paramNames.add(name)
                        }

                        private val localNames = mutableMapOf<Int, String>()
                        override fun visitLocalVariable(name: String, descriptor: String, signature: String?, start: Label, end: Label, index: Int) {
                            super.visitLocalVariable(name, descriptor, signature, start, end, index)

                            if (name == "this" || "$" in name) {
                                return // synthetic parameters do not have annotations
                            }

                            localNames[index] = name
                        }

                        override fun visitEnd() {
                            super.visitEnd()

                            // It seems like the proper parameter names don't make it through NeoForge's patching, so we'll
                            // instead try to infer them from the local variable names.
                            var paramNames: List<String?> = paramNames
                            if (paramNames.isEmpty()) {
                                paramNames = localNames.entries.sortedBy { it.key }.take(Type.getArgumentCount(descriptor)).map { it.value }
                            }

                            for ((name, paramSpec) in parameters) {
                                val parameterIndex = paramNames.indexOf(name)
                                if (parameterIndex == -1) {
                                    throw IOException("Failed to find parameters $name in method $name in $classFile (found: $paramNames)")
                                }
                                visitTypeAnnotation(
                                    TypeReference.newFormalParameterReference(parameterIndex).value,
                                    paramSpec.typePath?.typePath,
                                    NULLABLE,
                                    true,
                                ).visitEnd()
                            }
                        }
                    }
                }

                if (methodSpec.generics.isNotEmpty()) {
                    if (signature == null) {
                        throw IOException("Method $name in $classFile does not appear to have generics.")
                    }
                    val typeParameters = parseTypeParameters(signature)
                    for ((generic, genericSpec) in methodSpec.generics) {
                        methodVisitor.visitTypeAnnotation(
                            TypeReference.newTypeParameterBoundReference(
                                TypeReference.METHOD_TYPE_PARAMETER_BOUND,
                                typeParameters.indexOf(generic).takeUnless { it == -1 }
                                    ?: throw IOException("Failed to find generic `$generic` in method $name in $classFile"),
                                0,
                            ).value,
                            genericSpec.typePath?.typePath,
                            NULLABLE,
                            true,
                        ).visitEnd()
                    }
                }

                return methodVisitor
            }

            override fun visitEnd() {
                super.visitEnd()

                if (fields.isNotEmpty()) {
                    throw IOException("Failed to find fields $fields in $classFile")
                }
                if (methods.isNotEmpty()) {
                    throw IOException("Failed to find methods ${methods.keys} in $classFile")
                }
            }
        }
    }

    override fun getName(): String = name

    private fun parseTypeParameters(signature: String): List<String> {
        val result = mutableListOf<String>()
        SignatureReader(signature).accept(object : SignatureVisitor(Opcodes.ASM9) {
            override fun visitFormalTypeParameter(name: String) {
                result.add(name)
            }
        })
        return result
    }

    @Suppress("EqualsOrHashCode") // see comment on `hashCode` implementation
    data class Spec(
        val classes: Map<String, ClassSpec>,
        val implVersion: Int = 3, // increment to flush cache
    ) : MinecraftJarProcessor.Spec {
        val isEmpty: Boolean = classes.values.all { classSpec ->
            classSpec.fields.isEmpty() && classSpec.methods.values.all { methodSpec ->
                methodSpec.returnType == null && methodSpec.parameters.isEmpty() && methodSpec.generics.isEmpty()
            }
        }

        // Loom uses the hashCode to determine whether the spec has changed.
        // But since the default hashCode contract only requires that equal object must produce the same hashCode
        // but not that unequal objects must produce a different hashCode (at least as far as possible), I don't really
        // trust it for this purpose.
        // We'll instead use the first few bytes of a proper hash on the toString representation.
        // Much slower, but much less likely to not be different when it should be.
        override fun hashCode(): Int {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(toString().encodeToByteArray())
            return ByteBuffer.wrap(digest).int
        }
    }

    data class ClassSpec(
        val fields: Map<String, TargetSpec>,
        val methods: Map<String, MethodSpec>,
    )

    data class MethodSpec(
        val returnType: TargetSpec?,
        val parameters: Map<String, TargetSpec>,
        val generics: Map<String, TargetSpec>,
    )

    data class TargetSpec(
        val typePath: TypePathW?,
    )

    /** Wrapper around [TypePath] because that doesn't implement equals/hashCode. */
    class TypePathW(val typePath: TypePath) {
        // Bit inefficient to just use `toString`, but good enough for our use-case
        override fun equals(other: Any?): Boolean = typePath.toString() == (other as? TypePath)?.toString()
        override fun hashCode(): Int = typePath.toString().hashCode()
    }

    interface SpecBuilder {
        fun cls(name: String, configure: ClassSpecBuilder.() -> Unit)
        operator fun String.invoke(configure: ClassSpecBuilder.() -> Unit) = cls(this, configure)
    }
    interface ClassSpecBuilder {
        fun field(name: String, typePath: TypePath? = null)
        fun method(name: String, configure: MethodSpecBuilder.() -> Unit)
        fun method(
            name: String,
            vararg parameters: String,
            configure: MethodSpecBuilder.() -> Unit = {},
        ) = method(name) {
            for (parameter in parameters) {
                parameter(parameter)
            }
            configure()
        }
    }
    interface MethodSpecBuilder {
        fun returnType(typePath: TypePath? = null)
        fun parameter(name: String, typePath: TypePath? = null)
        fun generic(name: String, typePath: TypePath? = null)
    }

    private class SpecBuilderImpl : SpecBuilder {
        val classes = mutableMapOf<String, ClassSpec>()
        override fun cls(name: String, configure: ClassSpecBuilder.() -> Unit) {
            val builder = ClassSpecBuilderImpl()
            builder.configure()
            classes[name] = ClassSpec(builder.fields, builder.methods)
        }
    }
    private class ClassSpecBuilderImpl : ClassSpecBuilder {
        val fields = mutableMapOf<String, TargetSpec>()
        val methods = mutableMapOf<String, MethodSpec>()

        override fun field(name: String, typePath: TypePath?) {
            fields[name] = TargetSpec(typePath?.let { TypePathW(it) })
        }

        override fun method(name: String, configure: MethodSpecBuilder.() -> Unit) {
            val builder = MethodSpecBuilderImpl()
            builder.configure()
            methods[name] = MethodSpec(
                builder.returnValue,
                builder.parameters,
                builder.generics,
            )
        }
    }
    private class MethodSpecBuilderImpl : MethodSpecBuilder {
        var returnValue: TargetSpec? = null
        val parameters = mutableMapOf<String, TargetSpec>()
        val generics = mutableMapOf<String, TargetSpec>()

        override fun returnType(typePath: TypePath?) {
            returnValue = TargetSpec(typePath?.let { TypePathW(it) })
        }

        override fun parameter(name: String, typePath: TypePath?) {
            parameters[name] = TargetSpec(typePath?.let { TypePathW(it) })
        }

        override fun generic(name: String, typePath: TypePath?) {
            generics[name] = TargetSpec(typePath?.let { TypePathW(it) })
        }
    }

    companion object {
        private const val NULLABLE = "Lorg/jspecify/annotations/Nullable;"

        fun spec(configure: SpecBuilder.() -> Unit): Spec {
            val builder = SpecBuilderImpl()
            builder.configure()
            return Spec(builder.classes)
        }
    }
}
