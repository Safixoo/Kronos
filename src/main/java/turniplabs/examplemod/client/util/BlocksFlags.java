package turniplabs.examplemod.client.util;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Material;

public class BlocksFlags {
	public static final Block<?>[] BLOCKS_LIST = new Block[2048];
	public static final Material[] MATERIAL = new Material[2048];
	public static final boolean[] SOLID = new boolean[2048];
	public static final byte[] SOLID_MASK = new byte[2048];
	public static final boolean[] LEAVES = new boolean[2048];
	public static final boolean[] LIT_INTERIOR = new boolean[2048];

	public static void computeFlagArrays() {
		for (int i = 0; i < 1054; i++) {
			Block<?> block = Blocks.getBlock(i);

			SOLID[i] = Blocks.solid[i];
			MATERIAL[i] = (block == null || i == 0) ? Material.air : block.getMaterial();
			SOLID[i] = Blocks.solid[i] || MATERIAL[i] == Material.leaves;
			SOLID_MASK[i] = (byte) (SOLID[i] ? 1 : 0);
			LIT_INTERIOR[i] = i != 0 && BLOCKS_LIST[i] != null && BLOCKS_LIST[i].isLitInteriorSurface;
		}
	}

	public static void addBlockToList(Block<?> block) {
		BLOCKS_LIST[block.id()] = block;
	}
}
