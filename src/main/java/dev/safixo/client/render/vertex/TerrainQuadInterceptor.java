package dev.safixo.client.render.vertex;

import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.LightPipeline;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.QuadLightData;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.MeshDirection;
import net.minecraft.block.Block;
import org.joml.Vector3i;

public class TerrainQuadInterceptor implements IQuadReceiver {
	private final QuadLightData out = new QuadLightData();

	private LightPipeline pipeline;
	private SectionCache cache;
	private ModelColorizer colorizer;

	private VertexWriter[] solidWriters;
	private VertexWriter translucentWriter;

	private Vector3i pos;
	private int blockColor;

	public void setContext(ModelColorizer colorizer, SectionCache cache, LightPipeline pipe) {
		this.pipeline = pipe;
		this.cache = cache;
		this.colorizer = colorizer;
	}

	public void setCursor(Block block, Vector3i pos) {
		this.pos = pos;

		int blockColor = this.colorizer.getColor(this.cache, this.pos.x, this.pos.y, this.pos.z, block);
		this.blockColor = ColorBGRManager.rgbToBgr(blockColor);
	}

	public void setWriters(VertexWriter[] solidWriters, VertexWriter translucentWriter) {
		this.solidWriters = solidWriters;
		this.translucentWriter = translucentWriter;
	}

	@Override
	public int writeQuad(Quad quad, long ptr) {
		boolean shade = false;

		for (int i = 0; i < 4; i++) {
			if (quad.getColor(i) != 0xFFFFFFFF) {
				shade = true;
				break;
			}
		}

		this.pipeline.calculate(quad, this.pos, this.out, quad.getNormalEnum(), shade);

		for (int i = 0; i < 4; i++) {
			quad.setLight(i, this.out.lm[i]);
			quad.setColor(i, ColorBGRManager.multiplyColor(this.blockColor, this.out.br[i]));
		}

		// If the quad normal is aligned, reassign the quad to a different vertex writer for block-face culling purposes.
		if (quad.getNormalEnum() != MeshDirection.GENERIC && this.translucentWriter.getCurrentQuad() != quad) {
			VertexWriter writer = this.solidWriters[quad.getNormalEnum()];

			int stride = DefaultVertexFormats.TERRAIN_FORMAT.getStride();
			writer.ensureCapacity(stride * 4);

			writer.writeQuad(quad);
			writer.vertices += 4;
			return 0;
		}

		return DefaultVertexFormats.TERRAIN_FORMAT.writeQuad(quad, ptr);
	}

	@Override
	public int getStride() {
		return DefaultVertexFormats.TERRAIN_FORMAT.getStride();
	}
}
