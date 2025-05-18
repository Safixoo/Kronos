package turniplabs.examplemod.client.vertex.format;

import com.google.common.collect.ImmutableList;
import turniplabs.examplemod.client.vertex.operations.VertexOperations;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

public class DefaultVertexFormats {
	public static final VertexAttribute NORMAL = new VertexAttribute(3, VertexAttribute.Type.BYTE, VertexOperations.NORMAL);
	public static final VertexAttribute POSITION = new VertexAttribute(3, VertexAttribute.Type.FLOAT, VertexOperations.POSITION);
	public static final VertexAttribute TEXTURE = new VertexAttribute(2, VertexAttribute.Type.FLOAT, VertexOperations.TEXTURE);
	public static final VertexAttribute COLOR = new VertexAttribute(4, VertexAttribute.Type.UBYTE, VertexOperations.COLOR);
	//public static final VertexAttribute LIGHTMAP = new VertexAttribute(2, VertexAttribute.Type.USHORT, VertexOperations.LIGHTMAP);

	public static final VertexFormat TERRAIN_FORMAT = new VertexFormat(
		new ImmutableList.Builder<VertexAttribute>()
			.add(POSITION)
			.add(TEXTURE)
			.add(COLOR)
			.build(),
			TerrainVertexWriter.WRITER
	);
	public static final VertexFormat ENTITY_FORMAT = new VertexFormat(
		new ImmutableList.Builder<VertexAttribute>()
			.add(POSITION)
			.add(TEXTURE)
			.add(NORMAL)
			.build(),
			null
	);
}
