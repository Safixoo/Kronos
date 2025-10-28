package dev.safixo.core.hooks;

import dev.safixo.client.util.data.BlocksFlags;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

public class LongHashMapHook {
	public static byte[] rewriteHashMapClass() {
		boolean inDev = BlocksFlags.DEV_ENVIRONMENT;

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

		cw.visit(V1_7, ACC_PUBLIC, "net/minecraft/util/LongHashMap", null,
			"java/lang/Object", null);

		// private final Long2ReferenceMap<Object> map = new Long2ReferenceOpenHashMap<>();
		cw.visitField(ACC_PRIVATE | ACC_FINAL, "map",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap<Ljava/lang/Object;>;",
			null).visitEnd();

		// Constructor
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);

		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		mv.visitVarInsn(ALOAD, 0);
		mv.visitTypeInsn(NEW, "it/unimi/dsi/fastutil/longs/Long2ReferenceOpenHashMap");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "it/unimi/dsi/fastutil/longs/Long2ReferenceOpenHashMap", "<init>", "()V");
		mv.visitFieldInsn(PUTFIELD, "net/minecraft/util/LongHashMap", "map",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
		mv.visitInsn(RETURN);
		mv.visitMaxs(3, 1);
		mv.visitEnd();

		// public int getNumHashElements() { return this.map.size(); }
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "getNumHashElements" : "func_76162_a", "()I", null, null);

		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, "net/minecraft/util/LongHashMap", "map",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
		mv.visitMethodInsn(INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/Long2ReferenceMap",
			"size", "()I");
		mv.visitInsn(IRETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();

		// public Object getValueByKey(long key) { return this.map.get(key); }
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "getValueByKey" : "func_76164_a", "(J)Ljava/lang/Object;", null, null);

		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, "net/minecraft/util/LongHashMap", "map",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
		mv.visitVarInsn(LLOAD, 1);
		mv.visitMethodInsn(INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/Long2ReferenceMap",
			"get", "(J)Ljava/lang/Object;");
		mv.visitInsn(ARETURN);
		mv.visitMaxs(3, 3);
		mv.visitEnd();

		// public boolean containsItem(long key) { return this.map.containsKey(key); }
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "containsItem" : "func_76161_b", "(J)Z", null, null);

		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, "net/minecraft/util/LongHashMap", "map",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
		mv.visitVarInsn(LLOAD, 1);
		mv.visitMethodInsn(INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/Long2ReferenceMap",
			"containsKey", "(J)Z");
		mv.visitInsn(IRETURN);
		mv.visitMaxs(3, 3);
		mv.visitEnd();

		// public void add(long key, Object obj) { this.map.put(key, obj); }
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "add" : "func_76163_a", "(JLjava/lang/Object;)V", null, null);

		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, "net/minecraft/util/LongHashMap", "map",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
		mv.visitVarInsn(LLOAD, 1);
		mv.visitVarInsn(ALOAD, 3);
		mv.visitMethodInsn(INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/Long2ReferenceMap",
			"put", "(JLjava/lang/Object;)Ljava/lang/Object;");
		mv.visitInsn(POP);
		mv.visitInsn(RETURN);
		mv.visitMaxs(4, 4);
		mv.visitEnd();

		// public Object remove(long key) { return this.map.remove(key); }
		mv = cw.visitMethod(ACC_PUBLIC, inDev ? "remove" : "func_76159_d", "(J)Ljava/lang/Object;", null, null);

		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, "net/minecraft/util/LongHashMap", "map",
			"Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
		mv.visitVarInsn(LLOAD, 1);
		mv.visitMethodInsn(INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/Long2ReferenceMap",
			"remove", "(J)Ljava/lang/Object;");
		mv.visitInsn(ARETURN);
		mv.visitMaxs(3, 3);
		mv.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}
}
