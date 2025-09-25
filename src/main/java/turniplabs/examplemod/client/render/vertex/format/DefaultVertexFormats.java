package turniplabs.examplemod.client.render.vertex.format;

import com.google.common.collect.ImmutableList;
import turniplabs.examplemod.client.render.vertex.operations.VertexAttribute;
import turniplabs.examplemod.client.render.vertex.operations.VertexAttributeType;
import turniplabs.examplemod.client.render.vertex.writers.TerrainFormat;

public class DefaultVertexFormats {
	public static final VertexAttribute POSITION = new VertexAttribute(2, false, VertexAttribute.Type.UINT, VertexAttributeType.INTEGER);
	public static final VertexAttribute TEXTURE = new VertexAttribute(2, false, VertexAttribute.Type.USHORT, VertexAttributeType.FLOAT);
	public static final VertexAttribute COLOR = new VertexAttribute(4, true, VertexAttribute.Type.UBYTE, VertexAttributeType.FLOAT);
	//public static final VertexAttribute LIGHTMAP = new VertexAttribute(2, VertexAttribute.Type.USHORT, VertexOperations.LIGHTMAP);

	public static final VertexFormat TERRAIN_FORMAT = new TerrainFormat(
		new ImmutableList.Builder<VertexAttribute>()
			.add(POSITION)
			.add(TEXTURE)
			.add(COLOR)
			.build()
	);
}
