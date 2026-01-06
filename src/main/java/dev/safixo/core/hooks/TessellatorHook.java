package dev.safixo.core.hooks;

import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.render.vertex.VertexWriter;

@SuppressWarnings("unused")
public class TessellatorHook {
	public static void setTextureUV(double u, double v) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.u = (float) u;
		current.v = (float) v;
	}

	public static void addVertexWithUV(double x, double y, double z, double u, double v) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.x = (float) x;
		current.y = (float) y;
		current.z = (float) z;

		setTextureUV(u, v);
		current.addVertex();
	}

	public static void addVertex(double x, double y, double z) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.x = (float) x;
		current.y = (float) y;
		current.z = (float) z;

		current.addVertex();
	}

	public static void setColorRGBA(int r, int g, int b, int a) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		if (current.disableColor) {
			return;
		}

		current.color = ColorBGRManager.packColor(r, g, b);
	}

	public static void setBrightness(int lightmap) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.lightMap = lightmap;
	}

	public static void setNormal(int normal) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.normal = normal;
	}

	public static void setTranslation(double x, double y, double z) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.trasX = (float) x;
		current.trasY = (float) y;
		current.trasZ = (float) z;
	}

	public static void addTranslation(float x, float y, float z) {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.trasX += x;
		current.trasY += y;
		current.trasZ += z;
	}

	public static void disableColor() {
		VertexWriter current = VertexWriter.getCurrentInstance();

		current.disableColor = true;
	}
}
