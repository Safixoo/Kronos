package dev.safixo.client.render.gfx.buffer;

import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;

public class GlPersistentBuffer implements GlBuffer {
	private static Boolean SUPPORT_PERSISTENT;

	private int id;
	private int capacity;

	private ByteBuffer ptr;

	public GlPersistentBuffer() {
		this.id = GL15.glGenBuffers();
	}

	public GlPersistentBuffer(int size) {
		this();
		this.allocate(size);
	}

	public static boolean supportsPersistent() {
		if (SUPPORT_PERSISTENT != null) {
			return SUPPORT_PERSISTENT;
		}

		try {
			Class<?> classForName = Class.forName("org.lwjgl.opengl.GL45");
			return SUPPORT_PERSISTENT = GLContext.getCapabilities().OpenGL44;
		} catch (ClassNotFoundException e) {
			return SUPPORT_PERSISTENT = false;
		}
	}

	public ByteBuffer getPointer() {
		return this.ptr;
	}

	@Override
	public int getHandle() {
		return this.id;
	}

	@Override
	public int getCapacity() {
		return this.capacity;
	}

	public void allocate(int size) {
		this.bind();

		this.capacity = size;
		GL44.glBufferStorage(GL31.GL_COPY_WRITE_BUFFER, size, GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT | GL44.GL_CLIENT_STORAGE_BIT);
		this.ptr = GL30.glMapBufferRange(GL31.GL_COPY_WRITE_BUFFER, 0, this.capacity,
			GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT | GL44.GL_MAP_COHERENT_BIT, null);

		this.unbind();
	}

	public void delete() {
		GL15.glDeleteBuffers(this.id);
		this.id = -1;
	}

	public void bind() {
		GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, this.id);
	}

	public void unbind() {
		GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
	}
}
