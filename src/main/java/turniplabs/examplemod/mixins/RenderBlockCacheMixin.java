package turniplabs.examplemod.mixins;

import net.minecraft.client.render.RenderBlockCache;
import net.minecraft.core.block.Block;
import net.minecraft.core.world.WorldSource;
import org.spongepowered.asm.mixin.*;

@Mixin(value = RenderBlockCache.class, remap = false)
public class RenderBlockCacheMixin {


	@Shadow
	private int offsetX;

	@Shadow
	private int offsetY;

	@Shadow
	private int offsetZ;

	@Shadow
	private Block<?> block;

	@Shadow
	private WorldSource access;

	@Mutable
	@Shadow
	@Final
	private boolean[] brightnessCached;

	@Mutable
	@Shadow
	@Final
	private boolean[] opacityCached;

	@Mutable
	@Shadow
	@Final
	private boolean[] lightmapCoordCached;

	@Unique
	private static final boolean[] EMPTY_ARRAY = new boolean[27];

	/**
	 * @author Safixo
	 * @reason Faster array filling.
	 */
	// TODO: Cambiarlo por un redirect a la variable o algo mas simple.
	@Overwrite
	public void setupCache(Block<?> block, WorldSource access, int x, int y, int z) {
		if (x != this.offsetX || y != this.offsetY || z != this.offsetZ || this.block != block || this.access != access) {
			this.block = block;
			this.access = access;
			this.brightnessCached = new boolean[27];
			this.opacityCached = new boolean[27];
			this.lightmapCoordCached = new boolean[27];
			this.offsetX = x;
			this.offsetY = y;
			this.offsetZ = z;
		}

	}

	@Unique
	private static void fillBooleanArray(boolean[] array) {
		System.arraycopy(EMPTY_ARRAY, 0, array, 0, 27);
	}
}
