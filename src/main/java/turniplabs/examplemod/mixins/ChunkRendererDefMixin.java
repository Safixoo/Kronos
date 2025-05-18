package turniplabs.examplemod.mixins;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.client.render.terrain.ChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.util.interfaces.mixin.IChunkRenderer;


@Mixin(value = ChunkRenderer.class, remap = false)
public abstract class ChunkRendererDefMixin implements IChunkRenderer {

	@Shadow
	public int posX;

	@Shadow
	public int posY;

	@Shadow
	public int posZ;

	private static long asLong(int x, int y, int z) {
		long l = 0L;
		l |= ((long)x & 4194303L) << 42;
		l |= ((long)y & 1048575L) << 0;
		l |= ((long)z & 4194303L) << 20;
		return l;
	}

	@Inject(method = "setPos", at = @At("HEAD"))
	private void addPos(int x, int y, int z, CallbackInfo ci) {
		positionToRenderer.put(asLong(this.posX >> 4, this.posY >> 4, this.posZ >> 4), (ChunkRenderer) (Object)this);
	}

	@Override
	public Long2ReferenceMap<ChunkRenderer> chunkMap() {
		return positionToRenderer;
	}

	private static final Long2ReferenceMap<ChunkRenderer> positionToRenderer = new Long2ReferenceOpenHashMap<>();
}
