package dev.safixo.core.hooks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Icon;

@SuppressWarnings("unused")
public class RenderBlocksHook {
	public static boolean renderStandardBlock(RenderBlocks render, Block block, int x, int y, int z) {
		Tessellator tessellator = Tessellator.instance;
		boolean draw = false;

		if (block.shouldSideBeRendered(render.blockAccess, x, y - 1, z, 0)) {
			render.renderFaceYNeg(block, x, y, z, render.getBlockIcon(block, render.blockAccess, x, y, z, 0));
			draw = true;
		}

		if (block.shouldSideBeRendered(render.blockAccess, x, y + 1, z, 1)) {
			render.renderFaceYPos(block, x, y, z, render.getBlockIcon(block, render.blockAccess, x, y, z, 1));
			draw = true;
		}

		if (block.shouldSideBeRendered(render.blockAccess, x, y, z - 1, 2)) {
			render.renderFaceZNeg(block, x, y, z, render.getBlockIcon(block, render.blockAccess, x, y, z, 2));
			draw = true;
		}

		if (block.shouldSideBeRendered(render.blockAccess, x, y, z + 1, 3)) {
			render.renderFaceZPos(block, x, y, z, render.getBlockIcon(block, render.blockAccess, x, y, z, 3));
			draw = true;
		}

		if (block.shouldSideBeRendered(render.blockAccess, x - 1, y, z, 4)) {
			render.renderFaceXNeg(block, x, y, z, render.getBlockIcon(block, render.blockAccess, x, y, z, 4));
			draw = true;
		}

		if (block.shouldSideBeRendered(render.blockAccess, x + 1, y, z, 5)) {
			render.renderFaceXPos(block, x, y, z, render.getBlockIcon(block, render.blockAccess, x, y, z, 5));
			draw = true;
		}

		return draw;
	}
}
