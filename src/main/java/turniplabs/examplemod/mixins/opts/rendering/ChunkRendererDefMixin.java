package turniplabs.examplemod.mixins.opts.rendering;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.client.render.terrain.ChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.render.gl.GlVertexBuffer;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.interfaces.mixin.IChunkRenderer;


@Mixin(value = ChunkRenderer.class, remap = false)
public abstract class  ChunkRendererDefMixin implements IChunkRenderer {
	@Shadow
	public int posX;
	@Shadow
	public int posY;
	@Shadow
	public int posZ;

	private final IChunkRenderer[] adjacentSections = new IChunkRenderer[6];
	private int frame = -1;
	private int adjacentMask = 0;

	private long putPosition = -1;

	private static long asLong(int x, int y, int z) {
		long l = 0L;
		l |= ((long)x & 4194303L) << 42;
		l |= ((long)y & 1048575L) << 0;
		l |= ((long)z & 4194303L) << 20;
		return l;
	}

	@Inject(method = "setPos", at = @At("TAIL"))
	private void addPos(CallbackInfo ci) {
		long position = asLong(this.posX >> 4, this.posY >> 4, this.posZ >> 4);

		if (this.putPosition != position) {
			if (this.putPosition != -1) {
				positionToRenderer.remove(this.putPosition, this);
			}

			this.connectNeighborsDirty();
			this.setDirty(true);
			positionToRenderer.put(position, (ChunkRenderer) (Object) this);
		}

		this.putPosition = position;
	}

	@Inject(method = "delete", at = @At("TAIL"))
	private void resetData(CallbackInfo ci) {
		GlVertexBuffer translucentBuffer = this.translucentBuffer();
		GlVertexBuffer solidBuffer = this.solidBuffer();

		if (translucentBuffer != null) {
			translucentBuffer.clearVertexData();
			translucentBuffer.clear();
		}

		if (solidBuffer != null) {
			solidBuffer.clearVertexData();
			solidBuffer.clear();
		}

		positionToRenderer.remove(asLong(this.posX >> 4, this.posY >> 4, this.posZ >> 4));
		this.disconnectNeighborsDirty();
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

	public void connectNeighbors() {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			IChunkRenderer renderer = getSection(dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(this, Direction.opposite(dir));
			}

			this.setAdjacentNeighbor(renderer, dir);
		}
	}

	public void connectNeighborsDirty() {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			IChunkRenderer renderer = getSection(dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(this, Direction.opposite(dir));
				renderer.setDirty(true);
			}

			this.setAdjacentNeighbor(renderer, dir);
		}
	}

	public void disconnectNeighborsDirty() {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			IChunkRenderer renderer = getSection(dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(null, Direction.opposite(dir));
				renderer.setDirty(true);
			}

			this.setAdjacentNeighbor(null, dir);
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

	private IChunkRenderer getSectionActive(int direction) {
		return this.adjacentSections[direction];
	}

	@Override
	public ChunkRenderer getRender() {
		return (ChunkRenderer) (Object) this;
	}

	@Override
	public IChunkRenderer getAdjacent(int direction) {
		return this.adjacentSections[direction];
	}

	public void disconnectNeighbors() {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			IChunkRenderer renderer = getSectionActive(dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(null, Direction.opposite(dir));
			}

			this.setAdjacentNeighbor(null, dir);
		}
	}

	private static final Long2ReferenceMap<ChunkRenderer> positionToRenderer = new Long2ReferenceOpenHashMap<>();
}
