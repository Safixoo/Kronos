package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.LightPipeline;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.QuadLightData;
import dev.safixo.client.render.vertex.TerrainQuadInterceptor;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFluid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.Icon;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableInt;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;

// Ported from Sodium 0.5
public class FluidMesher {
	private static final float PI_2 = (float) (Math.PI / 2D);

	public static final float EPSILON = 0.0001f;
	private static final float ALIGNED_EQUALS_EPSILON = 0.011f;

	private final Vector3i blockPos = new Vector3i();
	private final MutableFloat scratchHeight = new MutableFloat(0);
	private final MutableInt scratchSamples = new MutableInt();
	private final Vector3f tempVector = new Vector3f();

	private final QuadLightData out = new QuadLightData();

	private int posX, posY, posZ;

	public FluidMesher() {

	}

	protected int getEffectiveFlowDecay(Material blockMaterial, SectionCache cache, int x, int y, int z) {
		if (cache.getBlockMaterial(x, y, z) != blockMaterial) {
			return -1;
		} else {
			int meta = cache.getBlockMetadata(x, y, z);

			if (meta >= 8) {
				meta = 0;
			}

			return meta;
		}
	}

	private Vector3f getFlowVector(SectionCache cache, Block block, int x, int y, int z) {
		Vector3f vec = this.tempVector.set(0, 0, 0);
		int flowLocal = this.getEffectiveFlowDecay(block.blockMaterial, cache, x, y, z);

		for (Vector2i dir : Direction.HORIZONTAL) {
			int dirX = dir.x + x;
			int dirZ = dir.y + z;

			int flowSide = this.getEffectiveFlowDecay(block.blockMaterial, cache, dirX, y, dirZ);

			if (flowSide < 0) {
				if (!cache.getBlockMaterial(dirX, y, dirZ).blocksMovement()) {
					flowSide = this.getEffectiveFlowDecay(block.blockMaterial, cache, dirX, y - 1, dirZ);

					if (flowSide >= 0) {
						int flowDiff = flowSide - (flowLocal - 8);
						vec.add(dir.x * flowDiff, 0, dir.y * flowDiff);
					}
				}
			} else {
				int flowDiff = flowSide - flowLocal;
				vec.add(dir.x * flowDiff, 0, dir.y * flowDiff);
			}
		}

		if (cache.getBlockMetadata(x, y, z) >= 8) {
			for (int dir = 0; dir < Direction.COUNT; dir++) {
				Vector3i dirVec = Direction.getDirection(dir);

				if (block.isBlockSolid(cache, x + dirVec.x, y + dirVec.y, z + dirVec.z, dir)) {
					vec.add(0.0F, -6.0F, 0.0F);
					break;
				}
			}
		}

		return vec;
	}

	private boolean isFluidOccluded(SectionCache cache, int x, int y, int z, int side, BlockFluid block) {
		int blockId = cache.getBlockId(x, y, z);
		Material material = PrimitivesFlags.MATERIAL[blockId];

		return !(material != block.blockMaterial && (side == Direction.UP || (material != Material.ice && !PrimitivesFlags.SOLID[blockId])));
	}

	public void render(VertexWriter writer, LightPipeline pipeline, SectionCache cache, BlockFluid block, int x, int y, int z) {
		int posX = this.posX = x;
		int posY = this.posY = y;
		int posZ = this.posZ = z;

		this.blockPos.set(x, y, z);

		boolean sfUp = this.isFluidOccluded(cache, posX, posY + 1, posZ, Direction.UP, block);
		boolean sfDown = this.isFluidOccluded(cache, posX, posY - 1, posZ, Direction.DOWN, block);
		boolean sfNorth = this.isFluidOccluded(cache, posX, posY, posZ - 1, Direction.NORTH, block);
		boolean sfSouth = this.isFluidOccluded(cache, posX, posY, posZ + 1, Direction.SOUTH, block);
		boolean sfWest = this.isFluidOccluded(cache, posX - 1, posY, posZ, Direction.WEST, block);
		boolean sfEast = this.isFluidOccluded(cache, posX + 1, posY, posZ, Direction.EAST, block);

		int meta = cache.getBlockMetadata(posX, posY, posZ);

		if (sfUp && sfDown && sfEast && sfWest && sfNorth && sfSouth) {
			return;
		}

		ModelColorizer colorizer = cache.getColorizer();
		int color = ColorBGRManager.rgbToBgr(colorizer.getColor(cache, x, y, z, block));

		float fluidHeight = this.fluidHeight(cache, block, posX, posY, posZ);
		float northWestHeight, southWestHeight, southEastHeight, northEastHeight;
		if (fluidHeight >= 1.0f) {
			northWestHeight = 1.0f;
			southWestHeight = 1.0f;
			southEastHeight = 1.0f;
			northEastHeight = 1.0f;
		} else {
			float heightNorth = this.fluidHeight(cache, block, x, y, z - 1);
			float heightSouth = this.fluidHeight(cache, block, x, y, z + 1);
			float heightEast = this.fluidHeight(cache, block, x + 1, y, z);
			float heightWest = this.fluidHeight(cache, block, x - 1, y, z);
			northWestHeight = this.fluidCornerHeight(cache, block, fluidHeight, heightNorth, heightWest, posX - 1, posY, posZ - 1);
			southWestHeight = this.fluidCornerHeight(cache, block, fluidHeight, heightSouth, heightWest, posX - 1, posY, posZ + 1);
			southEastHeight = this.fluidCornerHeight(cache, block, fluidHeight, heightSouth, heightEast, posX + 1, posY, posZ + 1);
			northEastHeight = this.fluidCornerHeight(cache, block, fluidHeight, heightNorth, heightEast, posX + 1, posY, posZ - 1);
		}
		float yOffset = sfDown ? 0.0F : EPSILON;

		if (!sfUp) {
			northWestHeight -= EPSILON;
			southWestHeight -= EPSILON;
			southEastHeight -= EPSILON;
			northEastHeight -= EPSILON;

			Vector3f velocity = this.getFlowVector(cache, block, posX, posY, posZ);

			TextureAtlasSprite sprite;
			float u1, u2, u3, u4;
			float v1, v2, v3, v4;

			if (velocity.x == 0 && velocity.z == 0) {
				sprite = this.getBlockIconFromSideAndMetadata(block, 1, meta);
				u1 = sprite.getMinU();
				v1 = sprite.getMinV();
				u2 = u1;
				v2 = sprite.getMaxV();
				u3 = sprite.getMaxU();
				v3 = v2;
				u4 = u3;
				v4 = v1;
			} else {
				sprite = this.getBlockIconFromSideAndMetadata(block, 2, meta);
				float dir = MathExt.fastAtan2(velocity.z, velocity.x) - PI_2;
				float sin = (float) Math.sin(dir) * 0.25F;
				float cos = (float) Math.cos(dir) * 0.25F;
				u1 = getU(sprite, 0.5F + (-cos - sin));
				v1 = getV(sprite, 0.5F + -cos + sin);
				u2 = getU(sprite, 0.5F + -cos + sin);
				v2 = getV(sprite, 0.5F + cos + sin);
				u3 = getU(sprite, 0.5F + cos + sin);
				v3 = getV(sprite, 0.5F + (cos - sin));
				u4 = getU(sprite, 0.5F + (cos - sin));
				v4 = getV(sprite, 0.5F + (-cos - sin));
			}

			// top surface alignedness is calculated with a more relaxed epsilon
			boolean aligned = isAlignedEquals(northEastHeight, northWestHeight)
				&& isAlignedEquals(northWestHeight, southEastHeight)
				&& isAlignedEquals(southEastHeight, southWestHeight)
				&& isAlignedEquals(southWestHeight, northEastHeight);

			boolean creaseNorthEastSouthWest = aligned
				|| northEastHeight > northWestHeight && northEastHeight > southEastHeight
				|| northEastHeight < northWestHeight && northEastHeight < southEastHeight
				|| southWestHeight > northWestHeight && southWestHeight > southEastHeight
				|| southWestHeight < northWestHeight && southWestHeight < southEastHeight;

			this.setColor(writer, color);

			if (creaseNorthEastSouthWest) {
				this.setVertex(writer, 1.0F, northEastHeight, 0.0f, u4, v4);
				this.setVertex(writer, 0.0f, northWestHeight, 0.0f, u1, v1);
				this.setVertex(writer, 0.0f, southWestHeight, 1.0F, u2, v2);
				this.setVertex(writer, 1.0F, southEastHeight, 1.0F, u3, v3);
			} else {
				this.setVertex(writer, 0.0f, northWestHeight, 0.0f, u1, v1);
				this.setVertex(writer, 0.0f, southWestHeight, 1.0F, u2, v2);
				this.setVertex(writer, 1.0F, southEastHeight, 1.0F, u3, v3);
				this.setVertex(writer, 1.0F, northEastHeight, 0.0f, u4, v4);
			}
			this.bufferQuad(pipeline, writer);
		}

		if (!sfDown) {
			TextureAtlasSprite sprite = this.getBlockIconFromSideAndMetadata(block, Direction.DOWN, 0);

			float minU = sprite.getMinU();
			float maxU = sprite.getMaxU();
			float minV = sprite.getMinV();
			float maxV = sprite.getMaxV();

			this.setColor(writer, color);

			this.setVertex(writer, 0.0f, yOffset, 1.0F, minU, maxV);
			this.setVertex(writer, 0.0f, yOffset, 0.0f, minU, minV);
			this.setVertex(writer, 1.0F, yOffset, 0.0f, maxU, minV);
			this.setVertex(writer, 1.0F, yOffset, 1.0F, maxU, maxV);
			this.bufferQuad(pipeline, writer);
		}

		for (int dir = Direction.NORTH; dir < Direction.COUNT; dir++) {
			float c1;
			float c2;
			float x1;
			float z1;
			float x2;
			float z2;

			switch (dir) {
				case Direction.NORTH:
					if (sfNorth) {
						continue;
					}
					c1 = northWestHeight;
					c2 = northEastHeight;
					x1 = 0.0f;
					x2 = 1.0F;
					z1 = EPSILON;
					z2 = z1;
					break;
				case Direction.SOUTH:
					if (sfSouth) {
						continue;
					}
					c1 = southEastHeight;
					c2 = southWestHeight;
					x1 = 1.0F;
					x2 = 0.0f;
					z1 = 1.0f - EPSILON;
					z2 = z1;
					break;
				case Direction.WEST:
					if (sfWest) {
						continue;
					}
					c1 = southWestHeight;
					c2 = northWestHeight;
					x1 = EPSILON;
					x2 = x1;
					z1 = 1.0F;
					z2 = 0.0f;
					break;
				case Direction.EAST:
					if (sfEast) {
						continue;
					}
					c1 = northEastHeight;
					c2 = southEastHeight;
					x1 = 1.0f - EPSILON;
					x2 = x1;
					z1 = 0.0f;
					z2 = 1.0F;
					break;
				default:
					continue;
			}

			TextureAtlasSprite sprite = this.getBlockIconFromSideAndMetadata(block, dir, meta);

			float u1 = sprite.getMinU();
			float u2 = getU(sprite, 0.5F);
			float v1 = getV(sprite, (1.0F - c1) * 0.5F);
			float v2 = getV(sprite, (1.0F - c2) * 0.5F);
			float v3 = getV(sprite, 0.5F);

			this.setColor(writer, color);

			this.setVertex(writer, x2, c2, z2, u2, v2);
			this.setVertex(writer, x2, yOffset, z2, u2, v3);
			this.setVertex(writer, x1, yOffset, z1, u1, v3);
			this.setVertex(writer, x1, c1, z1, u1, v1);
			this.bufferQuad(pipeline, writer);
		}
	}

	private void bufferQuad(LightPipeline pipeline, VertexWriter writer) {
		Quad quad = writer.getCurrentQuad();
		pipeline.calculate(quad, this.blockPos, this.out, quad.getNormalEnum(), true);

		for (int i = 0; i < 4; i++) {
			quad.setLight(i, this.out.lm[i]);
			quad.setColor(i, ColorBGRManager.multiplyColor(writer.color, this.out.br[i]));
		}

		writer.bufferQuad();
	}

	private static boolean isAlignedEquals(float a, float b) {
		return Math.abs(a - b) <= ALIGNED_EQUALS_EPSILON;
	}

	private void setColor(VertexWriter writer, int color) {
		writer.color = color;
	}

	static float getU(TextureAtlasSprite sprite, float t) {
		return MathExt.lerp(sprite.minU, sprite.maxU, t);
	}

	static float getV(TextureAtlasSprite sprite, float t) {
		return MathExt.lerp(sprite.minV, sprite.maxV, t);
	}

	private void setVertex(VertexWriter writer, float x, float y, float z, float u, float v) {
		writer.u = u;
		writer.v = v;

		writer.addVertex(x + this.posX, y + this.posY, z + this.posZ, false);
	}

	private float fluidCornerHeight(SectionCache cache, BlockFluid fluid, float fluidHeight, float fluidHeightX, float fluidHeightY, int x, int y, int z) {
		if (fluidHeightY >= 1.0f || fluidHeightX >= 1.0f) {
			return 1.0f;
		}

		if (fluidHeightY > 0.0f || fluidHeightX > 0.0f) {
			float height = this.fluidHeight(cache, fluid, x, y, z);

			if (height >= 1.0f) {
				return 1.0f;
			}

			this.modifyHeight(this.scratchHeight, this.scratchSamples, height);
		}

		this.modifyHeight(this.scratchHeight, this.scratchSamples, fluidHeight);
		this.modifyHeight(this.scratchHeight, this.scratchSamples, fluidHeightY);
		this.modifyHeight(this.scratchHeight, this.scratchSamples, fluidHeightX);

		float result = this.scratchHeight.floatValue() / this.scratchSamples.intValue();
		this.scratchHeight.setValue(0);
		this.scratchSamples.setValue(0);

		return result;
	}

	private void modifyHeight(MutableFloat totalHeight, MutableInt samples, float target) {
		if (target >= 0.8f) {
			totalHeight.add(target * 10.0f);
			samples.add(10);
		} else if (target >= 0.0f) {
			totalHeight.add(target);
			samples.increment();
		}
	}

	private static float getFluidHeightPercent(int meta) {
		if (meta >= 8) {
			meta = 0;
		}

		return 1.0F - ((meta + 1) / 9.0F);
	}

	private float fluidHeight(SectionCache cache, BlockFluid block, int x, int y, int z) {
		int adjBlock = cache.getBlockId(x, y, z);
		Material materialAdj = PrimitivesFlags.MATERIAL[adjBlock];

		if (block.blockMaterial == materialAdj) {
			int fluidStateUp = cache.getBlockId(x, y + 1, z);
			Material materialUp = PrimitivesFlags.MATERIAL[fluidStateUp];

			if (materialAdj == materialUp) {
				return 1.0f;
			} else {
				return getFluidHeightPercent(cache.getBlockMetadata(x, y, z));
			}
		}
		if (!materialAdj.isSolid()) {
			return 0.0f;
		}
		return -1.0f;
	}

	public TextureAtlasSprite getBlockIconFromSideAndMetadata(Block block, int side, int meta) {
		return (TextureAtlasSprite) this.getIconSafe(block.getIcon(side, meta));
	}

	public Icon getIconSafe(Icon icon) {
		if (icon == null) {
			icon = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).getAtlasSprite("missingno");
		}

		return icon;
	}
}
