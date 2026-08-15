package dev.safixo.client.util.data;

import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.MathExt;
import dev.safixo.core.hooks.AsyncBlockHook;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenSwamp;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Uses multiple arrays to pre-compute flags, avoid extra indirection in hot code, and skip dynamic dispatch when
 * possible.
 */
public class PrimitivesFlags {
	public static boolean REDIRECT_DRAWING;

	public static boolean DEV_ENVIRONMENT;
	public static boolean DETECTED = false;

	public static final Material[] MATERIAL = new Material[4096];
	public static final boolean[] SOLID = new boolean[4096];
	public static final boolean[] NORMAL_BLOCK = new boolean[4096];

	public static final boolean[] VOXEL_RENDER = new boolean[4096];

	// Just as SOLID but returns {1, 0}
	public static final byte[] SOLID_CULL_MASK = new byte[4096];

	// Solid for lighting purposes.
	public static final byte[] SOLID_LIGHT_MASK = new byte[4096];

	// If Block#shouldSideBeRendered is overridden in the given Block or not.
	// (avoids a common virtual dispatch).
	public static final boolean[] DIRECT_CULL = new boolean[4096];

	public static final boolean[] TILE_ENTITY = new boolean[4096];
	public static final byte[] RENDER_PASS = new byte[4096];

	private static int LEAVES_TOP_INDEX = 0;
	private static final int[] LEAVES_INDICES = new int[4096];
	public static final byte[] COLOR_MODULATOR = new byte[4096];

	public static final int[] LEAVES_COLOR = new int[256];
	public static final int[] GRASS_COLOR = new int[256];

	private static final boolean[] TICK_RANDOM = new boolean[4096];

	public static void computeFlagArrays() {
		LEAVES_TOP_INDEX = 0;

		Arrays.fill(RENDER_PASS, (byte) -1);
		Arrays.fill(MATERIAL, Material.air);

		for (int i = 0; i < 4096; i++) {
			Block block = Block.blocksList[i];

			if (block instanceof BlockLeavesBase) {
				LEAVES_INDICES[LEAVES_TOP_INDEX++] = i;
			}

			if (block == null) {
				continue;
			}

			TICK_RANDOM[i] = block.getTickRandomly();

			SOLID[i] = block.isOpaqueCube() || block instanceof BlockLeaves;
			VOXEL_RENDER[i] = block.getRenderType() == 0 && (SOLID[i] && Block.lightValue[i] == 0);

			NORMAL_BLOCK[i] = block.blockMaterial.isOpaque() && block.renderAsNormalBlock() && !block.canProvidePower();
			MATERIAL[i] = block.blockMaterial;

			SOLID_CULL_MASK[i] = (byte) (SOLID[i] ? 1 : 0);
			SOLID_LIGHT_MASK[i] = (byte) (((block.isOpaqueCube() || Block.lightOpacity[i] >= 14) && Block.lightValue[i] == 0) || block instanceof BlockLeaves ? 1 : 0);

			// To skip virtual overhead.
			TILE_ENTITY[i] = block.hasTileEntity(0);
			RENDER_PASS[i] = (byte) block.getRenderBlockPass();
		}

		for (int i = 0; i < 256; i++) {
			BiomeGenBase biome = BiomeGenBase.biomeList[i];

			if (biome == null) {
				continue;
			}

			double temp = MathExt.clamp(biome.getFloatTemperature(), 0.0F, 1.0F);
			double rainFall = MathExt.clamp(biome.getFloatRainfall(), 0.0F, 1.0F);

			int grassColor;

			if (biome.getClass() == BiomeGenSwamp.class) {
				grassColor = ((ColorizerGrass.getGrassColor(temp, rainFall) & 0xFEFEFE) + 0x4E0E4E) >> 1;
			} else {
				grassColor = ColorizerGrass.getGrassColor(temp, rainFall);
			}

			int foliageColor;

			if (biome.getClass() == BiomeGenSwamp.class) {
				foliageColor = ((ColorizerFoliage.getFoliageColor(temp, rainFall) & 0xFEFEFE) + 0x4E0E4E) >> 1;
			} else {
				foliageColor = ColorizerFoliage.getFoliageColor(temp, rainFall);
			}

			GRASS_COLOR[i] = grassColor;
			LEAVES_COLOR[i] = foliageColor;
		}
	}

	// Process which methods use default model implementation and based on that avoid the dynamic dispatch and the
	// original method overhead with a more direct call.
	public static void processModelMethods() {
		for (int i = 0; i < 4096; i++) {
			Block block = Block.blocksList[i];

			if (block == null) {
				continue;
			}

			Method sideRendering;
			String shouldSideBeRendered = DEV_ENVIRONMENT ? "shouldSideBeRendered" : "func_71877_c";

			try {
				sideRendering = block.getClass().getMethod(shouldSideBeRendered, IBlockAccess.class, int.class, int.class, int.class, int.class);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			}

			// what a man does to avoid dynamic dispatch.
			DIRECT_CULL[i] = sideRendering.getDeclaringClass() == Block.class;

			Method colorized;
			String colorMultiplier = DEV_ENVIRONMENT ? "colorMultiplier" : "func_71920_b";

			try {
				colorized = block.getClass().getMethod(colorMultiplier, IBlockAccess.class, int.class, int.class, int.class);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			}

			if (colorized.getDeclaringClass() == Block.class || (colorized.getDeclaringClass() == BlockFluid.class && block.blockMaterial != Material.water)) {
				COLOR_MODULATOR[i] = ModelColorizer.DEFAULT_COLOR;
			} else if (colorized.getDeclaringClass() == BlockFluid.class) {
				COLOR_MODULATOR[i] = ModelColorizer.WATER_COLOR;
			} else if (colorized.getDeclaringClass() == BlockGrass.class || block.blockMaterial == Material.vine || block.blockMaterial == Material.grass) {
				COLOR_MODULATOR[i] = ModelColorizer.GRASS_COLOR;
			} else if (colorized.getDeclaringClass() == BlockLeaves.class) {
				COLOR_MODULATOR[i] = ModelColorizer.FOLIAGE_COLOR;
			} else {
				COLOR_MODULATOR[i] = ModelColorizer.DYNAMIC_COLOR;
			}

			AsyncBlockHook.setupAsyncBounds(block);
		}
	}

	public static void processLeavesSolid() {
		// Fixes water lighting issues maybe??
		Block.canBlockGrass[Block.waterMoving.blockID] = false;
		Block.canBlockGrass[Block.waterStill.blockID] = false;

		boolean fastGraphics = !Minecraft.getMinecraft().gameSettings.fancyGraphics;

		VoxelMesher.SIDE_GRASS_NON_OVERLAY = fastGraphics ? null : Block.grass.getIcon(5, 5);
		VoxelMesher.OVERLAY_UVS[0] = TerrainFormat.deNormalizeTexCoordinate(BlockGrass.getIconSideOverlay().getMinU());
		VoxelMesher.OVERLAY_UVS[1] = TerrainFormat.deNormalizeTexCoordinate(BlockGrass.getIconSideOverlay().getMinV());
		VoxelMesher.OVERLAY_UVS[2] = TerrainFormat.deNormalizeTexCoordinate(BlockGrass.getIconSideOverlay().getMaxU());
		VoxelMesher.OVERLAY_UVS[3] = TerrainFormat.deNormalizeTexCoordinate(BlockGrass.getIconSideOverlay().getMaxV());

		for (int i = 0; i < LEAVES_TOP_INDEX; i++) {
			SOLID_CULL_MASK[LEAVES_INDICES[i]] = (byte) (fastGraphics ? 1 : 0);
			SOLID[LEAVES_INDICES[i]] = fastGraphics;
		}
	}

	public static void processDevInfo() {
		try {
			RenderGlobal.class.getDeclaredMethod("renderStars");
			PrimitivesFlags.DEV_ENVIRONMENT = true;
		} catch (NoSuchMethodException e) {
			PrimitivesFlags.DEV_ENVIRONMENT = false;
		}

		PrimitivesFlags.computeFlagArrays();
		PrimitivesFlags.processModelMethods();

		PrimitivesFlags.DETECTED = true;
	}

	public static boolean getTickRandom(int blockId) {
		return blockId != 0 && TICK_RANDOM[blockId];
	}
}
