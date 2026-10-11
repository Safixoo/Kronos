package dev.safixo.client.render.gfx.state;

import dev.safixo.client.render.FrameDebug;
import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.util.StagingBuffer;
import dev.safixo.client.util.data.FrameTimer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.lwjgl.BufferChecks;
import org.lwjgl.MemoryUtil;
import org.lwjgl.opengl.*;

import java.nio.*;

@SuppressWarnings("unused")
public class GlStateTracker {
	public static final boolean SKIP_CACHE = false;

	public static String VENDOR;

	public static int LAST_DEPTH_FUNC = -1;
	public static int LAST_VAO = -1;

	private static final Int2IntOpenHashMap LAST_BUFFER_TARGET = new Int2IntOpenHashMap();
	private static ImprovedTessellator TESSELLATOR;

	static {
		LAST_BUFFER_TARGET.defaultReturnValue(-1);
	}

	public static void update(boolean processMessages) {
		flushDrawState();
		Display.update(processMessages);

		FrameDebug.debug();

		StagingBuffer.advanceFrame();
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
		if (id == LAST_BUFFER_TARGET.get(target) && !SKIP_CACHE) {
			return;
		}
		if (!SKIP_CACHE) {
			LAST_BUFFER_TARGET.put(target, id);
		}

		GL15.glBindBuffer(target, id);
	}

	public static void glBindVertexArray(int vao) {
		if (LAST_VAO == vao && !SKIP_CACHE) {
			return;
		}

		LAST_BUFFER_TARGET.remove(GL15.GL_ELEMENT_ARRAY_BUFFER);

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

	public static void glPolygonOffset(float factor, float units) {
		GL11.glPolygonOffset(factor, units);
	}

	public static void glLogicOp(int opcode) {
		flushDrawState();
		GL11.glLogicOp(opcode);
	}

	public static void glLineWidth(float width) {
		GL11.glLineWidth(width);
	}

	public static String glGetString(int pname) {
		return GL11.glGetString(pname);
	}

	public static void glCullFace(int mode) {
		GL11.glCullFace(mode);
	}

	public static void glFlush() {
	}

	public static void glBindSampler(int unit, int sampler) {
		GL33.glBindSampler(unit, sampler);
	}

	public static void glBindBufferARB(int target, int id) {
		glBindBuffer(target, id);
	}

	public static void glBufferData(int target, long data, int usage) {
		GL15.glBufferData(target, data, usage);
	}

	public static void glBufferData(int target, DoubleBuffer data, int usage) {
		GL15.glBufferData(target, data, usage);
	}

	public static void glBufferData(int target, FloatBuffer data, int usage) {
		GL15.glBufferData(target, data, usage);
	}

	public static void glBufferData(int target, IntBuffer data, int usage) {
		GL15.glBufferData(target, data, usage);
	}

	public static void glBufferData(int target, ShortBuffer data, int usage) {
		GL15.glBufferData(target, data, usage);
	}

	public static void glBufferData(int target, ByteBuffer data, int usage) {
		GL15.glBufferData(target, data, usage);
	}

	public static void glBufferSubData(int target, long offset, ByteBuffer data) {
		GL15.glBufferSubData(target, offset, data);
	}

	public static void glBufferSubData(int target, long offset, DoubleBuffer data) {
		GL15.glBufferSubData(target, offset, data);
	}

	public static void glBufferSubData(int target, long offset, FloatBuffer data) {
		GL15.glBufferSubData(target, offset, data);
	}

	public static void glBufferSubData(int target, long offset, IntBuffer data) {
		GL15.glBufferSubData(target, offset, data);
	}

	public static void glBufferSubData(int target, long offset, ShortBuffer data) {
		GL15.glBufferSubData(target, offset, data);
	}

	public static void glCopyBufferSubData(int readtarget, int writetarget, long readoffset, long writeoffset, long size) {
		GL31.glCopyBufferSubData(readtarget, writetarget, readoffset, writeoffset, size);
	}

	public static void flushDrawState() {
		if (TESSELLATOR == null) {
			TESSELLATOR = ImprovedTessellator.getTessellator();
		}

		TESSELLATOR.flushState();
	}
}
