package turniplabs.examplemod.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.LightmapHelper;
import net.minecraft.client.render.RenderBlockCache;
import net.minecraft.client.render.RenderBlocks;
import net.minecraft.client.render.block.color.BlockColorDispatcher;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.tessellator.Tessellator;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.core.block.Block;
import net.minecraft.core.enums.LightLayer;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.util.phys.AABB;
import net.minecraft.core.world.WorldSource;
import org.spongepowered.asm.mixin.*;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.interfaces.mixin.IBlockAABB;

@Mixin(value = RenderBlocks.class, remap = false)
public abstract class RenderBlocksMixin {
	@Shadow
	public boolean enableAO;

	@Shadow
	public WorldSource blockAccess;

	@Shadow
	public RenderBlockCache cache;

	@Shadow
	public byte renderBitMask;

	@Shadow
	public abstract void renderBottomFace(Tessellator tessellator, AABB bounds, double x, double y, double z, IconCoordinate tex);

	@Shadow
	public abstract void renderTopFace(Tessellator tessellator, AABB bounds, double x, double y, double z, IconCoordinate tex);

	@Shadow
	public abstract void renderNorthFace(Tessellator tessellator, AABB bounds, double x, double y, double z, IconCoordinate tex);

	@Shadow
	public abstract void renderSouthFace(Tessellator tessellator, AABB bounds, double x, double y, double z, IconCoordinate tex);

	@Shadow
	public abstract void renderWestFace(Tessellator tessellator, AABB bounds, double x, double y, double z, IconCoordinate tex);

	@Shadow
	public abstract void renderEastFace(Tessellator tessellator, AABB bounds, double x, double y, double z, IconCoordinate tex);

	@Shadow
	public boolean overbright;
	@Shadow
	public static boolean enableDirectionalLight;
	@Shadow public float colorRedTopLeft;
	@Shadow public float colorRedBottomLeft;
	@Shadow public float colorRedBottomRight;
	@Shadow public float colorRedTopRight;
	@Shadow public float colorGreenTopLeft;
	@Shadow public float colorGreenBottomLeft;
	@Shadow public float colorGreenBottomRight;
	@Shadow public float colorGreenTopRight;
	@Shadow public float colorBlueTopLeft;
	@Shadow public float colorBlueBottomLeft;
	@Shadow public float colorBlueBottomRight;
	@Shadow public float colorBlueTopRight;
	@Shadow public int lightmapCoordTopLeft;
	@Shadow public int lightmapCoordBottomLeft;
	@Shadow public int lightmapCoordBottomRight;
	@Shadow public int lightmapCoordTopRight;
	@Shadow @Final public static float[] SIDE_LIGHT_MULTIPLIER;

	/**
	 * @author Safixo
	 * @reason Faster meshing.
	 */
	@Overwrite
	public boolean renderStandardBlock(Tessellator tessellator, BlockModel<?> blockModel, AABB bounds, int x, int y, int z) {
		int color = BlockColorDispatcher.getInstance().getDispatch(blockModel.block).getWorldColor(this.blockAccess, x, y, z);

		float r = (float) (color >>> 16 & 0xFF) * (1.0F / 255.0F);
		float g = (float) (color >>> 8 & 0xFF) * (1.0F / 255.0F);
		float b = (float) (color >>> 0 & 255) * (1.0F / 255.0F);

		return this.renderStandardBlockSmarter(blockModel, bounds, x, y, z, r, g, b);
	}

	@Unique
	public boolean renderStandardBlockSmarter(BlockModel<?> blockModel, AABB bounds, int x, int y, int z, float r, float g, float b) {
		this.facesBlock = ((IBlockAABB) bounds).blockBoundsCheck();

		this.enableAO = true;
		this.cache.setupCache(blockModel.block, this.blockAccess, x, y, z);
		int meta = this.blockAccess.getBlockMetadata(x, y, z);

		this.r = r;
		this.g = g;
		this.b = b;

		boolean rendered = false;

		for (int side = 0; side < Direction.COUNT; side++) {
			this.useColor = blockModel.shouldSideBeColored(this.blockAccess, x, y, z, side, meta);
			rendered |= this.renderSideFaceAll(blockModel, bounds, x, y, z, side);
		}

		this.enableAO = false;

		return rendered;
	}

	@Unique
	private boolean shouldDrawSide(int x, int y, int z, int side) {
		return this.facesBlock[side] || !this.blockAccess.isBlockOpaqueCube(x, y, z);
	}

	@Unique
	private boolean[] facesBlock;
	@Unique
	private float r, g, b;
	@Unique
	private boolean useColor;

	@Unique
	public boolean renderSideFaceAll(BlockModel<?> blockModel, AABB bounds, int x, int y, int z, int side) {
		int dirX = Direction.x(side);
		int dirY = Direction.y(side);
		int dirZ = Direction.z(side);

		if ((this.renderBitMask & (1 << side)) == 0 && this.shouldDrawSide(x + dirX, y + dirY, z + dirZ, side)) {
			IconCoordinate tex;
			if (this.overbright) {
				tex = blockModel.getBlockOverbrightTexture(this.blockAccess, x, y, z, side);
			} else {
				tex = blockModel.getBlockTexture(this.blockAccess, x, y, z, Side.sides[side]);
			}

			Block<?> block = blockModel.block;

			if (side == 0) {
				this.setupLightingFaster(block, x, y, z, 0, 0, -1, 0, (float) bounds.minY, 0, 0, 1, (float)bounds.maxZ, (float)bounds.minZ, -1, 0, 0, 1.0F - (float)bounds.minX, 1.0F - (float)bounds.maxX);
				this.renderBottomFace(Tessellator.instance, bounds, x, y, z, tex);
			} else if (side == 1) {
				this.setupLightingFaster(block, x, y, z, 1, 0, 1, 0, 1.0F - (float) bounds.maxY, 0, 0, 1, (float) bounds.maxZ, (float) bounds.minZ, 1, 0, 0, (float) bounds.maxX, (float) bounds.minY);
				this.renderTopFace(Tessellator.instance, bounds, x, y, z, tex);
			} else if (side == 2) {
				this.setupLightingFaster(block, x, y, z, 2, 0, 0, -1, (float) bounds.minZ, -1, 0, 0, 1.0F - (float) bounds.minX, 1.0F - (float) bounds.maxX, 0, 1, 0, (float) bounds.maxY, (float) bounds.minY);
				this.renderNorthFace(Tessellator.instance, bounds, x, y, z, tex);
			} else if (side == 3) {
				this.setupLightingFaster(block, x, y, z, 3, 0, 0, 1, 1.0F - (float) bounds.maxZ, 0, 1, 0, (float) bounds.maxY, (float) bounds.minY, -1, 0, 0, 1.0F - (float) bounds.minX, 1.0F - (float) bounds.maxX);
				this.renderSouthFace(Tessellator.instance, bounds, x, y, z, tex);
			} else if (side == 4) {
				this.setupLightingFaster(block, x, y, z, 4, -1, 0, 0, (float) bounds.minX, 0, 0, 1, (float) bounds.maxZ, (float) bounds.minZ, 0, 1, 0, (float) bounds.maxY, (float) bounds.minY);
				this.renderWestFace(Tessellator.instance, bounds, x, y, z, tex);
			} else {
				this.setupLightingFaster(block, x, y, z, 5, 1, 0, 0, 1.0F - (float) bounds.maxX, 0, 0, 1, (float) bounds.maxZ, (float) bounds.minZ, 0, -1, 0, 1.0F - (float) bounds.minY, 1.0F - (float) bounds.maxY);
				this.renderEastFace(Tessellator.instance, bounds, x, y, z, tex);
			}

			return true;
		}

		return false;
	}

	@Unique
	public void setupLightingFaster(Block<?> block, int x, int y, int z, int side, int dirX, int dirY, int dirZ, float depth, int topX, int topY, int topZ, float topP, float botP, int lefX, int lefY, int lefZ, float lefP, float rigP) {
		float r = 1.0F;
		float g = 1.0F;
		float b = 1.0F;

		if (this.useColor) {
			r = this.r;
			g = this.g;
			b = this.b;
		}

		if (LightmapHelper.isLightmapEnabled()) {
			this.prepareLightMap(block, block.emission == 0, x, y, z, dirX, dirY, dirZ, lefX, lefY, lefZ, topX, topY, topZ);
		}

		float lightTR = 1.0F;
		float lightBR = 1.0F;
		float lightBL = 1.0F;
		float lightTL = 1.0F;

		if (!this.overbright && block.emission == 0) {
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
		} else if (!this.overbright) {
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

		if (!this.overbright && enableDirectionalLight) {
			this.colorRedTopLeft = this.colorRedBottomLeft = this.colorRedBottomRight = this.colorRedTopRight = r * SIDE_LIGHT_MULTIPLIER[side];
			this.colorGreenTopLeft = this.colorGreenBottomLeft = this.colorGreenBottomRight = this.colorGreenTopRight = g * SIDE_LIGHT_MULTIPLIER[side];
			this.colorBlueTopLeft = this.colorBlueBottomLeft = this.colorBlueBottomRight = this.colorBlueTopRight = b * SIDE_LIGHT_MULTIPLIER[side];
		} else {
			this.colorRedTopLeft = this.colorRedBottomLeft = this.colorRedBottomRight = this.colorRedTopRight = r;
			this.colorGreenTopLeft = this.colorGreenBottomLeft = this.colorGreenBottomRight = this.colorGreenTopRight = g;
			this.colorBlueTopLeft = this.colorBlueBottomLeft = this.colorBlueBottomRight = this.colorBlueTopRight = b;
		}

		float tl = topP * lightTL + (1.0F - topP) * lightBL;
		float tr = topP * lightTR + (1.0F - topP) * lightBR;
		float bl = botP * lightTL + (1.0F - botP) * lightBL;
		float br = botP * lightTR + (1.0F - botP) * lightBR;
		float ltl = lefP * tl + (1.0F - lefP) * tr;
		float lbl = lefP * bl + (1.0F - lefP) * br;
		float lbr = rigP * bl + (1.0F - rigP) * br;
		float ltr = rigP * tl + (1.0F - rigP) * tr;

		this.colorRedTopLeft *= ltl;
		this.colorGreenTopLeft *= ltl;
		this.colorBlueTopLeft *= ltl;

		this.colorRedBottomLeft *= lbl;
		this.colorGreenBottomLeft *= lbl;
		this.colorBlueBottomLeft *= lbl;

		this.colorRedBottomRight *= lbr;
		this.colorGreenBottomRight *= lbr;
		this.colorBlueBottomRight *= lbr;

		this.colorRedTopRight *= ltr;
		this.colorGreenTopRight *= ltr;
		this.colorBlueTopRight *= ltr;
	}

	@Unique
	private void prepareLightMap(Block<?> block, boolean shouldAo, int x, int y, int z, int dirX, int dirY, int dirZ, int lefX, int lefY, int lefZ, int topX, int topY, int topZ) {
		if (this.overbright) {
			int dirX2 = dirX;
			int dirY2 = dirY;
			int dirZ2 = dirZ;

			if (!block.isSolidRender()) {
				dirX2 = 0;
				dirY2 = 0;
				dirZ2 = 0;
			}

			int lmc = LightmapHelper.getOverbrightLightmapCoord(this.blockAccess.getSavedLightValue(LightLayer.Sky, dirX2, dirY2, dirZ2));
			this.lightmapCoordTopLeft = this.lightmapCoordBottomLeft = this.lightmapCoordBottomRight = this.lightmapCoordTopRight = lmc;
		} else if (shouldAo) {
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

			this.lightmapCoordTopLeft = LightmapHelper.avg(lmcCen, lmcLef, lmcTop, lmcTopLef);
			this.lightmapCoordTopRight = LightmapHelper.avg(lmcCen, lmcRig, lmcTop, lmcTopRig);
			this.lightmapCoordBottomLeft = LightmapHelper.avg(lmcCen, lmcLef, lmcBot, lmcBotLef);
			this.lightmapCoordBottomRight = LightmapHelper.avg(lmcCen, lmcRig, lmcBot, lmcBotRig);
		} else {
			int lmc;

			if (!block.isSolidRender()) {
				lmc = block.getLightmapCoord(this.blockAccess, x, y, z);
			} else {
				lmc = block.getLightmapCoord(this.blockAccess, x + dirX, y + dirY, z + dirZ);
			}

			this.lightmapCoordTopLeft = this.lightmapCoordBottomLeft = this.lightmapCoordBottomRight = this.lightmapCoordTopRight = lmc;
		}
	}

	@Unique
	private static float lerp(float a, float b, float t) {
		return a + t * (b - a);
	}
}
