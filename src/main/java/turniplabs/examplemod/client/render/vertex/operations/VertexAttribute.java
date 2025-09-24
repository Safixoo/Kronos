package turniplabs.examplemod.client.render.vertex.operations;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

public class VertexAttribute {
	private final VertexAttributeType type;
	private final Type primitiveType;
	private final boolean normalized;
	private final int amount;

	public VertexAttribute(int amount, boolean normalized, Type primitiveType, VertexAttributeType type) {
		this.type = type;
		this.primitiveType = primitiveType;
		this.normalized = normalized;
		this.amount = amount;
	}

	public int getTypeAmount() {
		return this.amount;
	}

	public int getTypeSize() {
		return this.primitiveType.getBytes();
	}

	public int getTotalSize() {
		return this.getTypeSize() * this.amount;
	}

	public void disableAttribute(int index) {
		this.type.disableAttributeType(index);
	}

	public void setupAttribute(int index, int stride, int strideOffset) {
		this.type.setupAttributeType(index, this.primitiveType.getGlId(), this.amount, this.normalized, stride, strideOffset);
	}

	public enum Type {
		FLOAT(4, GL15.GL_FLOAT),
		SHORT(2, GL15.GL_SHORT),
		USHORT(2, GL15.GL_UNSIGNED_SHORT),
		HALF_FLOAT(2, GL33.GL_HALF_FLOAT),
		UBYTE(1, GL15.GL_UNSIGNED_BYTE);

		final int bytes, glId;

		Type(int bytes, int glId) {
			this.bytes = bytes;
			this.glId = glId;
		}

		public int getBytes() {
			return this.bytes;
		}

		public int getGlId() {
			return this.glId;
		}
	}

}
