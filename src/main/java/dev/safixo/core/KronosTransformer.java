package dev.safixo.core;

import dev.safixo.core.hooks.LongHashMapHook;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.util.HashSet;

import static org.objectweb.asm.Opcodes.*;

public class KronosTransformer implements IClassTransformer {
	static final boolean DISABLE_INJECTION = false;
	static final boolean GL_STATE_MANAGER = true;

	static final String RENDER_GLOBAL_HOOK = "dev/safixo/core/hooks/RenderGlobalHook";
	static final String DEBUG_SCREEN_HOOK = "dev/safixo/core/hooks/DebugScreenHook";
	static final String FONT_RENDERER_HOOK = "dev/safixo/core/hooks/FontRendererHook";
	static final String FRUSTUM_HOOK = "dev/safixo/core/hooks/FrustumHook";
	static final String MINECRAFT_HOOK = "dev/safixo/core/hooks/MinecraftHook";

	static final String RENDER_GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
	static final String ITEM_RENDERER = "net.minecraft.client.renderer.ItemRenderer";
	static final String CLIPPING_HELPER_IMPL = "net.minecraft.client.renderer.culling.ClippingHelperImpl";
	static final String CLIPPING_HELPER = "net.minecraft.client.renderer.culling.ClippingHelper";
	static final String FONT_RENDERER = "net.minecraft.client.gui.FontRenderer";
	static final String MINECRAFT = "net.minecraft.client.Minecraft";
	static final String ACTIVE_RENDER_INFO = "net.minecraft.client.renderer.ActiveRenderInfo";
	static final String BIOME_GEN_BASE = "net.minecraft.world.biome.BiomeGenBase";
	static final String LONG_HASH_MAP = "net.minecraft.util.LongHashMap";

	static HashSet<String> FUNCTION_NAMES;

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		byte[][] reference = new byte[1][];
		reference[0] = basicClass;

		if (DISABLE_INJECTION && !GL_STATE_MANAGER) {
			return basicClass;
		}

		if (GL_STATE_MANAGER && !name.contains("GlStateManager")) {
			fillStateMachineFunctions();
			redirectGlCalls(reference);
		}

		if (DISABLE_INJECTION && GL_STATE_MANAGER) {
			return basicClass;
		}

		// Overwrites classes methods completely with a function call with the same
		// args and with the instance of the original class.
		switch (transformedName) {
			case LONG_HASH_MAP:
				 return LongHashMapHook.rewriteHashMapClass();
			case RENDER_GLOBAL:
				// Redirect terrain rendering calls.
				replaceClassMethod(RENDER_GLOBAL_HOOK, "loadRenderers", "a", "()V", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "clipRenderersByFrustum", "a", "(Lbft;F)V", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "sortAndRender", "a", "(Lof;ID)I", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "updateRenderers", "a", "(Lof;Z)Z", reference, false);

				// NO-OP without OptiFine, it OptiFine is present it avoids a crash for invalid null terrain arrays.
				replaceClassMethod(RENDER_GLOBAL_HOOK, "renderAllSortedRenderers", "", "", reference, true);

				// Redirect most renderer updates.
				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockForUpdate", "a", "(III)V", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockForRenderUpdate", "b", "(III)V", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlockRangeForRenderUpdate", "a", "(IIIIII)V", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "markBlocksForUpdate", "b", "(IIIIII)V", reference, true);

				// Improved clouds.
				replaceClassMethod(RENDER_GLOBAL_HOOK, "renderCloudsFancy", "b", "(IIIIII)V", reference, true);
				break;
			case ITEM_RENDERER:
				// Batches all Tessellator calls to item renderer.
				replaceClassMethod(MINECRAFT_HOOK, "renderItemIn2D", "a", "(Ljava/lang/String;III)I", reference, false);
			case FONT_RENDERER:
				// Debug info.
				replaceClassMethod(DEBUG_SCREEN_HOOK, "drawStringWithShadow", "a", "(Ljava/lang/String;III)I", reference, false);

				// Redirect improved font-renderer draw loop.
				replaceClassMethod(FONT_RENDERER_HOOK, "renderString", "a", "(Ljava/lang/String;III)I", reference, false);
				break;
			case CLIPPING_HELPER_IMPL:
				// Redirect frustum setup to a more optimized and used one.
				replaceClassMethod(FRUSTUM_HOOK, "init", "b", "()V", reference, true);
				break;
			case CLIPPING_HELPER:
				// Uses the optimized implementation of frustum.
				replaceClassMethod(FRUSTUM_HOOK, "isBoxInFrustum", "b", "(DDDDDD)Z", reference, false);
				break;
			case MINECRAFT:
				// In many drivers in make stalls the GPU too soon in the tick loop.
				replaceClassMethod(MINECRAFT_HOOK, "checkGLError", "c", "(Ljava/lang/String;)V", reference, true);
				break;
			case BIOME_GEN_BASE:
				// TODO: Save a event instance per-thread to avoid creating events for every-biome fetched
				//  in meshing which is stupid slow thanks to Forge.
			case ACTIVE_RENDER_INFO:
				// TODO: this is not even verified to help even, to avoid this mess the best option is to backport
				//  Angelica's ASM GlStateManager or hooking to the matrices setup which is ugly.
		}

		return reference[0];
	}

	static void fillStateMachineFunctions() {
		if (FUNCTION_NAMES == null) {
			FUNCTION_NAMES = new HashSet<>();
		}

		FUNCTION_NAMES.add("glEnable");
		FUNCTION_NAMES.add("glDisable");
		FUNCTION_NAMES.add("glMatrixMode");
		FUNCTION_NAMES.add("glGetFloat");
		FUNCTION_NAMES.add("glLoadIdentity");
		FUNCTION_NAMES.add("glLoadMatrix");
		FUNCTION_NAMES.add("glMultMatrix");
		FUNCTION_NAMES.add("glScalef");
		FUNCTION_NAMES.add("glScaled");
		FUNCTION_NAMES.add("glRotatef");
		FUNCTION_NAMES.add("glRotated");
		FUNCTION_NAMES.add("glTranslatef");
		FUNCTION_NAMES.add("glBindFramebuffer");
		FUNCTION_NAMES.add("glFlush");
	}

	static void replaceClassMethod(String hookPath, String methodName, String runtimeName, String descriptor, byte[][] basicClass, boolean voidRet) {
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

			if (returnType.getSort() == Type.VOID || voidRet) {
				newDesc.append(")V");
			} else {
				newDesc.append(")").append(returnType.getDescriptor());
			}

			inject.add(new MethodInsnNode(INVOKESTATIC,
				hookPath,
				methodName,
				newDesc.toString()
			));

			if (returnType.getSort() == Type.INT || returnType.getSort() == Type.BOOLEAN) {
				if (voidRet) {
					inject.add(new InsnNode(ICONST_0));
				}

				inject.add(new InsnNode(IRETURN));
			} else if (returnType.getSort() == Type.OBJECT) {
				if (voidRet) {
					inject.add(new InsnNode(ACONST_NULL));
				}

				inject.add(new InsnNode(ARETURN));
			} else {
				inject.add(new InsnNode(RETURN));
			}

			method.instructions.add(inject);
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		basicClass[0] = writer.toByteArray();
	}

	// Some drivers (mine) seems to struggle to get the render matrices data with glGetFloat,
	// it doesn't help the fact that Minecraft uses double pass rendering for everything and
	// even for things and when it doesn't need it, this method is a victim for example.
	private static void prePassBullShit(byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);
		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			// Clear methods data and replace it with a direct call to the new function.
			if ((method.name.equals("updateRenderInfo") || (method.name.equals("a")) && method.desc.contains("Luf;Z"))) {
				InsnList inject = new InsnList();
				LabelNode continueLabel = new LabelNode();

				inject.add(new FieldInsnNode(GETSTATIC, RENDER_GLOBAL_HOOK, "PROCESS_RENDER_INFO", "Z"));
				inject.add(new JumpInsnNode(IFNE, continueLabel));
				inject.add(new InsnNode(RETURN));
				inject.add(continueLabel);

				method.instructions.add(inject);
			}
		}
	}

	static void redirectGlCalls(byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);
		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		boolean shouldReplace = false;

		for (int i = 0; i < classNode.methods.size(); i++) {
			InsnList inns = ((MethodNode) classNode.methods.get(i)).instructions;
			AbstractInsnNode insn = inns.getFirst();

			while (insn != null) {
				if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
					MethodInsnNode m = (MethodInsnNode) insn;

					if (m.owner.contains("org/lwjgl/")) {
						if (FUNCTION_NAMES.contains(m.name)) {
							inns.insertBefore(m, new MethodInsnNode(INVOKESTATIC,
								"dev/safixo/core/hooks/GlStateManager",
								m.name,
								m.desc
							));
							inns.remove(m);

							shouldReplace = true;
						}
					}
				}
				insn = insn.getNext();
			}
		}

		if (shouldReplace) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			classNode.accept(writer);
			basicClass[0] = writer.toByteArray();
		}
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
