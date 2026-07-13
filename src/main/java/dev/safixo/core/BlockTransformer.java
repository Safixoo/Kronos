package dev.safixo.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashSet;

import static org.objectweb.asm.Opcodes.*;

public class BlockTransformer implements IClassTransformer {
	static HashSet<String> BLOCK_TYPES;

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		byte[][] reference = new byte[][] {basicClass};

		checkDevEnvironment();

		startBlockCollection(reference);
		redirectAsyncBlocksCalls(reference);

		return reference[0];
	}

	static void redirectAsyncBlocksCalls(byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode node = (MethodNode) classNode.methods.get(i);

			InsnList inns = node.instructions;
			AbstractInsnNode insn = inns.getFirst();

			while (insn != null) {
				int opcode = insn.getOpcode();

				if (opcode == GETFIELD || opcode == PUTFIELD) {
					FieldInsnNode m = (FieldInsnNode) insn;

					if (m.name.startsWith("m") && BLOCK_TYPES.contains(m.owner) && isField(m.name)) {
						String name;
						String desc;

						if (opcode == GETFIELD) {
							name = "get" + getField(m.name);
							desc = KronosTransformer.IN_DEV
								? "(Lnet/minecraft/block/Block;)D"
								: "(Laqz;)D";
						} else {
							name = "set" + getField(m.name);
							desc = KronosTransformer.IN_DEV
								? "(Lnet/minecraft/block/Block;D)V"
								: "(Laqz;D)V";
						}

						node.instructions.set(m, new MethodInsnNode(INVOKESTATIC, KronosTransformer.ASYNC_BLOCK_HOOK, name, desc));
					}

				}
				insn = insn.getNext();
			}
		}

		classNode.accept(writer);
		basicClass[0] = writer.toByteArray();
	}

	public static boolean isField(String name) {
		switch (name) {
			case "minX":
			case "minY":
			case "minZ":
			case "maxX":
			case "maxY":
			case "maxZ": return true;

			default:
				return false;
		}
	}

	public static String getField(String name) {
		switch (name) {
			case "minX": return "MinX";
			case "minY": return "MinY";
			case "minZ": return "MinZ";
			case "maxX": return "MaxX";
			case "maxY": return "MaxY";
			case "maxZ": return "MaxZ";
			default:
				return null;
		}
	}

	public static byte[] setupAsyncFields(ClassNode classNode) {
		addField(classNode, "minXMT");
		addField(classNode, "minYMT");
		addField(classNode, "minZMT");

		addField(classNode, "maxXMT");
		addField(classNode, "maxYMT");
		addField(classNode, "maxZMT");

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		return writer.toByteArray();
	}
	private static final Double NULL_DOUBLE = (double) 0;

	private static void addField(ClassNode classNode, String name) {
		classNode.visitField(Opcodes.ACC_PUBLIC, name, "D", null, NULL_DOUBLE);
	}

	static void startBlockCollection(byte[][] basicClass) {
		if (BLOCK_TYPES == null) {
			BLOCK_TYPES = new HashSet<>();
			BLOCK_TYPES.add(IN_DEV ? "net/minecraft/block/Block" : "aqz");
		}

		ClassReader reader = new ClassReader(basicClass[0]);
		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		if (classNode.name.equals(IN_DEV ? "net/minecraft/block/Block" : "aqz")){
			basicClass[0] = setupAsyncFields(classNode);
		}

		if (BLOCK_TYPES.contains(classNode.superName)) {
			BLOCK_TYPES.add(classNode.name);
		}
	}

	static boolean CHECKED_FOR_DEV;
	static boolean IN_DEV;

	static void checkDevEnvironment() {
		if (CHECKED_FOR_DEV) {
			return;
		}

		CHECKED_FOR_DEV = true;
		Object deObf = Launch.blackboard.get("fml.deobfuscatedEnvironment");

		IN_DEV = deObf != null && (Boolean) deObf;
	}
}
