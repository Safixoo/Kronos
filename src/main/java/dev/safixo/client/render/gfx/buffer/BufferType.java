package dev.safixo.client.render.gfx.buffer;

public class BufferType {
	public static final BufferType VERTEX_BUFFER = new BufferType();
	public static final BufferType INDEX_BUFFER = new BufferType();
	public static final BufferType PERSISTENT_BUFFER = new BufferType();
	public static final BufferType SHADER_STORAGE_BUFFER = new BufferType();

	private static int BINDING_INDEX_SSBO = 0;

	BufferType() {}

	public GlBuffer instantiate(int size, int hint) {
		if (this == VERTEX_BUFFER) {
			return new GlVertexBuffer(size, hint);
		} else if (this == INDEX_BUFFER) {
			return new GlIndexBuffer(size, hint);
		} else if (this == SHADER_STORAGE_BUFFER) {
			return new GlShaderStorageBuffer(size, BINDING_INDEX_SSBO++, hint);
		} else {
			return new GlPersistentBuffer(size);
		}
	}
}
