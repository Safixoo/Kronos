package dev.safixo.core;

import dev.safixo.core.hooks.LongHashMapHook;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import static org.objectweb.asm.Opcodes.*;

public class KronosTransformer implements IClassTransformer {
	static final boolean DISABLE_INJECTION = false;

	static final String ASYNC_BLOCK_HOOK = "dev/safixo/core/hooks/AsyncBlockHook";
	static final String RENDER_GLOBAL_HOOK = "dev/safixo/core/hooks/RenderGlobalHook";
	static final String DEBUG_SCREEN_HOOK = "dev/safixo/core/hooks/DebugScreenHook";
	static final String FONT_RENDERER_HOOK = "dev/safixo/core/hooks/FontRendererHook";
	static final String FRUSTUM_HOOK = "dev/safixo/core/hooks/FrustumHook";
	static final String MINECRAFT_HOOK = "dev/safixo/core/hooks/MinecraftHook";
	static final String ADV_MODEL_RENDERER = "dev/safixo/client/render/pipelines/entity_model/AdvancedModelRenderer";

	static final String REBUILD_LISTENER = "dev/safixo/client/render/pipelines/terrain/meshing/RebuildListener";
	static final String VANILLA_MESHER = "dev/safixo/client/render/pipelines/terrain/meshing/builders/VanillaBlockMesher";

	static final String RENDER = "net.minecraft.client.renderer.entity.Render";
	static final String VEC3_POOL = "net.minecraft.util.Vec3Pool";
	static final String STRING_TRANSLATE = "net.minecraft.util.StringTranslate";
	static final String DATA_WATCHER = "net.minecraft.entity.DataWatcher";
	static final String TEXTURE_MANAGER = "net.minecraft.client.renderer.texture.TextureManager";
	static final String PROFILER = "net.minecraft.profiler.Profiler";
	static final String RENDER_BLOCKS = "net.minecraft.client.renderer.RenderBlocks";
	static final String BLOCK_SNOW = "net.minecraft.block.BlockSnow";
	static final String RENDER_GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
	static final String ENTITY_RENDERER = "net.minecraft.client.renderer.EntityRenderer";
	static final String ITEM_RENDERER = "net.minecraft.client.renderer.ItemRenderer";
	static final String CLIPPING_HELPER_IMPL = "net.minecraft.client.renderer.culling.ClippingHelperImpl";
	static final String CLIPPING_HELPER = "net.minecraft.client.renderer.culling.ClippingHelper";
	static final String FONT_RENDERER = "net.minecraft.client.gui.FontRenderer";
	static final String MINECRAFT = "net.minecraft.client.Minecraft";
	static final String LONG_HASH_MAP = "net.minecraft.util.LongHashMap";
	static final String WORLD_CLIENT = "net.minecraft.client.multiplayer.WorldClient";
	static final String MODEL_RENDERER = "net.minecraft.client.model.ModelRenderer";
	static final String WORLD = "net.minecraft.world.World";

	static HashMap<String, String> FUNCTION_NAMES;

	public static boolean IN_DEV;
	static boolean CHECKED_FOR_DEV;

	static void checkDevEnvironment() {
		if (CHECKED_FOR_DEV) {
			return;
		}

		CHECKED_FOR_DEV = true;
		Object deObf = Launch.blackboard.get("fml.deobfuscatedEnvironment");

		IN_DEV = deObf != null && (Boolean) deObf;
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		byte[][] reference = new byte[][] { basicClass };

		if (DISABLE_INJECTION) {
			transformedName = "";
		}

		checkDevEnvironment();

		// Overwrites classes methods completely with a function call with the same
		// args and with the instance of the original class.
		switch (transformedName) {
			case VEC3_POOL:
				replaceClassMethod(MINECRAFT_HOOK, "getVecFromPool", "a", "(DDD)Latc", reference, false);
				break;
			case RENDER:
				replaceClassMethod(MINECRAFT_HOOK, "bindTexture", "a", "(D)V", reference, true);
				break;
			case PROFILER:
				setFieldInProfiling(reference);
				break;
			case ENTITY_RENDERER:
				avoidDoublePassBullshit(reference);
				replaceClassMethod(MINECRAFT_HOOK, "disableLightmap", "a", "(D)V", reference, true);
				replaceClassMethod(MINECRAFT_HOOK, "enableLightmap", "b", "(D)V", reference, true);
				break;
			case BLOCK_SNOW:
				replaceClassMethod(MINECRAFT_HOOK, "shouldSideBeRendered", "a", "(Lacf;IIII)Z", reference, false);
				break;
			case RENDER_BLOCKS:
				replaceClassMethod(VANILLA_MESHER, "renderStandardBlock", "p", "(Laqz;III)Z", reference, false);
				break;
			case MODEL_RENDERER:
				replaceClassMethod(ADV_MODEL_RENDERER, "render", "a", "(F)V", reference, false);
				replaceClassMethod(ADV_MODEL_RENDERER, "renderWithRotation", "b", "(F)V", reference, false);
				replaceClassMethod(ADV_MODEL_RENDERER, "postRender", "c", "(F)V", reference, false);
				break;
			case WORLD_CLIENT:
				replaceClassMethod(MINECRAFT_HOOK, "createChunkProvider", "j", "()Lado", reference, false);
				break;
			case LONG_HASH_MAP:
				return LongHashMapHook.rewriteHashMapClass();
			case WORLD:
				replaceClassMethod(MINECRAFT_HOOK, "getLightBrightnessForSkyBlocks", "h", "(IIII)I", reference, false);
				break;
			case RENDER_GLOBAL:
				// Redirect terrain rendering calls.
				replaceClassMethod(RENDER_GLOBAL_HOOK, "loadRenderers", "a", "()V", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "clipRenderersByFrustum", "a", "(Lbft;F)V", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "sortAndRender", "a", "(Lof;ID)I", reference, true);
				replaceClassMethod(RENDER_GLOBAL_HOOK, "updateRenderers", "a", "(Lof;Z)Z", reference, false);

				// NO-OP without OptiFine, it OptiFine is present it avoids a crash for invalid null terrain arrays.
				replaceClassMethod(RENDER_GLOBAL_HOOK, "renderAllSortedRenderers", "", "", reference, true);

				// Redirect most renderer updates.
				replaceClassMethod(REBUILD_LISTENER, "markBlockForUpdate", "a", "(III)V", reference, true);
				replaceClassMethod(REBUILD_LISTENER, "markBlockForRenderUpdate", "b", "(III)V", reference, true);
				replaceClassMethod(REBUILD_LISTENER, "markBlockRangeForRenderUpdate", "a", "(IIIIII)V", reference, true);
				replaceClassMethod(REBUILD_LISTENER, "markBlocksForUpdate", "b", "(IIIIII)V", reference, true);

				// Improved clouds.
				replaceClassMethod(RENDER_GLOBAL_HOOK, "renderCloudsFancy", "c", "(F)V", reference, true);
				break;
			case ITEM_RENDERER:
				// Batches all Tessellator calls to item renderer.
				replaceClassMethod(MINECRAFT_HOOK, "renderItemIn2D", "a", "(Ljava/lang/String;III)I", reference, false);
			case FONT_RENDERER:
				// Debug info.
				replaceClassMethod(DEBUG_SCREEN_HOOK, "drawStringWithShadow", "a", "(Ljava/lang/String;III)I", reference, false);

				// Redirect improved font-renderer draw loop.
				replaceClassMethod(FONT_RENDERER_HOOK, "renderString", "b", "(Ljava/lang/String;IIIZ)I", reference, false);
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
		}

		fillStateMachineFunctions();

		if (!transformedName.equals("net.minecraft.client.renderer.Tessellator")) {
			replaceTessellatorsInstances(reference);
		}

		if (!transformedName.contains("dev.safixo.client.render.gfx.state") && !transformedName.equals("dev.safixo.core.hooks.GLFunctions")) {
			redirectGlCalls(reference);
		}

		return reference[0];
	}


	// TODO: Check what makes it that mess up in some instances.
	static void changeHashMap(byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			if (!method.name.equals("<init>")) {
				continue;
			}

			InsnList inns = method.instructions;
			AbstractInsnNode insn = inns.getFirst();

			while (insn != null) {
				if (insn.getOpcode() == INVOKESPECIAL) {
					MethodInsnNode m = (MethodInsnNode) insn;
					if (m.owner.equals("java/util/HashMap")) {
						m.owner = "dev/safixo/client/util/HashMapWrapped";
					}
				} else if (insn.getOpcode() == NEW) {
					TypeInsnNode t = (TypeInsnNode) insn;
					if (t.desc.equals("java/util/HashMap")) {
						t.desc = "dev/safixo/client/util/HashMapWrapped";
					}
				} else if (insn.getOpcode() == INVOKESTATIC) {
					MethodInsnNode m = (MethodInsnNode) insn;
					if (m.owner.equals("com/google/common/collect/Maps")) {
						m.owner = "dev/safixo/client/util/HashMapWrapped";
					}
				}

				insn = insn.getNext();
			}
		}

		classNode.accept(writer);
		basicClass[0] = writer.toByteArray();
	}

	static void setFieldInProfiling(byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			if ((!method.name.equals("startSection") && ((!method.name.equals("a")) || !method.desc.contains("(Ljava/lang/String;)V")))) {
				continue;
			}

			method.instructions.insertBefore(method.instructions.getFirst(), new MethodInsnNode(INVOKESTATIC, MINECRAFT_HOOK, "setProfilerTarget", "(Ljava/lang/String;)V"));
			method.instructions.insertBefore(method.instructions.getFirst(), new VarInsnNode(ALOAD, 1));
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		basicClass[0] = writer.toByteArray();
	}

	static void replaceTessellatorsInstances(byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode node = (MethodNode) classNode.methods.get(i);

			InsnList inns = node.instructions;
			AbstractInsnNode insn = inns.getFirst();

			while (insn != null) {
				int opcode = insn.getOpcode();

				if (opcode == GETSTATIC) {
					FieldInsnNode fieldInsn = (FieldInsnNode) insn;
					String owner = IN_DEV ? "net/minecraft/client/renderer/Tessellator" : "bfq";
					String desc = "L" + owner + ";";

					if (fieldInsn.owner.equals(owner) && fieldInsn.desc.equals(desc)) {
						inns.set(fieldInsn, new MethodInsnNode(INVOKESTATIC, "dev/safixo/client/render/ImprovedTessellator",
							"getTessellator",
							"()Ldev/safixo/client/render/ImprovedTessellator;"));
					}
				}
				insn = insn.getNext();
			}
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		basicClass[0] = writer.toByteArray();
	}

	static void addFunction(String className, String function) {
		FUNCTION_NAMES.put(function, className);
	}

	static void fillStateMachineFunctions() {
		if (FUNCTION_NAMES == null) {
			FUNCTION_NAMES = new HashMap<>(64);
		}

		addFunction("GlBooleanTracker", "glEnable");
		addFunction("GlBooleanTracker", "glDisable");
		addFunction("GlBooleanTracker", "glDepthMask");

		addFunction("GlClientStateTracker", "glClientActiveTexture");
		addFunction("GlClientStateTracker", "glEnableClientState");
		addFunction("GlClientStateTracker", "glDisableClientState");
		addFunction("GlClientStateTracker", "glVertexPointer");
		addFunction("GlClientStateTracker", "glTexCoordPointer");
		addFunction("GlClientStateTracker", "glColorPointer");
		addFunction("GlClientStateTracker", "glNormalPointer");

		addFunction("GlMatrixTracker", "glMatrixMode");
		addFunction("GlMatrixTracker", "glLoadIdentity");
		addFunction("GlMatrixTracker", "glLoadMatrix");
		addFunction("GlMatrixTracker", "glPopMatrix");
		addFunction("GlMatrixTracker", "glPushMatrix");
		addFunction("GlMatrixTracker", "glMultMatrix");
		addFunction("GlMatrixTracker", "glScalef");
		addFunction("GlMatrixTracker", "glTranslatef");
		addFunction("GlMatrixTracker", "glRotatef");
		addFunction("GlMatrixTracker", "glFrustum");
		addFunction("GlMatrixTracker", "glScaled");
		addFunction("GlMatrixTracker", "glRotated");
		addFunction("GlMatrixTracker", "glOrtho");
		addFunction("GlMatrixTracker", "gluPerspective");

		addFunction("GlTextureTracker", "glMultiTexCoord2f");
		addFunction("GlTextureTracker", "glActiveTexture");
		addFunction("GlTextureTracker", "glBindTexture");
		addFunction("GlTextureTracker", "glViewport");
		addFunction("GlTextureTracker", "glCopyTexSubImage2D");
		addFunction("GlTextureTracker", "glBindFramebuffer");

		addFunction("GlLightColorTracker", "glColorMaterial");
		addFunction("GlLightColorTracker", "glColor4f");
		addFunction("GlLightColorTracker", "glColor3f");
		addFunction("GlLightColorTracker", "glClearColor");
		addFunction("GlLightColorTracker", "glColorMask");
		addFunction("GlLightColorTracker", "glBlendFunc");
		addFunction("GlLightColorTracker", "glShadeModel");
		addFunction("GlLightColorTracker", "glClear");
		addFunction("GlLightColorTracker", "glLight");
		addFunction("GlLightColorTracker", "glLightf");
		addFunction("GlLightColorTracker", "glLighti");
		addFunction("GlLightColorTracker", "glLightModel");

		addFunction("GlDrawTracker", "glNewList");
		addFunction("GlDrawTracker", "glEndList");
		addFunction("GlDrawTracker", "glCallList");
		addFunction("GlDrawTracker", "glBegin");
		addFunction("GlDrawTracker", "glEnd");
		addFunction("GlDrawTracker", "glDrawArrays");
		addFunction("GlDrawTracker", "glMultiDrawArrays");
		addFunction("GlDrawTracker", "glMultiDrawArraysIndirect");

		addFunction("GlStateTracker", "glGetInteger");
		addFunction("GlStateTracker", "glGetFloat");
		addFunction("GlStateTracker", "glBindVertexArray");
		addFunction("GlStateTracker", "glBindBuffer");
		addFunction("GlStateTracker", "glFlush");
		addFunction("GlStateTracker", "glDepthFunc");
		addFunction("GlStateTracker", "update");

		addFunction("GlFogTracker", "glFog");
		addFunction("GlFogTracker", "glFogf");
		addFunction("GlFogTracker", "glFogi");
	}

	static void avoidDoublePassBullshit(byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		String renderWorld = IN_DEV ? "renderWorld" : "a";

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode node = (MethodNode) classNode.methods.get(i);

			if (!node.name.equals(renderWorld) ) {
				continue;
			}

			InsnList inns = node.instructions;
			AbstractInsnNode insn = inns.getFirst();

			boolean skip = false;

			while (insn != null && !skip) {
				if (insn.getType() == AbstractInsnNode.INSN) {
					InsnNode m = (InsnNode) insn;

					if (m.getOpcode() == ICONST_2) {
						node.instructions.set(m, new InsnNode(ICONST_1));
						skip = true;
					}
				}
				insn = insn.getNext();
			}
		}

		classNode.accept(writer);
		basicClass[0] = writer.toByteArray();
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

					if (m.owner.contains("lwjgl")) {
						String className = FUNCTION_NAMES.get(m.name);

						if (className != null) {
							m.owner = "dev/safixo/client/render/gfx/state/" + className;
							shouldReplace = true;
						}
					}
				}
				insn = insn.getNext();
			}
		}

		if (shouldReplace) {
			ClassWriter writer = new ClassWriter(0);
			classNode.accept(writer);
			basicClass[0] = writer.toByteArray();
		}
	}

	static void replaceClassMethod(String hookPath, String methodName, String runtimeName, String descriptor, byte[][] basicClass, boolean voidRet) {
		ClassReader reader = new ClassReader(basicClass[0]);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

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
