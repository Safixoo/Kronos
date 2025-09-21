package turniplabs.examplemod.client.render.vertex.operations;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

public class VertexAttributeType {
	public static final VertexAttributeType FLOAT = new FloatAttribute();
	public static final VertexAttributeType INTEGER = new FloatAttribute();

	public void setupAttributeType(int index, int glId, int size, boolean normalized, int stride, int strideOffset) {
		throw new RuntimeException("Function not implemented!");
	}

	public void disableAttributeType(int index) {
		throw new RuntimeException("Function not implemented!");
	}

	private static class FloatAttribute extends VertexAttributeType {
		@Override
		public void setupAttributeType(int index, int glId, int size, boolean normalized, int stride, int strideOffset) {
			GL20.glVertexAttribPointer(index, size, glId, normalized, stride, strideOffset);
			GL20.glEnableVertexAttribArray(index);
		}

		@Override
		public void disableAttributeType(int index) {
			GL20.glDisableVertexAttribArray(index);
		}
	}

	private static class IntegerAttribute extends VertexAttributeType {
		@Override
		public void setupAttributeType(int index, int glId, int size, boolean normalized, int stride, int strideOffset) {
		}

		@Override
		public void disableAttributeType(int index) {
			GL20.glDisableVertexAttribArray(index);
		}
	}
}
