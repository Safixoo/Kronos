package dev.safixo.core;

import it.unimi.dsi.fastutil.objects.*;
import org.objectweb.asm.ClassWriter;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class KronosTransformer implements IClassTransformer {
	static final String RENDER_GLOBAL_HOOK = "dev/safixo/core/RenderGlobalHook";
	static final String RENDER_GLOBAL_PATH = "net.minecraft.client.renderer.RenderGlobal";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		byte[][] reference = new byte[1][];
		reference[0] = basicClass;

		if (transformedName.equals(RENDER_GLOBAL_PATH)) {
			replaceClassMethod(RENDER_GLOBAL_HOOK, "loadRenderers", "func_72712_a", reference);
		}

		return reference[0];
	}

	static void replaceClassMethod(String hookPath, String methodName, String runtimeName, byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			if (method.name.equals(methodName) || method.name.equals(runtimeName)) {
				method.localVariables = null;
				method.instructions.clear();

				InsnList inject = new InsnList();
				inject.add(new MethodInsnNode(INVOKESTATIC,
					hookPath,
					methodName,
					"()V"
				));
				inject.add(new InsnNode(RETURN));
				method.instructions.add(inject);
			}
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		basicClass[0] = writer.toByteArray();
	}
}
