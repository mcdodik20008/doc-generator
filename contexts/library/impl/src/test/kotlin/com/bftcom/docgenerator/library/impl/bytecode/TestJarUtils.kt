package com.bftcom.docgenerator.library.impl.bytecode

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal object TestJarUtils {
    fun writeJar(
        jarPath: Path,
        entries: Map<String, ByteArray>,
    ): File {
        Files.createDirectories(jarPath.parent)
        JarOutputStream(Files.newOutputStream(jarPath)).use { jos ->
            for ((name, bytes) in entries) {
                jos.putNextEntry(JarEntry(name))
                jos.write(bytes)
                jos.closeEntry()
            }
        }
        return jarPath.toFile()
    }

    fun emptyJar(jarPath: Path): File = writeJar(jarPath, emptyMap())

    fun generateTestClientClassBytes(internalName: String = "com/example/TestClient"): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)

        // Аннотация (класс может не существовать)
        cw.visitAnnotation("Lorg/springframework/stereotype/Service;", true).visitEnd()

        // public static final String CONST = "x";
        cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "CONST", Type.getDescriptor(String::class.java), null, "x").visitEnd()

        // default ctor
        run {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }

        // public void call()
        run {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "call", "()V", null, null)
            mv.visitCode()

            // StringBuilder sb = new StringBuilder().append("https://example.com").toString();
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
            mv.visitLdcInsn("https://example.com")
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false,
            )
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)

            // WebClient.get() (owner matches analyzer patterns)
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/springframework/web/reactive/function/client/WebClient",
                "get",
                "()V",
                false,
            )
            // URL const after clientType set -> should be detected
            mv.visitLdcInsn("/api")

            // retry/timeout
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/springframework/web/reactive/function/client/WebClient",
                "retry",
                "()V",
                false,
            )
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/springframework/web/reactive/function/client/WebClient",
                "timeout",
                "()V",
                false,
            )

            // KafkaProducer.send()
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/apache/kafka/clients/producer/KafkaProducer",
                "send",
                "()V",
                false,
            )
            mv.visitLdcInsn("topic1")

            // Camel RouteBuilder.from()
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "org/apache/camel/builder/RouteBuilder",
                "from",
                "()V",
                false,
            )
            mv.visitLdcInsn("kafka:topic2")

            // String.concat
            mv.visitLdcInsn("https://")
            mv.visitLdcInsn("example.org")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)

            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }

        cw.visitEnd()
        return cw.toByteArray()
    }
}

