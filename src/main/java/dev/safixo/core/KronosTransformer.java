package dev.safixo.core;

import org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class KronosTransformer implements IClassTransformer {
	static final String RENDER_GLOBAL_HOOK = "dev/safixo/core/hooks/RenderGlobalHook";
	
	static final String RENDER_GLOBAL_PATH = "net.minecraft.client.renderer.RenderGlobal";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		byte[][] reference = new byte[1][];
		reference[0] = basicClass;

		if (transformedName.equals(RENDER_GLOBAL_PATH)) {
			replaceClassMethod(RENDER_GLOBAL_HOOK, "loadRenderers", "func_72712_a", reference);
			replaceClassMethod(RENDER_GLOBAL_HOOK, "clipRenderersByFrustum", "", reference);

			replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockForUpdate", "", reference);
			replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockForRenderUpdate", "", reference);
			replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockRangeForRenderUpdate", "", reference);
			replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlocksForUpdate", "", reference);
		}

		return reference[0];
	}

	static void replaceClassMethod(String hookPath, String methodName, String runtimeName, byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		String classDesc = Type.getObjectType(classNode.name).getDescriptor();

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			if (method.name.equals(methodName) || method.name.equals(runtimeName)) {
				method.localVariables = null;
				method.instructions.clear();

				Type[] types = Type.getArgumentTypes(method.desc);

				InsnList inject = new InsnList();
				inject.add(new VarInsnNode(ALOAD, 0));

				StringBuilder newDesc = new StringBuilder();
				newDesc.append("(" + classDesc);

				int argumentOffset = 1;

				// Process al argument types.
				for (int j = 0; j < types.length; j++) {
					argumentOffset = processType(newDesc, types[j], inject, argumentOffset);
				}

				newDesc.append(")V");

				inject.add(new MethodInsnNode(INVOKESTATIC,
					hookPath,
					methodName,
					newDesc.toString()
				));
				inject.add(new InsnNode(RETURN));
				method.instructions.add(inject);
			}
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		basicClass[0] = writer.toByteArray();
	}

	static int processType(StringBuilder newDesc, Type type, InsnList inject, int offset) {
		newDesc.append(type.getDescriptor());

		switch (type.getSort()) {
			case Type.FLOAT:
				inject.add(new VarInsnNode(FLOAD, offset));
				offset += 1;
				break;
			case Type.LONG:
				inject.add(new VarInsnNode(LLOAD, offset));
				offset += 2;
				break;
			case Type.DOUBLE:
				inject.add(new VarInsnNode(DLOAD, offset));
				offset += 2;
				break;
			case Type.ARRAY:
			case Type.OBJECT:
				inject.add(new VarInsnNode(ALOAD, offset));
				offset += 1;
				break;
			default: // the rest of possibilities.
				inject.add(new VarInsnNode(ILOAD, offset));
				offset += 1;
				break;
		}

		return offset;
	}
}
