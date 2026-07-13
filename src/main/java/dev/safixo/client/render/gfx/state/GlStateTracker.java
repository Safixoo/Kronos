package dev.safixo.client.render.gfx.state;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.util.data.FrameTimer;
import org.lwjgl.opengl.*;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

@SuppressWarnings("unused")
public class GlStateTracker {
	public static final boolean SKIP_CACHE = false;
	public static String VENDOR;

	public static int LAST_DEPTH_FUNC = -1;
	public static int LAST_VAO = -1;
	public static int LAST_VBO = -1;
	public static int LAST_SSBO = -1;

	public static void update(boolean processMessages) {
		flushDrawState();
		Display.update(processMessages);

		FrameTimer.getInstance().addFrame();
	}

	public static void update() {
		update(true);
	}

	public static void glDepthFunc(int mask) {
		if (mask != LAST_DEPTH_FUNC || SKIP_CACHE) {
			LAST_DEPTH_FUNC = mask;
			flushDrawState();
			GL11.glDepthFunc(mask);
		}
	}

	public static void glBindBuffer(int target, int id) {
		if (id != 0) {
			GlClientStateTracker.invalidateAllCachedClientState();
		}

		if (target == GL15.GL_ARRAY_BUFFER && !SKIP_CACHE) {
			if (id == LAST_VBO) {
				return;
			}
			LAST_VBO = id;
		}

		if (target == GL43.GL_SHADER_STORAGE_BUFFER && !SKIP_CACHE) {
			if (id == LAST_SSBO) {
				return;
			}
			LAST_SSBO = id;
		}

		GL15.glBindBuffer(target, id);
	}

	public static void glBindVertexArray(int vao) {
		if (vao != 0) {
			GlClientStateTracker.invalidateAllCachedClientState();
		}

		if (LAST_VAO == vao) {
			return;
		}

		LAST_VAO = vao;
		GL30.glBindVertexArray(vao);
	}

	public static float glGetFloat(int name) {
		return GL11.glGetFloat(name);
	}

	public static void glGetFloat(int name, FloatBuffer params) {
		if (VENDOR == null) {
			VENDOR = GL11.glGetString(GL11.GL_VENDOR);
		}

		if (name == GL11.GL_MODELVIEW_MATRIX) {
			GlMatrixTracker.MODEL_VIEW_STACK.top().get(params);
			return;
		}
		else if (name == GL11.GL_PROJECTION_MATRIX) {
			GlMatrixTracker.PROJECTION_STACK.top().get(params);
			return;
		}

		GL11.glGetFloat(name, params);
	}

	public static int glGetInteger(int name) {
		return GL11.glGetInteger(name);
	}

	public static void glGetInteger(int name, IntBuffer buffer) {
		if (name == GL11.GL_VIEWPORT && !SKIP_CACHE) {
			int position = buffer.position();
			buffer.put(0);
			buffer.put(0);
			buffer.put(GlTextureTracker.getViewportX());
			buffer.put(GlTextureTracker.getViewportY());
			((Buffer) buffer).position(position);
			return;
		}

		GL11.glGetInteger(name, buffer);
	}

	public static void glFlush() {
	}

	public static void flushDrawState() {
		ImprovedTessellator.getTessellator().flushState();
	}
}
