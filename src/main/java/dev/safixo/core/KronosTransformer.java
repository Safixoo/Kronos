package dev.safixo.core;

import dev.safixo.core.hooks.TessellatorHook;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class KronosTransformer implements IClassTransformer {
	static final boolean DISABLE_INJECTION = false;

	static final String RENDER_GLOBAL_HOOK = "dev/safixo/core/hooks/RenderGlobalHook";
	static final String DEBUG_SCREEN_HOOK = "dev/safixo/core/hooks/DebugScreenHook";
	static final String FRUSTUM_HOOK = "dev/safixo/core/hooks/FrustumHook";
	static final String MINECRAFT_HOOK = "dev/safixo/core/hooks/MinecraftHook";

	static final String RENDER_GLOBAL_PATH = "net.minecraft.client.renderer.RenderGlobal";
	static final String CLIPPING_HELPER_IMPL_PATH = "net.minecraft.client.renderer.culling.ClippingHelperImpl";
	static final String CLIPPING_HELPER_PATH = "net.minecraft.client.renderer.culling.ClippingHelper";
	static final String FONT_RENDERER = "net.minecraft.client.gui.FontRenderer";
	static final String TESSELLATOR = "net.minecraft.client.renderer.Tessellator";
	static final String MINECRAFT = "net.minecraft.client.Minecraft";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		byte[][] reference = new byte[1][];
		reference[0] = basicClass;

		if (DISABLE_INJECTION) {
			return basicClass;
		}

		// Overwrites classes methods completely with a function call with the same
		// args and with the instance of the original class.
		switch (transformedName) {
			case RENDER_GLOBAL_PATH:
				replaceClassMethod(RENDER_GLOBAL_HOOK, "loadRenderers", "a", "()V", reference);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "clipRenderersByFrustum", "a", "(Lbft;F)V", reference);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "sortAndRender", "a", "(Lof;ID)I", reference);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "renderAllSortedRenderers", "", "", reference);

				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockForUpdate", "a", "(III)V", reference);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockForRenderUpdate", "b", "(III)V", reference);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockRangeForRenderUpdate", "a", "(IIIIII)V", reference);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlocksForUpdate", "b", "(IIIIII)V", reference);
				break;
			case FONT_RENDERER:
				replaceClassMethod(DEBUG_SCREEN_HOOK, "drawStringWithShadow", "a", "(Ljava/lang/String;III)I", reference);
				break;
			case CLIPPING_HELPER_IMPL_PATH:
				replaceClassMethod(FRUSTUM_HOOK, "init", "b", "()V", reference);
				break;
			case CLIPPING_HELPER_PATH:
				replaceClassMethodBoolRet(FRUSTUM_HOOK, "isBoxInFrustum", "b", "(DDDDDD)Z", reference);
				break;
			case MINECRAFT:
				replaceClassMethod(MINECRAFT_HOOK, "checkGLError", "c", "(Ljava/lang/String;)V", reference);
				break;
			case TESSELLATOR:
				TessellatorHook.redirectTessellatorFunc("addVertexWithUV", "a", "(DDDDD)V", reference);
				TessellatorHook.redirectTessellatorFunc("addVertex", "a", "(DDD)V", reference);
				TessellatorHook.redirectTessellatorFunc("setTextureUV", "a", "(DD)V", reference);
				TessellatorHook.redirectTessellatorFunc("setColorRGBA", "a", "(IIII)V", reference);
				TessellatorHook.redirectTessellatorFunc("setBrightness", "c", "(I)V", reference);
				TessellatorHook.redirectTessellatorFunc("disableColor", "c", "()V", reference);
				TessellatorHook.redirectTessellatorFunc("setTranslation", "b", "(DDD)V", reference);
				TessellatorHook.redirectTessellatorFunc("addTranslation", "c", "(FFF)V", reference);
				break;
		}

		return reference[0];
	}

	static void replaceClassMethod(String hookPath, String methodName, String runtimeName, String descriptor, byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		String classDesc = Type.getObjectType(classNode.name).getDescriptor();

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			if ((!method.name.equals(methodName) && ((!method.name.equals(runtimeName)) || !method.desc.contains(descriptor)))) {
				continue;
			}

			// Clear method's data and replace it with a direct call to the new function.
			method.localVariables = null;
			method.instructions.clear();

			Type[] types = Type.getArgumentTypes(method.desc);
			Type returnType = Type.getReturnType(method.desc);

			InsnList inject = new InsnList();
			StringBuilder newDesc = new StringBuilder();

			int argumentOffset;

			boolean isStatic = (method.access & ACC_STATIC) != 0;

			if (!isStatic) {
				inject.add(new VarInsnNode(ALOAD, 0));
				newDesc.append("(").append(classDesc);
				argumentOffset = 1;
			} else {
				newDesc.append("(");
				argumentOffset = 0;
			}

			// Process argument types.
			for (Type type : types) {
				argumentOffset = processType(newDesc, type, inject, argumentOffset);
			}

			newDesc.append(")V");

			inject.add(new MethodInsnNode(INVOKESTATIC,
				hookPath,
				methodName,
				newDesc.toString()
			));

			if (returnType.getSort() == Type.INT) {
				inject.add(new InsnNode(ICONST_0));
				inject.add(new InsnNode(IRETURN));
			} else {
				inject.add(new InsnNode(RETURN));
			}

			method.instructions.add(inject);
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		basicClass[0] = writer.toByteArray();
	}

	static void replaceClassMethodBoolRet(String hookPath, String methodName, String runtimeName, String descriptor, byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		String classDesc = Type.getObjectType(classNode.name).getDescriptor();

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			// Clear methods data and replace it with a direct call to the new function.
			if ((method.name.equals(methodName) || (method.name.equals(runtimeName)) && method.desc.contains(descriptor))) {
				// Clear method data.
				method.localVariables = null;
				method.instructions.clear();

				Type[] types = Type.getArgumentTypes(method.desc);

				InsnList inject = new InsnList();
				StringBuilder newDesc = new StringBuilder();

				int argumentOffset;

				boolean isStatic = (method.access & ACC_STATIC) != 0;

				if (!isStatic) {
					inject.add(new VarInsnNode(ALOAD, 0));
					newDesc.append("(").append(classDesc);
					argumentOffset = 1;
				} else {
					newDesc.append("(");
					argumentOffset = 0;
				}

				// Process argument types.
				for (int j = 0; j < types.length; j++) {
					argumentOffset = processType(newDesc, types[j], inject, argumentOffset);
				}

				newDesc.append(")Z");

				inject.add(new MethodInsnNode(INVOKESTATIC,
					hookPath,
					methodName,
					newDesc.toString()
				));
				inject.add(new InsnNode(IRETURN));

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
