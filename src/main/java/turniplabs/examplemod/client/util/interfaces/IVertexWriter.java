package turniplabs.examplemod.client.util.interfaces;

import java.nio.ByteBuffer;

public interface IVertexWriter {
	void writeVertex(ByteBuffer buffer, int vertexIndex);
	void writeVertex(long buffer, long vertexIndex);
	int getStride();
}
