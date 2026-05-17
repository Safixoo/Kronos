package dev.safixo.client.render.gfx.buffer;

import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import org.lwjgl.opengl.EXTDirectStateAccess;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class GlShaderStorageBuffer implements GlBuffer {
	private int id;
	private int hint = GL15.GL_STATIC_DRAW;
	private int capacity;
	private int bindingIndex;

	public GlShaderStorageBuffer(int hint, int bindingIndex) {
		this.id = GL15.glGenBuffers();
		this.setHint(hint);
		this.setBindingIndex(bindingIndex);
	}

	public GlShaderStorageBuffer(int size, int bindingIndex, int hint) {
		this(hint, bindingIndex);
		this.allocate(UnsafeUtil.NULL, size);
	}

	@Override
	public int getHandle() {
		return this.id;
	}

	public void allocate(long vertexData, int size) {
		this.bind();

		if (vertexData != UnsafeUtil.NULL) {
			ByteBuffer vertexDataBuffer = NativeBuffer.wrap(vertexData);
			((Buffer) vertexDataBuffer).limit(size);

			GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, vertexDataBuffer, this.hint);
		} else {
			GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, size, this.hint);
		}

		this.capacity = size;
		this.unbind();
	}

	public void bufferData(ByteBuffer buffer, int size) {
		((Buffer) buffer).limit(size);

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glNamedBufferDataEXT(this.id, buffer, this.hint);
		} else {
			this.bind();
			GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer, this.hint);
		}
	}

	public void bufferSubData(ByteBuffer buffer, int offset, int size) {
		((Buffer) buffer).limit(size);

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glNamedBufferSubDataEXT(this.id, offset, buffer);
		} else {
			this.bind();
			GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset, buffer);
		}
	}

	public void setHint(int hint) {
		this.hint = hint;
	}

	public void setBindingIndex(int index) {
		this.bindingIndex = index;
	}

	@Override
	public int getCapacity() {
		return this.capacity;
	}

	public void clear() {
		this.bind();
		this.allocate(UnsafeUtil.NULL, 0);
		this.unbind();
	}

	public void delete() {
		this.clear();

		GL15.glDeleteBuffers(this.id);
		this.id = -1;
	}

	public void bindBase() {
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, this.bindingIndex, this.id);
	}

	public void bind() {
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.id);
	}

	public void unbind() {
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
	}
}
