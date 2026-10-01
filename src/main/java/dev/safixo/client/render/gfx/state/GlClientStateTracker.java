package dev.safixo.client.render.gfx.state;

import dev.safixo.client.util.MathExt;
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
		GL11.glEnableClientState(cap);
	}

	public static void glClientActiveTexture(int unit) {
		GL13.glClientActiveTexture(unit);
	}

	public static void glClientActiveTextureARB(int unit) {
		glClientActiveTexture(unit);
	}

	public static void glDisableClientState(int cap) {
		GL11.glDisableClientState(cap);
	}

	public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
		GL11.glVertexPointer(size, type, stride, pointer);
	}

	public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
		GL11.glTexCoordPointer(size, type, stride, pointer);
	}

	public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
		GL11.glColorPointer(size, type, stride, pointer);
	}

	public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
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
