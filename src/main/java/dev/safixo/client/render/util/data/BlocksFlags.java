package dev.safixo.client.render.util.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.color.BlockColor;
import net.minecraft.client.render.block.color.BlockColorDispatcher;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.BlockModelStandard;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Material;
import net.minecraft.core.util.phys.AABB;
import net.minecraft.core.world.WorldSource;

import java.lang.reflect.Method;

public class BlocksFlags {
	public static final Material[] MATERIAL = new Material[2048];
	public static final boolean[] SOLID = new boolean[2048];

	public static final byte[] SOLID_LIGHT_MASK = new byte[2048];
	public static final BlockModel<?>[] BLOCK_MODEL = new BlockModel[2048];
	public static final BlockColor[] BLOCK_COLOR = new BlockColor[2048];

	public static final boolean[] DIRECT_CULL = new boolean[2048];

	private static int LEAVES_TOP_INDEX = 0;
	private static final int[] LEAVES_INDICES = new int[2048];

	public static void computeFlagArrays() {
		for (int i = 0; i < Blocks.highestBlockId; i++) {
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

	// Process which methods use default model implementation and based on that avoid the dynamic dispatch and the
	// original method overhead with a more direct call.
	public static void processModelMethods() {
		for (int i = 0; i < Blocks.highestBlockId; i++) {
			Block<?> block = Blocks.getBlock(i);

			BlockModel<?> model = BlockModelDispatcher.getInstance().getDispatch(block);
			BlockColor color = BlockColorDispatcher.getInstance().getDispatch(block);
			Method method;

			try {
				method = model.getClass().getMethod("shouldSideBeRendered", WorldSource.class, AABB.class, int.class, int.class, int.class, int.class);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			}

			BLOCK_MODEL[i] = model;
			BLOCK_COLOR[i] = color;

			// what a man does to avoid dynamic dispatch.
			DIRECT_CULL[i] = method.getDeclaringClass() == BlockModelStandard.class;
		}
	}

	public static void processLeavesSolid() {
		boolean solid = Minecraft.getMinecraft().gameSettings.fancyGraphics.value == 0;

		for (int i = 0; i < LEAVES_TOP_INDEX; i++) {
			SOLID[LEAVES_INDICES[i]] = solid;
		}

	}
}
