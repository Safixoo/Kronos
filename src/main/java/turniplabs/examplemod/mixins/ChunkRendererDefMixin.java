package turniplabs.examplemod.mixins;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.client.render.terrain.ChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.interfaces.mixin.IChunkRenderer;


@Mixin(value = ChunkRenderer.class, remap = false)
public abstract class ChunkRendererDefMixin implements IChunkRenderer {
	@Shadow
	public int posX;
	@Shadow
	public int posY;
	@Shadow
	public int posZ;

	private final IChunkRenderer[] adjacentSections = new IChunkRenderer[6];
	private int frame = -1;
	private int adjacentMask = 0;

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
		this.connectNeighbors();
	}

	@Inject(method = "reset", at = @At("HEAD"))
	private void resetData(CallbackInfo ci) {
		positionToRenderer.put(asLong(this.posX >> 4, this.posY >> 4, this.posZ >> 4), null);
		this.disconnectNeighbors();
	}

	@Override
	public Long2ReferenceMap<ChunkRenderer> chunkMap() {
		return positionToRenderer;
	}

	@Override
	public int getFrame() {
		return this.frame;
	}

	@Override
	public void setFrame(int frame) {
		this.frame = frame;
	}

	private void connectNeighbors() {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			IChunkRenderer renderer = getSection(dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(this, Direction.opposite(dir));
			}

			this.setAdjacentNeighbor(renderer, dir);
		}
	}

	@Override
	public void setAdjacentNeighbor(IChunkRenderer render, int direction) {
		if (render == null) {
			this.adjacentMask &= ~(1 << direction);
		} else {
			this.adjacentMask |= (1 << direction);
		}

		this.adjacentSections[direction] = render;
	}

	@Override
	public int getAdjacentMask() {
		return this.adjacentMask;
	}

	private IChunkRenderer getSection(int direction) {
		int chunkX = (this.posX >> 4) + Direction.x(direction);
		int chunkY = (this.posY >> 4) + Direction.y(direction);
		int chunkZ = (this.posZ >> 4) + Direction.z(direction);

		return (IChunkRenderer) positionToRenderer.get(asLong(chunkX, chunkY, chunkZ));
	}

	@Override
	public ChunkRenderer getRender() {
		return (ChunkRenderer) (Object) this;
	}

	@Override
	public IChunkRenderer getAdjacent(int direction) {
		return this.adjacentSections[direction];
	}

	private void disconnectNeighbors() {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			IChunkRenderer renderer = getSection(dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(null, Direction.opposite(dir));
			}

			this.setAdjacentNeighbor(null, dir);
		}
	}

	private static final Long2ReferenceMap<ChunkRenderer> positionToRenderer = new Long2ReferenceOpenHashMap<>();
}
