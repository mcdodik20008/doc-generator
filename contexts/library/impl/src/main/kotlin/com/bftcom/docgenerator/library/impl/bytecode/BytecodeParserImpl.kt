package com.bftcom.docgenerator.library.impl.bytecode

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.library.api.BytecodeParser
import com.bftcom.docgenerator.library.api.RawLibraryNode
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile
import java.util.zip.ZipEntry

/**
 * Реализация парсера байткода через ASM.
 * Извлекает классы, методы, поля, аннотации из .class файлов в jar.
 */
@Component
class BytecodeParserImpl : BytecodeParser {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun parseJar(jarFile: File): List<RawLibraryNode> {
        if (!jarFile.exists() || !jarFile.name.endsWith(".jar", ignoreCase = true)) {
            log.debug("Skipping non-jar file: {}", jarFile.name)
            return emptyList()
        }

        val nodes = mutableListOf<RawLibraryNode>()

        try {
            JarFile(jarFile).use { jar ->
                // 1. Собираем все .class-entries, чтобы знать общее количество
                val classEntries = mutableListOf<ZipEntry>()
                val entriesEnum = jar.entries()
                while (entriesEnum.hasMoreElements()) {
                    val entry = entriesEnum.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        classEntries += entry
                    }
                }

                val totalClasses = classEntries.size
                if (totalClasses == 0) {
                    log.debug("Jar {} has no .class entries, skipping", jarFile.name)
                    return emptyList()
                }

                log.info(
                    "Start parsing jar {} ({} class files)",
                    jarFile.name,
                    totalClasses,
                )

                var processedClasses = 0
                var lastLoggedProgress = -1

                // 2. Парсим классы и логируем прогресс
                for (entry in classEntries) {
                    try {
                        jar.getInputStream(entry).use { input ->
                            parseClass(input, entry.name, nodes)
                        }
                    } catch (e: Exception) {
                        log.debug(
                            "Failed to parse class {} in jar {}: {}",
                            entry.name,
                            jarFile.name,
                            e.message,
                        )
                    }

                    processedClasses++

                    val progress = (processedClasses * 100) / totalClasses
                    // Логируем, только когда пересекаем новый порог 10%, чтобы не спамить
                    if (progress % 10 == 0 && progress != lastLoggedProgress) {
                        log.info(
                            "Parsing jar {}: {}% ({}/{})",
                            jarFile.name,
                            progress,
                            processedClasses,
                            totalClasses,
                        )
                        lastLoggedProgress = progress
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to parse jar {}: {}", jarFile.name, e.message)
        }

        log.debug("Parsed {} nodes from jar: {}", nodes.size, jarFile.name)
        return nodes
    }

    private fun parseClass(
        input: InputStream,
        filePath: String,
        nodes: MutableList<RawLibraryNode>,
    ) {
        val reader = ClassReader(input.readBytes())
        val visitor = LibraryClassVisitor(filePath, nodes)
        reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    private class LibraryClassVisitor(
        private val filePath: String,
        private val nodes: MutableList<RawLibraryNode>,
    ) : ClassVisitor(Opcodes.ASM9) {
        private var internalName: String? = null // org/example/MyClass (с /)
        private var classFqn: String? = null // org.example.MyClass
        private var packageName: String? = null
        private var simpleName: String? = null
        private var classModifiers: Set<String> = emptySet()
        private var classAnnotations: List<String> = emptyList()
        private var outerClassFqn: String? = null
        private var superClassFqn: String? = null
        private var interfaceFqns: List<String> = emptyList()
        private var classSignature: String? = null

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            internalName = name
            classFqn = name.replace('/', '.')
            simpleName = classFqn!!.substringAfterLast('.')
            packageName = classFqn!!.substringBeforeLast('.')

            classModifiers = extractModifiers(access)
            classAnnotations = emptyList()
            classSignature = signature

            superClassFqn = superName?.replace('/', '.')
            interfaceFqns = interfaces?.map { it.replace('/', '.') } ?: emptyList()
            outerClassFqn = null
        }

        override fun visitOuterClass(
            owner: String?,
            name: String?,
            desc: String?,
        ) {
            outerClassFqn = owner?.replace('/', '.')
        }

        override fun visitAnnotation(
            descriptor: String,
            visible: Boolean,
        ): AnnotationVisitor? {
            val annotationFqn = Type.getType(descriptor).className
            classAnnotations = classAnnotations + annotationFqn
            return null
        }

        override fun visitEnd() {
            val fqn = classFqn ?: return
            val name = simpleName ?: fqn.substringAfterLast('.')
            val kind = determineClassKind(classModifiers)

            val meta = mutableMapOf<String, Any>()

            classSignature?.let { meta["signature"] = it }
            superClassFqn?.let { meta["superClass"] = it }
            if (interfaceFqns.isNotEmpty()) {
                meta["interfaces"] = interfaceFqns
            }

            if (isCoroutineStateMachineClass(internalName ?: fqn, classModifiers)) {
                meta["synthetic_coroutine_class"] = true
            }

            nodes.add(
                RawLibraryNode(
                    fqn = fqn,
                    name = name,
                    packageName = packageName,
                    kind = kind,
                    lang = Lang.java, // байткод; язык исходника можно вычислять отдельно
                    filePath = filePath,
                    signature = null,
                    annotations = classAnnotations,
                    modifiers = classModifiers,
                    parentFqn = outerClassFqn,
                    meta = meta,
                ),
            )
        }
        // --- FIELD ---

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?,
        ): FieldVisitor? {
            val ownerFqn = classFqn ?: return null
            val fieldFqn = "$ownerFqn.$name"
            val fieldType = Type.getType(descriptor).className
            val modifiers = extractModifiers(access)

            val meta =
                mutableMapOf<String, Any>(
                    "descriptor" to descriptor,
                    "type" to fieldType,
                )

            signature?.let { meta["signature"] = it }
            value?.let { meta["initialValue"] = it }

            if (access and Opcodes.ACC_SYNTHETIC != 0) {
                meta["synthetic"] = true
            }

            nodes.add(
                RawLibraryNode(
                    fqn = fieldFqn,
                    name = name,
                    packageName = packageName,
                    kind = NodeKind.FIELD,
                    lang = Lang.java,
                    filePath = filePath,
                    signature = fieldType,
                    annotations = emptyList(), // при необходимости можно дописать парсинг аннотаций
                    modifiers = modifiers,
                    parentFqn = ownerFqn,
                    meta = meta,
                ),
            )

            // Код нам не нужен, аннотации полей пока игнорируем
            return null
        }

        // --- METHOD ---

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            // Статические инициализаторы неинтересны
            if (name == "<clinit>") return null

            val ownerFqn = classFqn ?: return null
            val methodFqn = "$ownerFqn.$name"
            val methodSignature = buildMethodSignature(name, descriptor)
            val modifiers = extractModifiers(access)

            val isSuspend = isSuspendMethod(descriptor)
            val isSyntheticHelper = isSyntheticCoroutineHelper(name, access)

            val meta =
                mutableMapOf<String, Any>(
                    "descriptor" to descriptor,
                )

            signature?.let { meta["signature"] = it }

            if (exceptions != null && exceptions.isNotEmpty()) {
                meta["exceptions"] = exceptions.map { it.replace('/', '.') }
            }

            if (isSuspend) {
                meta["kotlin_suspend"] = true
            }
            if (isSyntheticHelper) {
                meta["synthetic_coroutine_helper"] = true
            }
            if (access and Opcodes.ACC_BRIDGE != 0) {
                meta["bridge"] = true
            }
            if (access and Opcodes.ACC_SYNTHETIC != 0) {
                meta["synthetic"] = true
            }

            nodes.add(
                RawLibraryNode(
                    fqn = methodFqn,
                    name = name,
                    packageName = packageName,
                    kind = NodeKind.METHOD,
                    lang = Lang.java,
                    filePath = filePath,
                    signature = methodSignature,
                    annotations = emptyList(), // можно позже дописать парсинг аннотаций методов
                    modifiers = modifiers,
                    parentFqn = ownerFqn,
                    meta = meta,
                ),
            )

            // Тело метода нам не нужно (SKIP_CODE), аннотации пока не собираем
            return null
        }

        // --- Вспомогательные методы ---

        private fun extractModifiers(access: Int): Set<String> {
            val modifiers = mutableSetOf<String>()
            if (access and Opcodes.ACC_PUBLIC != 0) modifiers.add("public")
            if (access and Opcodes.ACC_PRIVATE != 0) modifiers.add("private")
            if (access and Opcodes.ACC_PROTECTED != 0) modifiers.add("protected")
            if (access and Opcodes.ACC_STATIC != 0) modifiers.add("static")
            if (access and Opcodes.ACC_FINAL != 0) modifiers.add("final")
            if (access and Opcodes.ACC_ABSTRACT != 0) modifiers.add("abstract")
            if (access and Opcodes.ACC_INTERFACE != 0) modifiers.add("interface")
            if (access and Opcodes.ACC_ENUM != 0) modifiers.add("enum")
            return modifiers
        }

        private fun determineClassKind(modifiers: Set<String>): NodeKind =
            when {
                "enum" in modifiers -> NodeKind.ENUM
                "interface" in modifiers -> NodeKind.INTERFACE
                else -> NodeKind.CLASS
            }

        private fun buildMethodSignature(
            name: String,
            descriptor: String,
        ): String {
            val methodType = Type.getMethodType(descriptor)
            val params = methodType.argumentTypes.joinToString(", ") { it.className }
            val returnType = methodType.returnType.className
            return "$name($params): $returnType"
        }

        private fun isSuspendMethod(descriptor: String): Boolean {
            val methodType = Type.getMethodType(descriptor)
            val argTypes = methodType.argumentTypes
            if (argTypes.isEmpty()) return false

            val lastArg = argTypes.last().className
            val returnType = methodType.returnType.className

            return lastArg == "kotlin.coroutines.Continuation" &&
                returnType == "java.lang.Object"
        }

        private fun isSyntheticCoroutineHelper(
            name: String,
            access: Int,
        ): Boolean {
            if (access and Opcodes.ACC_SYNTHETIC != 0) return true
            if ("\$default" in name) return true
            if ("\$SuspendLambda" in name) return true
            return false
        }

        private fun isCoroutineStateMachineClass(
            internalName: String,
            modifiers: Set<String>,
        ): Boolean {
            // Грубые, но полезные эвристики для корутинных генераций Kotlin
            if ("interface" in modifiers) return false
            if ("\$SuspendLambda" in internalName) return true
            if ("\$Continuation" in internalName) return true
            if ("\$WhenMappings" in internalName) return true
            if ("\$DefaultImpls" in internalName) return true
            return false
        }
    }
}
