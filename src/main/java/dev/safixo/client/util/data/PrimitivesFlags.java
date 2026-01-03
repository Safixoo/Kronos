package dev.safixo.client.util.data;

import dev.safixo.client.render.pipelines.terrain.meshing.ModelColorizer;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.IBlockAccess;

import java.lang.reflect.Method;

/**
 * Uses multiple arrays to pre-compute flags, avoid extra indirection in hot code, and skip dynamic dispatch when
 * possible.
 */
public class PrimitivesFlags {
	public static boolean REDIRECT_DRAWING;

	public static boolean DEV_ENVIRONMENT;
	public static boolean DETECTED = false;

	public static final Material[] MATERIAL = new Material[2048];
	public static final boolean[] SOLID = new boolean[2048];
	public static final boolean[] NORMAL_BLOCK = new boolean[2048];

	public static final byte[] SOLID_CULL_MASK = new byte[2048];
	public static final byte[] SOLID_LIGHT_MASK = new byte[2048];
	public static final boolean[] DIRECT_CULL = new boolean[2048];

	private static int LEAVES_TOP_INDEX = 0;
	private static final int[] LEAVES_INDICES = new int[2048];
	public static final byte[] COLOR_MODULATOR = new byte[2048];

	public static void computeFlagArrays() {
		LEAVES_TOP_INDEX = 0;

		for (int i = 0; i < 2048; i++) {
			Block block = Block.blocksList[i];

			if (block instanceof BlockLeavesBase) {
				LEAVES_INDICES[LEAVES_TOP_INDEX++] = i;
			}

			SOLID[i] = ((block != null && block.isOpaqueCube()) || block instanceof BlockLeaves);
			NORMAL_BLOCK[i] = ((block != null && block.blockMaterial.isOpaque() && block.renderAsNormalBlock() && !block.canProvidePower()));
			MATERIAL[i] = (block == null || i == 0) ? Material.air : block.blockMaterial;
			SOLID_CULL_MASK[i] = (byte) (((block != null && block.isOpaqueCube()) || block instanceof BlockLeaves) ? 1 : 0);
			SOLID_LIGHT_MASK[i] = (byte) (((block != null && block.isOpaqueCube()) || block instanceof BlockLeaves) ? 1 : 0);
		}
	}

	// Process which methods use default model implementation and based on that avoid the dynamic dispatch and the
	// original method overhead with a more direct call.
	public static void processModelMethods() {
		for (int i = 0; i < 2048; i++) {
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
			} else if (colorized.getDeclaringClass() == BlockGrass.class) {
				COLOR_MODULATOR[i] = ModelColorizer.GRASS_COLOR;
			} else if (colorized.getDeclaringClass() == BlockLeaves.class) {
				COLOR_MODULATOR[i] = ModelColorizer.LEAVES_COLOR;
			} else {
				// dynamic dispatch at runtime.
			}
		}
	}

	public static void processLeavesSolid() {
		boolean solid = !Minecraft.getMinecraft().gameSettings.fancyGraphics;

		for (int i = 0; i < LEAVES_TOP_INDEX; i++) {
			SOLID_CULL_MASK[LEAVES_INDICES[i]] = (byte) (solid ? 1 : 0);
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
}
