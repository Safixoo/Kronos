package turniplabs.examplemod.client.util.interfaces;

public interface IVertexOperation {
	void setupBufferState(int amount, int type, int stride, int offset);
	void cleanupBufferState();
}
