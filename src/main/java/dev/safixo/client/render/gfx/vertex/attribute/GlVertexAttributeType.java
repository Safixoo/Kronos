package dev.safixo.client.render.gfx.vertex.attribute;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class GlVertexAttributeType {
	public static final GlVertexAttributeType FLOAT = new FloatAttribute();
	public static final GlVertexAttributeType INTEGER = new IntegerAttribute();

	public void setupAttributeType(int index, int glId, int size, boolean normalized, int stride, int strideOffset) {
		throw new RuntimeException("Function not implemented!");
	}

	public void disableAttributeType(int index) {
		throw new RuntimeException("Function not implemented!");
	}

	private static class FloatAttribute extends GlVertexAttributeType {
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

	private static class IntegerAttribute extends GlVertexAttributeType {
		@Override
		public void setupAttributeType(int index, int glId, int size, boolean normalized, int stride, int strideOffset) {
			GL30.glVertexAttribIPointer(index, size, glId, stride, strideOffset);
			GL20.glEnableVertexAttribArray(index);
		}

		@Override
		public void disableAttributeType(int index) {
			GL20.glDisableVertexAttribArray(index);
		}
	}
}
