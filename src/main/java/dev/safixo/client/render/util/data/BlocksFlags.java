package dev.safixo.client.render.util.data;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;

import java.lang.reflect.Method;

public class BlocksFlags {
	public static final Material[] MATERIAL = new Material[2048];
	public static final boolean[] SOLID = new boolean[2048];

	public static final byte[] SOLID_LIGHT_MASK = new byte[2048];

	public static final boolean[] DIRECT_CULL = new boolean[2048];

	private static int LEAVES_TOP_INDEX = 0;
	private static final int[] LEAVES_INDICES = new int[2048];

	public static void computeFlagArrays() {
		for (int i = 0; i < 2048; i++) {
			Block block = Block.blocksList[i];

			if (block instanceof BlockLeavesBase) {
				LEAVES_INDICES[LEAVES_TOP_INDEX++] = i;
			}

			SOLID[i] = Block.opaqueCubeLookup[i];
			MATERIAL[i] = (block == null || i == 0) ? Material.air : block.blockMaterial;
			SOLID_LIGHT_MASK[i] = (byte) ((Block.opaqueCubeLookup[i] || block instanceof BlockLeavesBase) ? 1 : 0);
			SOLID[i] = Block.opaqueCubeLookup[i] || (MATERIAL[i] == Material.leaves && Minecraft.getMinecraft().gameSettings.fancyGraphics);
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

			Method method;

			try {
				method = block.getClass().getMethod("shouldSideBeRendered", IBlockAccess.class, int.class, int.class, int.class, int.class);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			}

			// what a man does to avoid dynamic dispatch.
			DIRECT_CULL[i] = method.getDeclaringClass() == Block.class;
		}
	}

	public static void processLeavesSolid() {
		boolean solid = !Minecraft.getMinecraft().gameSettings.fancyGraphics;

		for (int i = 0; i < LEAVES_TOP_INDEX; i++) {
			SOLID[LEAVES_INDICES[i]] = solid;
			SOLID_LIGHT_MASK[LEAVES_INDICES[i]] = (byte) (solid ? 1 : 0);
		}

	}
}
