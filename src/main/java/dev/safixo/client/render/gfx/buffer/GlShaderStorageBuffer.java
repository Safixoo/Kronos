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
		this.bufferData(UnsafeUtil.NULL, 0, size);
	}

	@Override
	public int getHandle() {
		return this.id;
	}

	@Override
	public int getBufferTarget() {
		return 0;
	}

	@Override
	public void bufferData(long ptr, int offset, int size) {
		if (ptr == UnsafeUtil.NULL) {
			this.bufferData(null, offset, size);
			return;
		}


		this.bufferData(NativeBuffer.wrap(ptr), offset, size);
	}

	@Override
	public void bufferSubData(long ptr, int offset, int writingOffset, int size) {
		if (ptr == UnsafeUtil.NULL) {
			this.bufferSubData(null, 0, 0, size);
			return;
		}

		this.bufferSubData(NativeBuffer.wrap(ptr), offset, writingOffset, size);
	}

	@Override
	public void bufferData(ByteBuffer buffer, int position, int size) {
		this.bind();

		if (buffer == null) {
			GL15.glBufferData(this.getBufferTarget(), size, this.hint);
		} else {
			((Buffer)buffer).position(position);
			((Buffer)buffer).limit(size);
			GL15.glBufferData(this.getBufferTarget(), buffer, this.hint);
		}

		this.capacity = size;
	}

	@Override
	public void bufferSubData(ByteBuffer buffer, int offset, int writingOffset, int size) {
		this.bind();

		if (buffer != null) {
			((Buffer) buffer).position(offset);
			((Buffer) buffer).limit(offset + size);
			GL15.glBufferSubData(this.getBufferTarget(), writingOffset, buffer);
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
		this.bufferData(UnsafeUtil.NULL, 0, 0);
		this.unbind();
	}

	@Override
	public void bind() {
		GL15.glBindBuffer(this.getBufferTarget(), this.id);
	}

	@Override
	public void unbind() {
		GL15.glBindBuffer(this.getBufferTarget(), 0);
	}

	@Override
	public void delete() {
		this.clear();

		GL15.glDeleteBuffers(this.id);
		this.id = -1;
	}

	@Override
	public BufferType getBufferType() {
		return BufferType.SHADER_STORAGE_BUFFER;
	}

	public void bindBase() {
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, this.bindingIndex, this.id);
	}
}
