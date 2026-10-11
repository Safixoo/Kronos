package dev.safixo.core;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import dev.safixo.core.hooks.LongHashMapHook;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class KronosTransformer implements IClassTransformer {
	static final boolean DEBUG_MEM_OPT = false;

	static final String ASYNC_BLOCK_HOOK = "dev/safixo/core/hooks/AsyncBlockHook";
	static final String RENDER_GLOBAL_HOOK = "dev/safixo/core/hooks/RenderGlobalHook";
	static final String DEBUG_SCREEN_HOOK = "dev/safixo/core/hooks/DebugScreenHook";
	static final String FONT_RENDERER_HOOK = "dev/safixo/core/hooks/FontRendererHook";
	static final String FRUSTUM_HOOK = "dev/safixo/core/hooks/FrustumHook";
	static final String MINECRAFT_HOOK = "dev/safixo/core/hooks/MinecraftHook";
	static final String TEXTURE_MAP_HOOK = "dev/safixo/core/hooks/TextureMapHook";

	static final String SKY_RENDERER = "dev/safixo/client/render/pipelines/sky/SkyRenderer";
	static final String ADV_MODEL_RENDERER = "dev/safixo/client/render/pipelines/entity/AdvancedModelRenderer";
	static final String REBUILD_LISTENER = "dev/safixo/client/render/pipelines/terrain/meshing/RebuildListener";

	static final String RENDER = "net.minecraft.client.renderer.entity.Render";
	static final String TEXTURE_ATLAS_SPRITE = "net.minecraft.client.renderer.texture.TextureAtlasSprite";
	static final String PROFILER = "net.minecraft.profiler.Profiler";
	static final String BLOCK_SNOW = "net.minecraft.block.BlockSnow";
	static final String RENDER_GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
	static final String ENTITY_RENDERER = "net.minecraft.client.renderer.EntityRenderer";
	static final String ITEM_RENDERER = "net.minecraft.client.renderer.ItemRenderer";
	static final String CLIPPING_HELPER_IMPL = "net.minecraft.client.renderer.culling.ClippingHelperImpl";
	static final String CLIPPING_HELPER = "net.minecraft.client.renderer.culling.ClippingHelper";
	static final String FONT_RENDERER = "net.minecraft.client.gui.FontRenderer";
	static final String MINECRAFT = "net.minecraft.client.Minecraft";
	static final String LONG_HASH_MAP = DEBUG_MEM_OPT ? "net.minecraft.util.LongHashMap" : "disabled!";
	static final String WORLD_CLIENT = "net.minecraft.client.multiplayer.WorldClient";
	static final String MODEL_RENDERER = "net.minecraft.client.model.ModelRenderer";
	static final String TEXTURE_MAP = "net.minecraft.client.renderer.texture.TextureMap";
	static final String WORLD = "net.minecraft.world.World";

	static HashMap<String, String> FUNCTION_NAMES;
	public static HashSet<String> UNKNOWN_FUNCTIONS;
	public static HashMap<Integer, String> CAP_TO_NAME;

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

	private byte[] classBytes;

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		this.classBytes = basicClass;

		checkDevEnvironment();

		// Overwrites classes methods completely with a function call with the same
		// args and with the instance of the original class.
		switch (transformedName) {
			case TEXTURE_ATLAS_SPRITE:
				this.replaceClassMethod(TEXTURE_MAP_HOOK, "updateAnimation", "()V");
				break;
			case TEXTURE_MAP:
				this.replaceClassMethod(TEXTURE_MAP_HOOK, "loadTextureAtlas", "(Lbjp;)V");
				break;
			case RENDER:
				this.replaceClassMethod(MINECRAFT_HOOK, "bindTexture", "(D)V");
				break;
			case PROFILER:
				this.setFieldInProfiling();
				break;
			case ENTITY_RENDERER:
				this.avoidDoublePassBullshit();
				this.replaceClassMethod(MINECRAFT_HOOK, "disableLightmap", "(D)V");
				this.replaceClassMethod(MINECRAFT_HOOK, "enableLightmap", "(D)V");
				break;
			case BLOCK_SNOW:
				this.replaceClassMethod(MINECRAFT_HOOK, "shouldSideBeRendered", "(Lacf;IIII)Z");
				break;
			case MODEL_RENDERER:
				this.replaceClassMethod(ADV_MODEL_RENDERER, "render", "(F)V");
				this.replaceClassMethod(ADV_MODEL_RENDERER, "renderWithRotation", "(F)V");
				this.replaceClassMethod(ADV_MODEL_RENDERER, "postRender", "(F)V");
				break;
			case WORLD_CLIENT:
				this.replaceClassMethod(MINECRAFT_HOOK, "createChunkProvider", "()Lado");
				break;
			case LONG_HASH_MAP:
				return LongHashMapHook.rewriteHashMapClass();
			case WORLD:
				this.replaceClassMethod(MINECRAFT_HOOK, "getLightBrightnessForSkyBlocks", "(IIII)I");
				break;
			case RENDER_GLOBAL:
				this.replaceClassMethod(SKY_RENDERER, "renderSky", "(F)V");

				// Redirect terrain rendering calls.
				this.replaceClassMethod(RENDER_GLOBAL_HOOK, "loadRenderers", "()V");
				this.replaceClassMethod(RENDER_GLOBAL_HOOK, "clipRenderersByFrustum", "(Lbft;F)V");
				this.replaceClassMethod(RENDER_GLOBAL_HOOK, "sortAndRender", "(Lof;ID)I");
				this.replaceClassMethod(RENDER_GLOBAL_HOOK, "updateRenderers", "(Lof;Z)Z");
				this.replaceClassMethod(RENDER_GLOBAL_HOOK, "renderAllRenderLists", "(Lof;ID)V");

				// OptiFine seems to replace renderAllRenderLists by this method.
				this.replaceClassMethod(RENDER_GLOBAL_HOOK, "renderAllSortedRenderers", "()V");

				// Redirect most renderer updates.
				this.replaceClassMethod(REBUILD_LISTENER, "markBlockForUpdate", "(III)V");
				this.replaceClassMethod(REBUILD_LISTENER, "markBlockForRenderUpdate","(III)V");
				this.replaceClassMethod(REBUILD_LISTENER, "markBlockRangeForRenderUpdate","(IIIIII)V");
				this.replaceClassMethod(REBUILD_LISTENER, "markBlocksForUpdate", "(IIIIII)V");

				// Improved clouds.
				this.replaceClassMethod(RENDER_GLOBAL_HOOK, "renderCloudsFancy", "(F)V");
				break;
			case ITEM_RENDERER:
				// Batches all Tessellator calls to item renderer.
				this.replaceClassMethod(MINECRAFT_HOOK, "renderItemIn2D", "(Ljava/lang/String;III)I");
				break;
			case FONT_RENDERER:
				// Debug info.
				this.replaceClassMethod(DEBUG_SCREEN_HOOK, "drawStringWithShadow", "(Ljava/lang/String;III)I");

				// Redirect improved font-renderer draw loop.
				this.replaceClassMethod(FONT_RENDERER_HOOK, "renderString", "(Ljava/lang/String;IIIZ)I");
				break;
			case CLIPPING_HELPER_IMPL:
				// Redirect frustum setup to a more optimized and used one.
				this.replaceClassMethod(FRUSTUM_HOOK, "init", "()V");
				break;
			case CLIPPING_HELPER:
				// Uses the optimized implementation of frustum.
				this.replaceClassMethod(FRUSTUM_HOOK, "isBoxInFrustum", "(DDDDDD)Z");
				break;
			case MINECRAFT:
				// In many drivers in make stalls the GPU too soon in the tick loop.
				this.replaceClassMethod(MINECRAFT_HOOK, "checkGLError", "(Ljava/lang/String;)V");
				this.replaceClassMethod(MINECRAFT_HOOK, "isAmbientOcclusionEnabled", "()Z");
				break;
		}

		fillStateMachineFunctions();

		if (DEBUG_MEM_OPT) {
			if (!transformedName.contains("joptsimple") && !transformedName.startsWith("dev.safixo")) {
				this.changeHashMap();
				this.changeHashSet();

				if (transformedName.equals("cpw.mods.fml.common.discovery.ModCandidate")) {
					this.changeListSet();
				}
			}
		}

		if (!transformedName.equals("net.minecraft.client.renderer.Tessellator")) {
			this.replaceTessellatorsInstances();
		}

		if (!transformedName.startsWith("dev.safixo.client.render.gfx.state") && !transformedName.equals("dev.safixo.core.hooks.GLFunctions")) {
			this.redirectGlCalls();
		}

		return this.classBytes;
	}

	void changeHashMap() {
		ClassReader reader = new ClassReader(this.classBytes);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (MethodNode method : (List<MethodNode>) classNode.methods) {
			InsnList inns = method.instructions;
			AbstractInsnNode insn = inns.getFirst();

			String hashMapWrapped = "dev/safixo/client/util/collection/HashMapWrapped";

			while (insn != null) {
				if (insn.getOpcode() == INVOKESPECIAL) {
					MethodInsnNode m = (MethodInsnNode) insn;
					if (m.owner.equals("java/util/HashMap")) {
						m.owner = hashMapWrapped;
					}
				}
				if (insn.getOpcode() == NEW) {
					TypeInsnNode t = (TypeInsnNode) insn;
					if (t.desc.equals("java/util/HashMap")) {
						t.desc = hashMapWrapped;
					}
				}
				if (insn.getOpcode() == INVOKESTATIC) {
					MethodInsnNode m = (MethodInsnNode) insn;
					if (m.owner.equals("com/google/common/collect/Maps") && m.name.contains("newHashMap")) {
						m.owner = hashMapWrapped;
					}
				}

				insn = insn.getNext();
			}
		}

		classNode.accept(writer);
		this.classBytes = writer.toByteArray();
	}

	void changeHashSet() {
		ClassReader reader = new ClassReader(this.classBytes);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			InsnList inns = method.instructions;
			AbstractInsnNode insn = inns.getFirst();

			String hashMapWrapped = "dev/safixo/client/util/collection/HashSetWrapped";

			while (insn != null) {
				if (insn.getOpcode() == INVOKESPECIAL) {
					MethodInsnNode m = (MethodInsnNode) insn;
					if (m.owner.equals("java/util/HashSet")) {
						m.owner = hashMapWrapped;
					}
				} else if (insn.getOpcode() == NEW) {
					TypeInsnNode t = (TypeInsnNode) insn;
					if (t.desc.equals("java/util/HashSet")) {
						t.desc = hashMapWrapped;
					}
				} else if (insn.getOpcode() == INVOKESTATIC) {
					MethodInsnNode m = (MethodInsnNode) insn;
					if (m.owner.equals("com/google/common/collect/Sets") && m.name.contains("newHashSet")) {
						m.owner = hashMapWrapped;
					}
				}

				insn = insn.getNext();
			}
		}

		classNode.accept(writer);
		this.classBytes = writer.toByteArray();
	}

	void changeListSet() {
		ClassReader reader = new ClassReader(this.classBytes);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			InsnList inns = method.instructions;
			AbstractInsnNode insn = inns.getFirst();

			String hashMapWrapped = "dev/safixo/client/util/collection/ListSetWrapped";

			while (insn != null) {
				if (insn.getNext() != null && insn.getNext().getOpcode() == PUTFIELD) {
					FieldInsnNode fInsnNode = (FieldInsnNode) insn.getNext();

					if (fInsnNode.name.equals("packages")) {
						if (insn.getOpcode() == INVOKESTATIC) {
							MethodInsnNode m = (MethodInsnNode) insn;
							if (m.owner.equals("com/google/common/collect/Lists") && m.name.contains("newArrayList")) {
								m.owner = hashMapWrapped;
								break;
							}
						}
					}
				}

				insn = insn.getNext();
			}
		}

		classNode.accept(writer);
		this.classBytes = writer.toByteArray();
	}

	void setFieldInProfiling() {
		ClassReader reader = new ClassReader(this.classBytes);

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

		this.classBytes = writer.toByteArray();
	}

	void replaceTessellatorsInstances() {
		ClassReader reader = new ClassReader(this.classBytes);

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

		this.classBytes = writer.toByteArray();
	}

	static void addFunction(String className, String function) {
		FUNCTION_NAMES.put(function, className);
	}

	static void readGLFields(byte[] classBytes) {
		ClassReader reader = new ClassReader(classBytes);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (FieldNode field : (List<FieldNode>) classNode.fields) {
			if (field.value instanceof Integer) {
				CAP_TO_NAME.put((Integer) field.value, field.name);
			}
		}
	}

/*
[X] glLogicOp
[X] glLineWidth
[X] glPolygonOffset

[X] glVertexAttribPointer
[X] glDisableVertexAttribArray
[X] glEnableVertexAttribArray
[X] glVertexAttribIPointer

// Buffer data.
[X] glSamplerParameteri
 */

	static void fillStateMachineFunctions() {
		if (FUNCTION_NAMES == null) {
			FUNCTION_NAMES = new HashMap<>(64);
			CAP_TO_NAME = new HashMap<>(64);
			UNKNOWN_FUNCTIONS = new HashSet<>(64);
		} else {
			return;
		}

		try {
			readGLFields(Launch.classLoader.getClassBytes(GL11.class.getName()));
			readGLFields(Launch.classLoader.getClassBytes(GL12.class.getName()));
			readGLFields(Launch.classLoader.getClassBytes(GL13.class.getName()));
		} catch (IOException e) {
			throw new RuntimeException(e);
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

		addFunction("GlTextureTracker", "glTexParameteri");
		addFunction("GlTextureTracker", "glTexParameterf");
		addFunction("GlTextureTracker", "glMultiTexCoord2fARB");
		addFunction("GlTextureTracker", "glGetTexLevelParameteri");
		addFunction("GlTextureTracker", "glTexGen");
		addFunction("GlTextureTracker", "glTexGeni");
		addFunction("GlTextureTracker", "glAlphaFunc");
		addFunction("GlTextureTracker", "glActiveTextureARB");
		addFunction("GlTextureTracker", "glClientActiveTextureARB");
		addFunction("GlTextureTracker", "glTexSubImage2D");
		addFunction("GlTextureTracker", "glTexImage2D");

		addFunction("GlLightColorTracker", "glColorMaterial");
		addFunction("GlLightColorTracker", "glColor4f");
		addFunction("GlLightColorTracker", "glColor3f");
		addFunction("GlLightColorTracker", "glClearColor");
		addFunction("GlLightColorTracker", "glColorMask");
		addFunction("GlLightColorTracker", "glBlendFunc");
		addFunction("GlLightColorTracker", "glShadeModel");
		addFunction("GlLightColorTracker", "glClear");
		addFunction("GlLightColorTracker", "glClearDepth");
		addFunction("GlLightColorTracker", "glLight");
		addFunction("GlLightColorTracker", "glLightf");
		addFunction("GlLightColorTracker", "glLighti");
		addFunction("GlLightColorTracker", "glLightModel");

		addFunction("GlDrawTracker", "glDrawArrays");
		addFunction("GlDrawTracker", "glDrawElementsBaseVertex");
		addFunction("GlDrawTracker", "glMultiDrawArrays");
		addFunction("GlDrawTracker", "glMultiDrawElementsIndirect");
		addFunction("GlDrawTracker", "glMultiDrawArraysIndirect");

		addFunction("GlImmediateTracker", "glVertex3f");
		addFunction("GlImmediateTracker", "glTexCoord2f");
		addFunction("GlImmediateTracker", "glNormal3f");
		addFunction("GlImmediateTracker", "glNewList");
		addFunction("GlImmediateTracker", "glEndList");
		addFunction("GlImmediateTracker", "glCallList");
		addFunction("GlImmediateTracker", "glCallLists");
		addFunction("GlImmediateTracker", "glBegin");
		addFunction("GlImmediateTracker", "glEnd");

		addFunction("GlStateTracker", "glGetInteger");
		addFunction("GlStateTracker", "glGetString");
		addFunction("GlStateTracker", "glGetFloat");
		addFunction("GlStateTracker", "glBufferSubData");
		addFunction("GlStateTracker", "glFlush");
		addFunction("GlStateTracker", "glDepthFunc");
		addFunction("GlStateTracker", "glCullFace");

		addFunction("GlStateTracker", "glBindVertexArray");
		addFunction("GlStateTracker", "glBindSampler");
		addFunction("GlStateTracker", "glBindBuffer");
		addFunction("GlStateTracker", "glBindBufferARB");

		addFunction("GlStateTracker", "glPolygonOffset");
		addFunction("GlStateTracker", "glLogicOp");
		addFunction("GlStateTracker", "glLineWidth");

		addFunction("GlStateTracker", "glCopyBufferSubData");
		addFunction("GlStateTracker", "glBufferData");
		addFunction("GlStateTracker", "glBufferDataARB");
		addFunction("GlStateTracker", "update");

		addFunction("GlFogTracker", "glFog");
		addFunction("GlFogTracker", "glFogf");
		addFunction("GlFogTracker", "glFogi");
		addFunction("GlFogTracker", "glFog");
		addFunction("GlFogTracker", "glFogf");
		addFunction("GlFogTracker", "glFogi");

		addFunction("GlResourceTracker", "glGenBuffersARB");
		addFunction("GlResourceTracker", "glGenBuffers");
		addFunction("GlResourceTracker", "glGenLists");
		addFunction("GlResourceTracker", "glGenSamplers");
		addFunction("GlResourceTracker", "glGenTextures");
		addFunction("GlResourceTracker", "glGenQueriesARB");
		addFunction("GlResourceTracker", "glGenVertexArrays");

		addFunction("GlResourceTracker", "glDeleteLists");
		addFunction("GlResourceTracker", "glDeleteTextures");
		addFunction("GlResourceTracker", "glDeleteVertexArrays");
		addFunction("GlResourceTracker", "glDeleteBuffers");
		addFunction("GlResourceTracker", "glDeleteShader");
		addFunction("GlResourceTracker", "glDeleteProgram");

		addFunction("GlShaderTracker", "glGetShaderInfoLog");
		addFunction("GlShaderTracker", "glGetProgramInfoLog");
		addFunction("GlShaderTracker", "glGetUniformLocation");
		addFunction("GlShaderTracker", "glShaderSource");
		addFunction("GlShaderTracker", "glCompileShader");
		addFunction("GlShaderTracker", "glCreateShader");
		addFunction("GlShaderTracker", "glAttachShader");
		addFunction("GlShaderTracker", "glUseProgram");
		addFunction("GlShaderTracker", "glCreateProgram");
		addFunction("GlShaderTracker", "glLinkProgram");
	}

	void avoidDoublePassBullshit() {
		ClassReader reader = new ClassReader(this.classBytes);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		String renderWorld = IN_DEV ? "renderWorld" : "a";

		for (MethodNode node : (List<MethodNode>) classNode.methods) {
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
		this.classBytes = writer.toByteArray();
	}

	void redirectGlCalls() {
		ClassReader reader = new ClassReader(this.classBytes);
		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		boolean shouldReplace = false;

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode methodNode = (MethodNode) classNode.methods.get(i);
			InsnList inns = methodNode.instructions;
			AbstractInsnNode insn = inns.getFirst();

			while (insn != null) {
				if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
					MethodInsnNode m = (MethodInsnNode) insn;

					if (m.owner.contains("lwjgl")) {
						String className = FUNCTION_NAMES.get(m.name);

						if (className != null) {
							m.owner = "dev/safixo/client/render/gfx/state/" + className;
							shouldReplace = true;
						} else {
							UNKNOWN_FUNCTIONS.add(m.name);
						}
					}
				}
				insn = insn.getNext();
			}
		}

		if (shouldReplace) {
			ClassWriter writer = new ClassWriter(0);
			classNode.accept(writer);
			this.classBytes = writer.toByteArray();
		}
	}

	static String stripMethodName(String methodName) {
		return methodName.substring(0, methodName.indexOf("("));
	}

	void replaceClassMethod(String hookPath, String methodName, String descriptor) {
		ClassReader reader = new ClassReader(this.classBytes);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		String classDesc = Type.getObjectType(classNode.name).getDescriptor();

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			String mappedName = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(
				classNode.name,
				method.name,
				method.desc
			);

			if (!methodName.equals(mappedName)) {
				continue;
			}

			// Clear method's data and replace it with a direct call to the new function.
			method.localVariables = null;
			method.instructions.clear();
			method.tryCatchBlocks.clear();

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

			newDesc.append(")").append(returnType.getDescriptor());

			inject.add(new MethodInsnNode(INVOKESTATIC,
				hookPath,
				methodName,
				newDesc.toString()
			));

			if (returnType.getSort() == Type.INT || returnType.getSort() == Type.BOOLEAN) {
				inject.add(new InsnNode(IRETURN));
			} else if (returnType.getSort() == Type.OBJECT) {
				inject.add(new InsnNode(ARETURN));
			} else {
				inject.add(new InsnNode(RETURN));
			}

			method.instructions.add(inject);
		}

		try {
			classNode.accept(writer);
		} catch (Exception e) {
			System.err.println("[ERROR] Failed to rewrite method:");
			System.err.println("[ERROR] Method name: " + methodName);
			System.err.println("[ERROR] Class path: " + classNode.name);
			throw e;
		}
		this.classBytes = writer.toByteArray();
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
