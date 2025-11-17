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
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".class") && !entry.isDirectory) {
                        try {
                            jar.getInputStream(entry).use { input ->
                                parseClass(input, entry.name, nodes)
                            }
                        } catch (e: Exception) {
                            log.debug("Failed to parse class {}: {}", entry.name, e.message)
                        }
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
        private var className: String? = null
        private var packageName: String? = null
        private var classModifiers: Set<String> = emptySet()
        private var classAnnotations: List<String> = emptyList()
        private var outerClass: String? = null

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            className = name.replace('/', '.')
            packageName = className?.substringBeforeLast('.')
            classModifiers = extractModifiers(access)
            outerClass = null
        }

        override fun visitOuterClass(owner: String?, name: String?, desc: String?) {
            outerClass = owner?.replace('/', '.')
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            val annotationFqn = Type.getType(descriptor).className
            classAnnotations = classAnnotations + annotationFqn
            return null
        }

        override fun visitEnd() {
            val fqn = className ?: return
            val name = fqn.substringAfterLast('.')
            val kind = determineClassKind(classModifiers)

            nodes.add(
                RawLibraryNode(
                    fqn = fqn,
                    name = name,
                    packageName = packageName,
                    kind = kind,
                    lang = Lang.java, // TODO: можно определить по kotlin-metadata
                    filePath = filePath,
                    signature = null,
                    annotations = classAnnotations,
                    modifiers = classModifiers,
                    parentFqn = outerClass,
                    meta = emptyMap(),
                ),
            )
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?,
        ): FieldVisitor? {
            val fieldFqn = "${className}.$name"
            val fieldType = Type.getType(descriptor).className
            val modifiers = extractModifiers(access)

            nodes.add(
                RawLibraryNode(
                    fqn = fieldFqn,
                    name = name,
                    packageName = packageName,
                    kind = NodeKind.FIELD,
                    lang = Lang.java,
                    filePath = filePath,
                    signature = fieldType,
                    annotations = emptyList(), // TODO: можно добавить парсинг аннотаций полей
                    modifiers = modifiers,
                    parentFqn = className,
                    meta = emptyMap(),
                ),
            )
            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            // Пропускаем синтетические методы и конструкторы (если нужно)
            if (name == "<clinit>") return null

            val methodFqn = "${className}.$name"
            val methodSignature = buildMethodSignature(name, descriptor)
            val modifiers = extractModifiers(access)

            nodes.add(
                RawLibraryNode(
                    fqn = methodFqn,
                    name = name,
                    packageName = packageName,
                    kind = if (name == "<init>") NodeKind.METHOD else NodeKind.METHOD,
                    lang = Lang.java,
                    filePath = filePath,
                    signature = methodSignature,
                    annotations = emptyList(), // TODO: можно добавить парсинг аннотаций методов
                    modifiers = modifiers,
                    parentFqn = className,
                    meta = emptyMap(),
                ),
            )
            return null
        }

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

        private fun determineClassKind(modifiers: Set<String>): NodeKind {
            return when {
                modifiers.contains("enum") -> NodeKind.ENUM
                modifiers.contains("interface") -> NodeKind.INTERFACE
                else -> NodeKind.CLASS
            }
        }

        private fun buildMethodSignature(name: String, descriptor: String): String {
            val methodType = Type.getMethodType(descriptor)
            val params = methodType.argumentTypes.joinToString(", ") { it.className }
            val returnType = methodType.returnType.className
            return "$name($params): $returnType"
        }
    }
}

