package turniplabs.examplemod.client.vertex.writer;

import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.util.interfaces.IVertexWriter;

import java.nio.ByteBuffer;

// In difference with modern Minecraft, beta seems to not use a lighting
// attribute (???), it simply uses lighting baked in color attribute.
// Which makes the difference in whereas not using lighting baked in a
// texture it doesn't produces as smoothly chunk transitions as it has
// to redo the vertex data to change lighting, which makes the rare transitions
// at the morning/afternoon of the game.
public class TerrainVertexWriter implements IVertexWriter {
	public static final int STRIDE = 24;
	public static final IVertexWriter WRITER = new TerrainVertexWriter();

	@Override
	public void writeVertex(ByteBuffer buffer, int offset) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();
		long bufferPtr = MemoryUtil.memAddress(buffer);

		MemoryUtil.memPutFloat(bufferPtr + 0, man.x + man.transX);
		MemoryUtil.memPutFloat(bufferPtr + 4, man.y + man.transY);
		MemoryUtil.memPutFloat(bufferPtr + 8, man.z + man.transZ);

		MemoryUtil.memPutFloat(bufferPtr + 12, man.u);
		MemoryUtil.memPutFloat(bufferPtr + 16, man.v);

		MemoryUtil.memPutInt(bufferPtr + 20, man.color);

		buffer.position(buffer.position() + STRIDE);

		//buffer.putInt(offset + 24, man.lightMap);
	}

	@Override
	public void writeVertex(long buffer, long offset) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();
		offset += buffer;

		MemoryUtil.memPutFloat(offset + 0L, man.x + man.transX);
		MemoryUtil.memPutFloat(offset + 4L, man.y + man.transY);
		MemoryUtil.memPutFloat(offset + 8L, man.z + man.transZ);

		MemoryUtil.memPutFloat(offset + 12, man.u);
		MemoryUtil.memPutFloat(offset + 16, man.v);

		MemoryUtil.memPutInt(offset + 20, man.color);
	}

	@Override
	public int getStride() {
		return STRIDE;
	}
}
