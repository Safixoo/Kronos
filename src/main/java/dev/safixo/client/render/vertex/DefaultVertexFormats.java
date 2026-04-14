package dev.safixo.client.render.vertex;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttributeType;
import dev.safixo.client.render.vertex.writers.CloudFormat;
import dev.safixo.client.render.vertex.writers.EntityFormat;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

public class DefaultVertexFormats {
	public static final GlVertexAttribute POSITION_2UI = new GlVertexAttribute(2, false, GlVertexAttribute.Type.UINT, GlVertexAttributeType.INTEGER);
	public static final GlVertexAttribute TEXTURE_2US = new GlVertexAttribute(2, false, GlVertexAttribute.Type.USHORT, GlVertexAttributeType.FLOAT);

	public static final GlVertexAttribute POSITION_3F = new GlVertexAttribute(3, false, GlVertexAttribute.Type.FLOAT, GlVertexAttributeType.FLOAT);
	public static final GlVertexAttribute POSITION_3B = new GlVertexAttribute(3, false, GlVertexAttribute.Type.BYTE, GlVertexAttributeType.FLOAT);
	public static final GlVertexAttribute TEXTURE_2F = new GlVertexAttribute(2, false, GlVertexAttribute.Type.FLOAT, GlVertexAttributeType.FLOAT);

	public static final GlVertexAttribute COLOR = new GlVertexAttribute(3, true, GlVertexAttribute.Type.UBYTE, GlVertexAttributeType.FLOAT);
	public static final GlVertexAttribute COLOR_AND_LIGHTMAP = new GlVertexAttribute(4, false, GlVertexAttribute.Type.UBYTE, GlVertexAttributeType.INTEGER);

	public static final GlVertexAttribute LIGHTMAP = new GlVertexAttribute(1, false, GlVertexAttribute.Type.UBYTE, GlVertexAttributeType.INTEGER);
	public static final GlVertexAttribute NORMAL = new GlVertexAttribute(3, false, GlVertexAttribute.Type.BYTE, GlVertexAttributeType.FLOAT);



	public static final GlVertexFormat TERRAIN_FORMAT = new TerrainFormat(
		new ImmutableList.Builder<GlVertexAttribute>()
			.add(POSITION_2UI)
			.add(TEXTURE_2US)
			.add(COLOR_AND_LIGHTMAP)
			.build()
	);

	public static final GlVertexFormat ENTITY_FORMAT = new EntityFormat(
		new ImmutableList.Builder<GlVertexAttribute>()
			.add(POSITION_3F)
			.add(TEXTURE_2F)
			.add(NORMAL)
			.build()
	);

	public static final GlVertexFormat CLOUD_FORMAT = new CloudFormat(
		new ImmutableList.Builder<GlVertexAttribute>()
			.add(POSITION_3B)
			.add(COLOR)
			.build()
	);
}
