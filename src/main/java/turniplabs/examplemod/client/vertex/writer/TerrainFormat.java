package turniplabs.examplemod.client.vertex.writer;

import com.google.common.collect.ImmutableList;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.vertex.format.VertexAttribute;
import turniplabs.examplemod.client.vertex.format.VertexFormat;

// In difference with modern Minecraft, beta seems to not use a lighting
// attribute (???), it simply uses lighting baked in color attribute.
// Which makes the difference in whereas not using lighting baked in a
// texture it doesn't produces as smoothly chunk transitions as it has
// to redo the vertex data to change lighting, which makes the rare transitions
// at the morning/afternoon of the game.
public class TerrainFormat extends VertexFormat {
	public static final int STRIDE = 24;

	public TerrainFormat(ImmutableList<VertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		MemoryUtil.memPutFloat(ptr + 0, man.x + man.trasX);
		MemoryUtil.memPutFloat(ptr + 4, man.y + man.trasY);
		MemoryUtil.memPutFloat(ptr + 8, man.z + man.trasZ);

		MemoryUtil.memPutFloat(ptr + 12, man.u);
		MemoryUtil.memPutFloat(ptr + 16, man.v);

		MemoryUtil.memPutInt(ptr + 20, man.color);
	}

	@Override
	public int getStride() {
		return STRIDE;
	}
}
