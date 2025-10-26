package dev.safixo.core.hooks;

import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.render.vertex.VertexWriterManager;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class TessellatorHook {
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
}
