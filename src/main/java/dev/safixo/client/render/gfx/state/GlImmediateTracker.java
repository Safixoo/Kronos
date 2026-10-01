package dev.safixo.client.render.gfx.state;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class GlImmediateTracker {
	public static boolean IMMEDIATE_DRAWING;
	private static float NX, NY, NZ;

	public static void glVertex3f(float x, float y, float z) {
		GL11.glVertex3f(x, y, z);
	}

	public static void glTexCoord2f(float x, float y) {
		GL11.glTexCoord2f(x, y);
	}

	public static void glNormal3f(float x, float y, float z) {
		if (NX != x || NY != y || NZ != z || GlStateTracker.SKIP_CACHE || IMMEDIATE_DRAWING) {
			if (!IMMEDIATE_DRAWING) {
				NX = x;
				NY = y;
				NZ = z;
			}
			GL11.glNormal3f(x, y, z);
		}
	}

	public static void glBegin(int mode) {
		GlStateTracker.flushDrawState();

		GL11.glBegin(mode);
		IMMEDIATE_DRAWING = true;
	}

	public static void glEnd() {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL11.glEnd();
		IMMEDIATE_DRAWING = false;
	}

	public static void glEndList() {
		GlStateTracker.flushDrawState();
		GL11.glEndList();
	}

	public static void glNewList(int list, int drawMode) {
		GlStateTracker.flushDrawState();
		GL11.glNewList(list, drawMode);
	}

	public static void glCallList(int list) {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL11.glCallList(list);
	}

	public static void glCallLists(ByteBuffer lists) {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL11.glCallLists(lists);
	}

	public static void glCallLists(IntBuffer lists) {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL11.glCallLists(lists);
	}

	public static void glCallLists(ShortBuffer lists) {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL11.glCallLists(lists);
	}
}
