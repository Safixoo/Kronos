package turniplabs.examplemod.client.vertex.operations;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import turniplabs.examplemod.client.util.interfaces.IVertexOperation;

public class VertexOperations {
	public static final IVertexOperation POSITION = new PositionOperation();
	public static final IVertexOperation TEXTURE = new UVOperation();
	public static final IVertexOperation COLOR = new ColorOperation();
	public static final IVertexOperation NORMAL = new NormalOperation();
	public static final IVertexOperation LIGHTMAP = new LightOperation();

	private static class PositionOperation implements IVertexOperation {
		@Override
		public void setupBufferState(int amount, int type, int stride, int offset) {
			GL11.glVertexPointer(amount, type, stride, offset);
			GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		}

		@Override
		public void cleanupBufferState() {
			GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		}
	}

	private static class UVOperation implements IVertexOperation {
		@Override
		public void setupBufferState(int amount, int type, int stride, int offset) {
			GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
			GL11.glTexCoordPointer(amount, type, stride, offset);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}

		@Override
		public void cleanupBufferState() {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
	}

	private static class ColorOperation implements IVertexOperation {
		@Override
		public void setupBufferState(int amount, int type, int stride, int offset) {
			GL11.glColorPointer(amount, type, stride, offset);
			GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		}

		@Override
		public void cleanupBufferState() {
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		}
	}

	private static class NormalOperation implements IVertexOperation {
		@Override
		public void setupBufferState(int amount, int type, int stride, int offset) {
			GL11.glNormalPointer(type, stride, offset);
			GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
		}

		@Override
		public void cleanupBufferState() {
			GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
		}
	}

	private static class LightOperation implements IVertexOperation {
		@Override
		public void setupBufferState(int amount, int type, int stride, int offset) {

		}

		@Override
		public void cleanupBufferState() {

		}
	}
}
