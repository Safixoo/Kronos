package turniplabs.examplemod.mixins.opts;

import net.minecraft.client.render.RenderBlockCache;
import net.minecraft.core.block.Block;
import net.minecraft.core.world.WorldSource;
import org.spongepowered.asm.mixin.*;

@Mixin(value = RenderBlockCache.class, remap = false)
public class RenderBlockCacheMixin {
	@Shadow private int offsetX;
	@Shadow private int offsetY;
	@Shadow private int offsetZ;

	@Shadow
	private Block<?> block;
	@Shadow
	private WorldSource access;

	@Mutable
	@Shadow
	private @Final boolean[] brightnessCached;
	@Mutable
	@Shadow
	private @Final boolean[] opacityCached;
	@Mutable
	@Shadow
	private @Final boolean[] lightmapCoordCached;

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
}
