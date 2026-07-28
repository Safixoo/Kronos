package dev.safixo.core.hooks;

import dev.safixo.client.render.vertex.VertexWriter;

public class VertexRedirector {
	public static void setNormal(VertexWriter writer, int normal) {
		writer.normal = normal;
	}

	public static void setLight(VertexWriter writer, int light) {
		writer.light = light;
	}

	public static void setColor(VertexWriter writer, int color) {
		writer.color = color;
	}

	public static void setTextureUV(VertexWriter writer, double u, double v) {
		writer.u = (float) u;
		writer.v = (float) v;
	}

	public static void addVertexWithUV(VertexWriter writer, double x, double y, double z, double u, double v) {
		setTextureUV(writer, u, v);
		addVertex(writer, x, y, z);
	}

	public static void addOffset(VertexWriter writer, double dx, double dy, double dz) {
		writer.dx += dx;
		writer.dy += dy;
		writer.dz += dz;
	}

	public static void setOffset(VertexWriter writer, double dx, double dy, double dz) {
		writer.dx = dx;
		writer.dy = dy;
		writer.dz = dz;
	}

	public static void addVertex(VertexWriter writer, double x, double y, double z) {
		writer.addVertex((float) x, (float) y, (float) z, true);
	}
}
