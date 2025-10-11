package dev.safixo.core.hooks;

import dev.safixo.client.render.util.ColorBGRManager;
import dev.safixo.client.render.vertex.VertexWriterManager;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class TessellatorHook {
	private static final String VERTEX_WRITER_PATH = "dev/safixo/client/render/vertex/VertexWriterManager";
	private static final String TESSELLATOR_HOOK_PATH = "dev/safixo/core/hooks/TessellatorHook";

	public static void setTextureUV(double u, double v) {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		current.u = u;
		current.v = v;
	}

	public static void addVertexWithUV(double x, double y, double z, double u, double v) {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		current.x = x;
		current.y = y;
		current.z = z;

		setTextureUV(u, v);
		current.addVertex();
	}

	public static void addVertex(double x, double y, double z) {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		current.x = x;
		current.y = y;
		current.z = z;

		current.addVertex();
	}

	public static void setColorRGBA(int r, int g, int b, int a) {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		if (current.disableColor) {
			return;
		}

		current.color = ColorBGRManager.packColor(r, g, b);
	}

	public static void setBrightness(int lightmap) {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		current.lightMap = lightmap;
	}

	public static void setTranslation(double x, double y, double z) {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		current.trasX = x;
		current.trasY = y;
		current.trasZ = z;
	}

	public static void addTranslation(float x, float y, float z) {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		current.trasX += x;
		current.trasY += y;
		current.trasZ += z;
	}

	public static void disableColor() {
		VertexWriterManager current = VertexWriterManager.getCurrentInstance();

		current.disableColor = true;
	}

	public static void redirectTessellatorFunc(String methodName, String runtimeName, String descriptor, byte[][] basicClass) {
		ClassReader reader = new ClassReader(basicClass[0]);
		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 0);

		for (int i = 0; i < classNode.methods.size(); i++) {
			MethodNode method = (MethodNode) classNode.methods.get(i);

			// Clear methods data and replace it with a direct call to the new function.
			if (!((method.name.equals(methodName) || (method.name.equals(runtimeName)) && method.desc.contains(descriptor)))) {
				continue;
			}

			Type[] argumentTypes = Type.getArgumentTypes(method.desc);

			LabelNode continueLabel = new LabelNode();

			// if (!VertexWriterManager.isCurrentDrawing()) {
 			//		continueLabel;
 			// }
			// VertexWriterManager.foo(..);
 			// return;
 			//
 			// continueLabel:

			// Checks if our writer is currently drawing.
			InsnList inject = new InsnList();
			inject.add(new MethodInsnNode(INVOKESTATIC,
				VERTEX_WRITER_PATH,
				"isCurrentDrawing",
				"()Z"
			));
			// If not jump to the normal path.
			inject.add(new JumpInsnNode(IFEQ,
				continueLabel
			));

			int argumentOffset = (method.access & ACC_STATIC) != 0 ? 0 : 1;

			{
				// Setup arguments based in the parameters of the current.
				for (Type type : argumentTypes) {
					argumentOffset = processType(type, inject, argumentOffset);
				}

				// Call the function to redirect and return.
				inject.add(new MethodInsnNode(INVOKESTATIC,
					TESSELLATOR_HOOK_PATH,
					methodName,
					descriptor
				));
				inject.add(new InsnNode(RETURN));
			}

			inject.add(continueLabel);

			// Add injection at the start.
			method.instructions.insert(inject);
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);

		basicClass[0] = writer.toByteArray();
	}

	static int processType(Type type, InsnList inject, int offset) {
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
