package turniplabs.examplemod.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Material;

public class BlocksFlags {
	public static final Material[] MATERIAL = new Material[2048];
	public static final boolean[] SOLID = new boolean[2048];
	public static final byte[] SOLID_LIGHT_MASK = new byte[2048];
	public static final boolean[] LEAVES = new boolean[2048];

	private static int LEAVES_TOP_INDEX = 0;
	private static final int[] LEAVES_INDICES = new int[2048];

	public static void computeFlagArrays() {
		for (int i = 0; i < 1054; i++) {
			Block<?> block = Blocks.getBlock(i);

			if (block != null && block.getMaterial() == Material.leaves) {
				LEAVES_INDICES[LEAVES_TOP_INDEX++] = i;
			}

			SOLID[i] = Blocks.solid[i];
			MATERIAL[i] = (block == null || i == 0) ? Material.air : block.getMaterial();
			SOLID_LIGHT_MASK[i] = (byte) ((Blocks.solid[i] || MATERIAL[i] == Material.leaves) ? 1 : 0);
			SOLID[i] = Blocks.solid[i] || (MATERIAL[i] == Material.leaves && Minecraft.getMinecraft().gameSettings.fancyGraphics.value == 0);
		}
	}

	public static void processLeavesSolid() {
		boolean solid = Minecraft.getMinecraft().gameSettings.fancyGraphics.value == 0;

		for (int i = 0; i < LEAVES_TOP_INDEX; i++) {
			SOLID[LEAVES_INDICES[i]] = solid;
		}

	}
}
