package turniplabs.examplemod.client.render.meshing;

import net.minecraft.client.render.LightmapHelper;
import net.minecraft.client.render.RenderBlockCache;
import net.minecraft.client.render.block.color.BlockColor;
import net.minecraft.client.render.block.color.BlockColorDispatcher;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelGrass;
import net.minecraft.client.render.block.model.BlockModelLeaves;
import net.minecraft.client.render.tessellator.Tessellator;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.core.block.Block;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.util.phys.AABB;
import org.spongepowered.asm.mixin.Unique;
import turniplabs.examplemod.client.render.data.BlockLightCache;
import turniplabs.examplemod.client.util.BlocksFlags;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.render.data.ModelBoundsData;
import turniplabs.examplemod.client.render.data.SectionCache;
import turniplabs.examplemod.client.util.ColorBGRManager;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.interfaces.mixin.IBlockAABB;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

public class BlockRenderer {
	public BlockLightCache cache = new BlockLightCache();
	private SectionCache chunkCache;

	private boolean[] facesBlock;
	private boolean useColor;

	public int colorTopLeft;
	public int colorTopRight;
	public int colorBottomLeft;
	public int colorBottomRight;

	public int lightMapCoordTopLeft;
	public int lightMapCoordBottomLeft;
	public int lightMapCoordBottomRight;
	public int lightMapCoordTopRight;

	private boolean isLeaves;

	private final ModelBoundsData modelData = new ModelBoundsData();
	private static final float[] SIDE_LIGHT_MULTIPLIER = new float[] {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};

	private void setModelBounds(int x, int y, int z, AABB bounds) {
		this.modelData.setBoundsData(bounds.minX + x, bounds.minY + y, bounds.minZ + z, bounds.maxX + x, bounds.maxY + y, bounds.maxZ + z);
		this.modelData.setBoundsDataExtra(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
		this.modelData.setRender(this);
	}

	public void renderStandardBlock(Block<?> block, BlockColor blockColor, BlockModel<?> blockModel, AABB bounds, int x, int y, int z) {
		int color = blockColor.getWorldColor(this.chunkCache, x, y, z);

		this.cache.setupCache(this.chunkCache, x, y, z);
		this.facesBlock = ((IBlockAABB) bounds).blockBoundsCheck();
		this.setModelBounds(x, y, z, bounds);

		boolean isGrass = blockModel instanceof BlockModelGrass;
		boolean isLeaves = this.isLeaves = blockModel instanceof BlockModelLeaves;

		for (int side = 0; side < Direction.COUNT; side++) {
			if (isGrass || isLeaves) {
				int meta = this.chunkCache.getBlockMetadata(x, y, z);
				this.useColor = blockModel.shouldSideBeColored(this.chunkCache, x, y, z, side, meta);
			}

			this.renderSideFaceAll(block, blockModel, this.modelData, x, y, z, side, ColorBGRManager.rgbToBgr(color));
		}

//		if (isGrass) {
//			BlockModelGrass.useOverlay = true;
//			blockModel.renderStandardBlock(Tessellator.instance, blockModel.block.getBoundsRaw(), x, y, z);
//			BlockModelGrass.useOverlay = false;
//		}
	}

	public void setChunkCache(SectionCache chunkCache) {
		this.chunkCache = chunkCache;
	}

	private boolean shouldDrawSide(int x, int y, int z, int side) {
		return this.facesBlock[side] || !this.chunkCache.isBlockOpaqueCube(x, y, z);
	}

	private void renderSideFaceAll(Block<?> block, BlockModel<?> blockModel, ModelBoundsData bounds, int x, int y, int z, int side, int color) {
		int dirX = Direction.x(side);
		int dirY = Direction.y(side);
		int dirZ = Direction.z(side);

		if (this.shouldDrawSide(x + dirX, y + dirY, z + dirZ, side)) {
			IconCoordinate tex = blockModel.getBlockTexture(this.chunkCache, x, y, z, Side.sides[side]);

			VertexWriterManager.getCurrentInstance().ensureCapacity(TerrainVertexWriter.STRIDE * 4);
			FaceWriterWrapper quadWriter = FaceDataWriters.getWriterBySide(side);

			float minU = (float) tex.getIconUMin();
			float maxU = (float) tex.getIconUMax();
			float minV = (float) tex.getIconVMin();
			float maxV = (float) tex.getIconVMax();

			quadWriter.setUV(maxU, minV, minU, maxV);
			quadWriter.colorizeAndBufferQuad(this, bounds, block, x, y, z, color);
		}
	}

	public void colorizeQuad(Block<?> block, int x, int y, int z, int side, int dirX, int dirY, int dirZ, float depth, int topX, int topY, int topZ, float topP, float botP, int lefX, int lefY, int lefZ, float lefP, float rigP, int color) {
		if (!this.useColor) {
			color = 0xFFFFFFFF;
		}

		if (LightmapHelper.isLightmapEnabled()) {
			this.prepareLightMap(block, block.emission == 0, x, y, z, dirX, dirY, dirZ, lefX, lefY, lefZ, topX, topY, topZ);
		}

		float lightTR = 1.0f;
		float lightBR = 1.0f;
		float lightBL = 1.0f;
		float lightTL = 1.0f;

		if (!this.isLeaves && block.emission == 0) {
			float dirB = this.cache.getBrightness(dirX, dirY, dirZ);
			boolean lefT = this.cache.getOpacity(dirX + lefX, dirY + lefY, dirZ + lefZ);
			boolean botT = this.cache.getOpacity(dirX - topX, dirY - topY, dirZ - topZ);
			boolean topT = this.cache.getOpacity(dirX + topX, dirY + topY, dirZ + topZ);
			boolean rigT = this.cache.getOpacity(dirX - lefX, dirY - lefY, dirZ - lefZ);
			float lB = this.cache.getBrightness(dirX + lefX, dirY + lefY, dirZ + lefZ);
			float bB = this.cache.getBrightness(dirX - topX, dirY - topY, dirZ - topZ);
			float tB = this.cache.getBrightness(dirX + topX, dirY + topY, dirZ + topZ);
			float rB = this.cache.getBrightness(dirX - lefX, dirY - lefY, dirZ - lefZ);
			float blB = botT && lefT ? lB : this.cache.getBrightness(dirX + lefX - topX, dirY + lefY - topY, dirZ + lefZ - topZ);
			float tlB = topT && lefT ? lB : this.cache.getBrightness(dirX + lefX + topX, dirY + lefY + topY, dirZ + lefZ + topZ);
			float brB = botT && rigT ? rB : this.cache.getBrightness(dirX - lefX - topX, dirY - lefY - topY, dirZ - lefZ - topZ);
			float trB = topT && rigT ? rB : this.cache.getBrightness(dirX - lefX + topX, dirY - lefY + topY, dirZ - lefZ + topZ);

			lightTL = (tlB + lB + tB + dirB) * 0.25F;
			lightTR = (tB + dirB + trB + rB) * 0.25F;
			lightBR = (dirB + bB + rB + brB) * 0.25F;
			lightBL = (lB + blB + dirB + bB) * 0.25F;

			if (!block.isSolidRender()) {
				dirB = this.cache.getBrightness(0, 0, 0);
				lefT = this.cache.getOpacity(lefX, lefY, lefZ);
				botT = this.cache.getOpacity(-topX, -topY, -topZ);
				topT = this.cache.getOpacity(topX, topY, topZ);
				rigT = this.cache.getOpacity(-lefX, -lefY, -lefZ);
				lB = this.cache.getBrightness(lefX, lefY, lefZ);
				bB = this.cache.getBrightness(-topX, -topY, -topZ);
				tB = this.cache.getBrightness(topX, topY, topZ);
				rB = this.cache.getBrightness(-lefX, -lefY, -lefZ);
				blB = botT && lefT ? lB : this.cache.getBrightness(lefX - topX, lefY - topY, lefZ - topZ);
				tlB = topT && lefT ? lB : this.cache.getBrightness(lefX + topX, lefY + topY, lefZ + topZ);
				brB = botT && rigT ? rB : this.cache.getBrightness(-lefX - topX, -lefY - topY, -lefZ - topZ);
				trB = topT && rigT ? rB : this.cache.getBrightness(-lefX + topX, -lefY + topY, -lefZ + topZ);
				lightTL = (tlB + lB + tB + dirB) * 0.25F * lerp(depth, 1.0F, lightTL);
				lightTR = (tB + dirB + trB + rB) * 0.25F * lerp(depth, 1.0F, lightTR);
				lightBR = (dirB + bB + rB + brB) * 0.25F * lerp(depth, 1.0F, lightBR);
				lightBL = (lB + blB + dirB + bB) * 0.25F * lerp(depth, 1.0F, lightBL);

			}
		} else if (!this.isLeaves) {
			float brightness;

			if (!block.isSolidRender()) {
				brightness = this.cache.getBrightness(0, 0, 0);
			} else {
				brightness = this.cache.getBrightness(dirX, dirY, dirZ);
			}

			lightTR = brightness;
			lightBR = brightness;
			lightBL = brightness;
			lightTL = brightness;
		}

		color = ColorBGRManager.multiplyColor(color, SIDE_LIGHT_MULTIPLIER[side]);

		float tl = topP * lightTL + (1.0F - topP) * lightBL;
		float tr = topP * lightTR + (1.0F - topP) * lightBR;
		float bl = botP * lightTL + (1.0F - botP) * lightBL;
		float br = botP * lightTR + (1.0F - botP) * lightBR;
		float ltl = lefP * tl + (1.0F - lefP) * tr;
		float lbl = lefP * bl + (1.0F - lefP) * br;
		float lbr = rigP * bl + (1.0F - rigP) * br;
		float ltr = rigP * tl + (1.0F - rigP) * tr;

		color &= 0x00_FF_FF_FF;

		this.colorTopLeft = ColorBGRManager.multiplyColor(color, ltl);
		this.colorBottomLeft = ColorBGRManager.multiplyColor(color, lbl);
		this.colorBottomRight = ColorBGRManager.multiplyColor(color, lbr);
		this.colorTopRight = ColorBGRManager.multiplyColor(color, ltr);
	}


	private void prepareLightMap(Block<?> block, boolean shouldAo, int x, int y, int z, int dirX, int dirY, int dirZ, int lefX, int lefY, int lefZ, int topX, int topY, int topZ) {
		if (shouldAo) {
			if (!block.isSolidRender()) {
				dirX = 0;
				dirY = 0;
				dirZ = 0;
			}

			boolean topT = this.cache.getOpacity(dirX + topX, dirY + topY, dirZ + topZ);
			boolean botT = this.cache.getOpacity(dirX - topX, dirY - topY, dirZ - topZ);
			boolean lefT = this.cache.getOpacity(dirX + lefX, dirY + lefY, dirZ + lefZ);
			boolean rigT = this.cache.getOpacity(dirX - lefX, dirY - lefY, dirZ - lefZ);

			boolean topLefT = this.cache.getOpacity(dirX + topX + lefX, dirY + topY + lefY, dirZ + topZ + lefZ);
			boolean topRigT = this.cache.getOpacity(dirX + topX - lefX, dirY + topY - lefY, dirZ + topZ - lefZ);
			boolean botLefT = this.cache.getOpacity(dirX - topX + lefX, dirY - topY + lefY, dirZ - topZ + lefZ);
			boolean botRigT = this.cache.getOpacity(dirX - topX - lefX, dirY - topY - lefY, dirZ - topZ - lefZ);

			int lmcCen = this.cache.getLightmapCoord(dirX, dirY, dirZ);
			int lmcTop = topT ? lmcCen : this.cache.getLightmapCoord(dirX + topX, dirY + topY, dirZ + topZ);
			int lmcBot = botT ? lmcCen : this.cache.getLightmapCoord(dirX - topX, dirY - topY, dirZ - topZ);
			int lmcLef = lefT ? lmcCen : this.cache.getLightmapCoord(dirX + lefX, dirY + lefY, dirZ + lefZ);
			int lmcRig = rigT ? lmcCen : this.cache.getLightmapCoord(dirX - lefX, dirY - lefY, dirZ - lefZ);

			int lmcTopLef = topT && lefT ? lmcLef : (topLefT ? lmcCen : this.cache.getLightmapCoord(dirX + topX + lefX, dirY + topY + lefY, dirZ + topZ + lefZ));
			int lmcBotLef = botT && lefT ? lmcLef : (botLefT ? lmcCen : this.cache.getLightmapCoord(dirX - topX + lefX, dirY - topY + lefY, dirZ - topZ + lefZ));
			int lmcTopRig = topT && rigT ? lmcRig : (topRigT ? lmcCen : this.cache.getLightmapCoord(dirX + topX - lefX, dirY + topY - lefY, dirZ + topZ - lefZ));
			int lmcBotRig = botT && rigT ? lmcRig : (botRigT ? lmcCen : this.cache.getLightmapCoord(dirX - topX - lefX, dirY - topY - lefY, dirZ - topZ - lefZ));

			this.lightMapCoordTopLeft = LightmapHelper.avg(lmcCen, lmcLef, lmcTop, lmcTopLef);
			this.lightMapCoordTopRight = LightmapHelper.avg(lmcCen, lmcRig, lmcTop, lmcTopRig);
			this.lightMapCoordBottomLeft = LightmapHelper.avg(lmcCen, lmcLef, lmcBot, lmcBotLef);
			this.lightMapCoordBottomRight = LightmapHelper.avg(lmcCen, lmcRig, lmcBot, lmcBotRig);
		} else {
			int lmc = block.getLightmapCoord(this.chunkCache, x + dirX, y + dirY, z + dirZ);
			this.lightMapCoordTopLeft = this.lightMapCoordBottomLeft = this.lightMapCoordBottomRight = this.lightMapCoordTopRight = lmc;
		}
	}

	private void renderFacingFace(ModelBoundsData data, IconCoordinate tex, FaceWriterWrapper writeOrder) {

	}

	@Unique
	private static float lerp(float a, float b, float t) {
		return a + t * (b - a);
	}
}
