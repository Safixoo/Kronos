package dev.safixo.core.hooks;

import dev.safixo.client.util.data.PrimitivesFlags;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

@SuppressWarnings("unused")
public class LongHashMapHook {
	public static byte[] rewriteHashMapClass() {
		boolean inDev = PrimitivesFlags.DEV_ENVIRONMENT;

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

		cw.visit(V1_7, ACC_PUBLIC, "net/minecraft/util/LongHashMap", null,
			"java/lang/Object", null);

		// private final FastLongHashMap map;
		cw.visitField(ACC_PRIVATE | ACC_FINAL, "map",
			"Ldev/safixo/client/util/FastLongHashMap;",
			null,
			null).visitEnd();

		MethodVisitor mv;

		// Constructor
		mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();

		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");

		mv.visitVarInsn(ALOAD, 0);
		mv.visitTypeInsn(NEW, "dev/safixo/client/util/FastLongHashMap");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL,
			"dev/safixo/client/util/FastLongHashMap",
			"<init>",
			"()V");

		mv.visitFieldInsn(PUTFIELD,
			"net/minecraft/util/LongHashMap",
			"map",
			"Ldev/safixo/client/util/FastLongHashMap;");

		mv.visitInsn(RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		// public int getNumHashElements()
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "getNumHashElements" : "func_76162_a", "()I", null, null);
		mv.visitCode();

		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD,
			"net/minecraft/util/LongHashMap",
			"map",
			"Ldev/safixo/client/util/FastLongHashMap;");

		mv.visitMethodInsn(INVOKEVIRTUAL,
			"dev/safixo/client/util/FastLongHashMap",
			"getSize",
			"()I");

		mv.visitInsn(IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		// public Object getValueByKey(long key)
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "getValueByKey" : "func_76164_a", "(J)Ljava/lang/Object;", null, null);
		mv.visitCode();

		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD,
			"net/minecraft/util/LongHashMap",
			"map",
			"Ldev/safixo/client/util/FastLongHashMap;");

		mv.visitVarInsn(LLOAD, 1);

		mv.visitMethodInsn(INVOKEVIRTUAL,
			"dev/safixo/client/util/FastLongHashMap",
			"get",
			"(J)Ljava/lang/Object;");

		mv.visitInsn(ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		// public boolean containsItem(long key)
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "containsItem" : "func_76161_b", "(J)Z", null, null);
		mv.visitCode();

		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD,
			"net/minecraft/util/LongHashMap",
			"map",
			"Ldev/safixo/client/util/FastLongHashMap;");

		mv.visitVarInsn(LLOAD, 1);

		mv.visitMethodInsn(INVOKEVIRTUAL,
			"dev/safixo/client/util/FastLongHashMap",
			"contains",
			"(J)Z");

		mv.visitInsn(IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		// public void add(long key, Object obj)
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "add" : "func_76163_a", "(JLjava/lang/Object;)V", null, null);
		mv.visitCode();

		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD,
			"net/minecraft/util/LongHashMap",
			"map",
			"Ldev/safixo/client/util/FastLongHashMap;");

		mv.visitVarInsn(LLOAD, 1);
		mv.visitVarInsn(ALOAD, 3);

		mv.visitMethodInsn(INVOKEVIRTUAL,
			"dev/safixo/client/util/FastLongHashMap",
			"put",
			"(JLjava/lang/Object;)V");

		mv.visitInsn(RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		// public Object remove(long key)
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "remove" : "func_76159_d", "(J)Ljava/lang/Object;", null, null);
		mv.visitCode();

		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD,
			"net/minecraft/util/LongHashMap",
			"map",
			"Ldev/safixo/client/util/FastLongHashMap;");

		mv.visitVarInsn(LLOAD, 1);

		mv.visitMethodInsn(INVOKEVIRTUAL,
			"dev/safixo/client/util/FastLongHashMap",
			"remove",
			"(J)Ljava/lang/Object;");

		mv.visitInsn(ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}
}
