package dev.safixo.client.render.util.data;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.Sys;

import java.lang.reflect.Method;

public class BlocksFlags {
	public static boolean DEV_ENVIRONMENT;
	public static boolean DETECTED = false;

	public static final Material[] MATERIAL = new Material[2048];
	public static final boolean[] SOLID = new boolean[2048];
	public static final boolean[] NORMAL_BLOCK = new boolean[2048];

	public static final byte[] SOLID_LIGHT_MASK = new byte[2048];
	public static final boolean[] DIRECT_CULL = new boolean[2048];

	private static int LEAVES_TOP_INDEX = 0;
	private static final int[] LEAVES_INDICES = new int[2048];

	public static void computeFlagArrays() {
		LEAVES_TOP_INDEX = 0;

		for (int i = 0; i < 2048; i++) {
			Block block = Block.blocksList[i];

			if (block instanceof BlockLeavesBase) {
				LEAVES_INDICES[LEAVES_TOP_INDEX++] = i;
			}

			SOLID[i] = ((block != null && block.isOpaqueCube()));
			NORMAL_BLOCK[i] = ((block != null && block.blockMaterial.isOpaque() && block.renderAsNormalBlock() && !block.canProvidePower()));
			MATERIAL[i] = (block == null || i == 0) ? Material.air : block.blockMaterial;
			SOLID_LIGHT_MASK[i] = (byte) (((block != null && block.isOpaqueCube()) || block instanceof BlockLeavesBase) ? 1 : 0);
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
			String shouldSideBeRendered = DEV_ENVIRONMENT ? "shouldSideBeRendered" : "func_71877_c";

			try {
				method = block.getClass().getMethod(shouldSideBeRendered, IBlockAccess.class, int.class, int.class, int.class, int.class);
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
			SOLID_LIGHT_MASK[LEAVES_INDICES[i]] = (byte) (solid ? 1 : 0);
		}

	}

	public static void processDevInfo() {
		try {
			RenderGlobal.class.getDeclaredMethod("renderStars");
			BlocksFlags.DEV_ENVIRONMENT = true;
		} catch (NoSuchMethodException e) {
			BlocksFlags.DEV_ENVIRONMENT = false;
		}

		BlocksFlags.computeFlagArrays();
		BlocksFlags.processModelMethods();

		BlocksFlags.DETECTED = true;
	}
}
