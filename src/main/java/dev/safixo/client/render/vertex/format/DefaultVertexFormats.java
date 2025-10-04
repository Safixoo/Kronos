package dev.safixo.client.render.vertex.format;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.vertex.operations.VertexAttribute;
import dev.safixo.client.render.vertex.operations.VertexAttributeType;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

public class DefaultVertexFormats {
	public static final VertexAttribute POSITION = new VertexAttribute(2, false, VertexAttribute.Type.UINT, VertexAttributeType.INTEGER); // 8 bytes
	public static final VertexAttribute TEXTURE = new VertexAttribute(2, false, VertexAttribute.Type.USHORT, VertexAttributeType.FLOAT); // 4 bytes
	public static final VertexAttribute COLOR = new VertexAttribute(3, true, VertexAttribute.Type.UBYTE, VertexAttributeType.FLOAT); // 4 bytes
	public static final VertexAttribute LIGHTMAP = new VertexAttribute(1, false, VertexAttribute.Type.UBYTE, VertexAttributeType.INTEGER);

	public static final VertexFormat TERRAIN_FORMAT = new TerrainFormat(
		new ImmutableList.Builder<VertexAttribute>()
			.add(POSITION)
			.add(TEXTURE)
			.add(COLOR)
			.add(LIGHTMAP)
			.build()
	);
}
