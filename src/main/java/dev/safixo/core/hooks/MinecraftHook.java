package dev.safixo.core.hooks;

import dev.safixo.client.render.util.data.BlocksFlags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockIce;
import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;

public class MinecraftHook {
	public static void checkGLError(Minecraft minecraft, String str) {
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		// NO-OP
	}

	public static boolean shouldSideBeRendered(BlockIce block, IBlockAccess access, int x, int y, int z, int val) {
		return access.getBlockId(x, y, z) != Block.ice.blockID && !access.isBlockOpaqueCube(x, y, z);
	}
}
