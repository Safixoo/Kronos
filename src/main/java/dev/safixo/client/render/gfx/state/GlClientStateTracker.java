package dev.safixo.client.render.gfx.state;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

@SuppressWarnings("unused")
public class GlClientStateTracker {
	private static final ClientStateData NORMAL = new ClientStateData();
	private static final ClientStateData VERTEX = new ClientStateData();
	private static final ClientStateData COLOR = new ClientStateData();

	private static final ClientStateData TEX_0 = new ClientStateData();
	private static final ClientStateData TEX_1 = new ClientStateData();

	private static boolean VERTEX_ACTIVE, VERTEX_QUEUED;
	private static boolean NORMAL_ACTIVE, NORMAL_QUEUED;
	private static boolean COLOR_ACTIVE, COLOR_QUEUED;

	private static boolean TEX0_ACTIVE, TEX0_QUEUED;
	private static boolean TEX1_ACTIVE, TEX1_QUEUED;

	private static int UNIT;

	public static void glEnableClientState(int cap) {
		if (cap == GL11.GL_VERTEX_ARRAY) {
			VERTEX_QUEUED = true;
			if (VERTEX_ACTIVE) {
				return;
			}
			VERTEX_ACTIVE = true;
		} else if (cap == GL11.GL_COLOR_ARRAY) {
			COLOR_QUEUED = true;
			if (COLOR_ACTIVE) {
				return;
			}
			COLOR_ACTIVE = true;
		} else if (cap == GL11.GL_NORMAL_ARRAY) {
			NORMAL_QUEUED = true;
			if (NORMAL_ACTIVE) {
				return;
			}
			NORMAL_ACTIVE = true;
		} else if (cap == GL11.GL_TEXTURE_COORD_ARRAY) {
			if (UNIT == GL13.GL_TEXTURE0) {
				TEX0_QUEUED = true;
				if (TEX0_ACTIVE) {
					return;
				}
				TEX0_ACTIVE = true;
			} else {
				TEX1_QUEUED = true;
				if (TEX1_ACTIVE) {
					return;
				}
				TEX1_ACTIVE = true;
			}
		}

		GL11.glEnableClientState(cap);
	}

	public static void glClientActiveTexture(int unit) {
		UNIT = unit;
		GL13.glClientActiveTexture(unit);
	}

	// Called before drawing, and before binding to vertex arrays/buffers as they are incompatible
	// with client arrays rendering.
	public static void invalidateAllCachedClientState() {
		if (VERTEX_ACTIVE && !VERTEX_QUEUED) {
			invalidateState(VERTEX);
			GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
			VERTEX_ACTIVE = VERTEX_QUEUED;
		}
		if (NORMAL_ACTIVE && !NORMAL_QUEUED) {
			invalidateState(NORMAL);
			GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
			NORMAL_ACTIVE = NORMAL_QUEUED;
		}
		if (COLOR_ACTIVE && !COLOR_QUEUED) {
			invalidateState(COLOR);
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
			COLOR_ACTIVE = COLOR_QUEUED;
		}
		boolean restore = false;

		if (TEX0_ACTIVE && !TEX0_QUEUED) {
			invalidateState(TEX_0);
			GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			TEX0_ACTIVE = TEX0_QUEUED;
			restore = true;
		}
		if (TEX1_ACTIVE && !TEX1_QUEUED) {
			invalidateState(TEX_1);
			GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			TEX1_ACTIVE = TEX1_QUEUED;
			restore = true;
		}

		if (restore) {
			GL13.glClientActiveTexture(UNIT);
		}
	}

	public static void glDisableClientState(int cap) {
		if (cap == GL11.GL_VERTEX_ARRAY) {
			VERTEX_QUEUED = false;
			return;
		} else if (cap == GL11.GL_COLOR_ARRAY) {
			COLOR_QUEUED = false;
			return;
		} else if (cap == GL11.GL_NORMAL_ARRAY) {
			NORMAL_QUEUED = false;
			return;
		} else if (cap == GL11.GL_TEXTURE_COORD_ARRAY) {
			if (UNIT == GL13.GL_TEXTURE0) {
				TEX0_QUEUED = false;
			}
			if (UNIT == GL13.GL_TEXTURE1) {
				TEX1_QUEUED = false;
			}
			return;
		}

		GL11.glDisableClientState(cap);
	}

	public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
		if (equal(VERTEX, size, type, stride, pointer)) {
			return;
		}

		setData(VERTEX, size, type, stride, pointer);
		GL11.glVertexPointer(size, type, stride, pointer);
	}

	public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
		ClientStateData stateByUnit = UNIT == GL13.GL_TEXTURE0 ? TEX_0 : TEX_1;

		if (equal(stateByUnit, 3, type, stride, pointer)) {
			return;
		}

		setData(stateByUnit, 3, type, stride, pointer);
		GL11.glTexCoordPointer(size, type, stride, pointer);
	}

	public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
		if (equal(COLOR, size, type, stride, pointer)) {
			return;
		}

		setData(COLOR, size, type, stride, pointer);
		GL11.glColorPointer(size, type, stride, pointer);
	}

	public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
		if (equal(NORMAL, 3, type, stride, pointer)) {
			return;
		}

		setData(NORMAL, 3, type, stride, pointer);
		GL11.glNormalPointer(type, stride, pointer);
	}

	public static void glVertexPointer(int size, int type, int stride, long pointer) {
		GL11.glVertexPointer(size, type, stride, pointer);
	}

	public static void glTexCoordPointer(int size, int type, int stride, long pointer) {
		GL11.glTexCoordPointer(size, type, stride, pointer);
	}

	public static void glColorPointer(int size, int type, int stride, long pointer) {
		GL11.glColorPointer(size, type, stride, pointer);
	}

	public static void glNormalPointer(int type, int stride, long pointer) {
		GL11.glNormalPointer(type, stride, pointer);
	}

	private static void setData(ClientStateData state, int size, int type, int stride, ByteBuffer pointer) {
		state.stride = stride;
		state.type = type;
		state.pointer = pointer;
		state.size = size;
	}

	private static void invalidateState(ClientStateData state) {
		state.pointer = null;
	}

	private static boolean equal(ClientStateData state, int size, int type, int stride, ByteBuffer pointer) {
		return state.pointer == pointer && state.type == type && state.size == size && state.stride == stride;
	}

	private static class ClientStateData {
		int size;
		int type;
		int stride;
		ByteBuffer pointer;
	}
}
