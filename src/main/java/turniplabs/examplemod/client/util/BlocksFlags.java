package turniplabs.examplemod.client.util;

import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.BlockModelLeaves;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;

public class BlocksFlags {
	public static final Block<?>[] BLOCKS_LIST = new Block[2048];
	public static final boolean[] IS_LEAVES = new boolean[2048];
	public static final boolean[] SOLID = new boolean[2048];
	public static final boolean[] LIT_INTERIOR = new boolean[2048];

	public static void computeFlagArrays() {
		for (int i = 0; i < 1054; i++) {
			SOLID[i] = Blocks.solid[i];
			IS_LEAVES[i] = BlockModelDispatcher.getInstance().getDispatch(Blocks.getBlock(i)) instanceof BlockModelLeaves;
			LIT_INTERIOR[i] = i != 0 && BLOCKS_LIST[i] != null && BLOCKS_LIST[i].isLitInteriorSurface;
		}
	}

	public static void addBlockToList(Block<?> block) {
		BLOCKS_LIST[block.id()] = block;
	}
}
