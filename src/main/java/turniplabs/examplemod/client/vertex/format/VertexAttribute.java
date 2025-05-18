package turniplabs.examplemod.client.vertex.format;

import org.lwjgl.opengl.GL11;
import turniplabs.examplemod.client.util.interfaces.IVertexOperation;

public class VertexAttribute {
	private final Type type;
	private final IVertexOperation operation;
	private final int amount;

	public VertexAttribute(int amount, Type type, IVertexOperation associatedOperation) {
		this.type = type;
		this.operation = associatedOperation;
		this.amount = amount;
	}

	public int getType() {
		return this.type.glId;
	}

	public int getAmount() {
		return this.amount;
	}

	public int getSize() {
		return this.type.size;
	}

	public IVertexOperation getOperation() {
		return this.operation;
	}

	public int attributeStride() {
		return this.getSize() * this.getAmount();
	}

	public void setupAttribute(int amount, int type, int stride, int offset) {
		this.operation.setupBufferState(amount, type, stride, offset);
	}

	public static class Type {
		private final int size;
		private final int glId;

		public static final Type BYTE = new Type(1, GL11.GL_BYTE);
		public static final Type UBYTE = new Type(1, GL11.GL_UNSIGNED_BYTE);
		public static final Type INT = new Type(4, GL11.GL_INT);
		public static final Type UINT = new Type(4, GL11.GL_UNSIGNED_INT);
		public static final Type FLOAT = new Type(4, GL11.GL_FLOAT);
		public static final Type SHORT = new Type(2, GL11.GL_SHORT);
		public static final Type USHORT = new Type(2, GL11.GL_UNSIGNED_SHORT);

		public int getSize() {
			return size;
		}

		public int getId() {
			return glId;
		}

		Type(int SIZE, int GL_ID) {
			size = SIZE;
			glId = GL_ID;
		}
	}
}
