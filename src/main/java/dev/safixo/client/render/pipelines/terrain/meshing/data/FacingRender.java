package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.util.Direction;
import org.joml.Vector3i;

import static dev.safixo.client.util.Direction.*;

@SuppressWarnings("PointlessArithmeticExpression")
public class FacingRender {
	public short dirPacked;
	public byte dirX, dirY, dirZ;
	public byte aoCornerX0, aoCornerY0, aoCornerZ0;
	public byte aoCornerX1, aoCornerY1, aoCornerZ1;

	// (0b111 * 3) * 4
	public long quadVert;
	public final short[] uvData = new short[16];
	public byte[] weightIndices = new byte[8];
	public byte bA, bB, bC, bD;

	public short aoCorner0Packed, aoCorner1Packed;
	public short aoCorner0, aoCorner1;

	public static final byte[] ROTATION = new byte[4 * Direction.COUNT];

	public void setQuadVerts(int ind, Vector3i verts) {
		long data = (verts.x & 0xF) | (verts.y & 0xF) << 4 | (verts.z & 0xF) << 8;
		this.quadVert |= data << (12 * ind);
	}

	// Emulates RenderBlocks rotation flags, somehow it works for the vanilla models that I tried, although
	// this was made kind of by reversing the behaviour and not trying to mimic the psychotic code used in
	// for the flags.
	static {
		ROTATION[0] = 0 * 4;
		ROTATION[1] = 1 * 4;

		// swapped??
		ROTATION[2] = 3 * 4;
		ROTATION[3] = 2 * 4;
	}

	public FacingRender() {

	}

	public void processCornersDir(int dir) {
		this.aoCornerX0 = Direction.x(this.aoCorner0);
		this.aoCornerY0 = Direction.y(this.aoCorner0);
		this.aoCornerZ0 = Direction.z(this.aoCorner0);

		this.aoCornerX1 = Direction.x(this.aoCorner1);
		this.aoCornerY1 = Direction.y(this.aoCorner1);
		this.aoCornerZ1 = Direction.z(this.aoCorner1);

		this.aoCorner0Packed = (short) (SectionCache.makeBlockIndex(this.aoCornerX0, this.aoCornerY0, this.aoCornerZ0) & 0xFFF);
		this.aoCorner1Packed = (short) (SectionCache.makeBlockIndex(this.aoCornerX1, this.aoCornerY1, this.aoCornerZ1) & 0xFFF);
		this.dirPacked = (short) (SectionCache.makeBlockIndex(this.dirX, this.dirY, this.dirZ) & 0xFFF);

		this.dirX = Direction.x(dir);
		this.dirY = Direction.y(dir);
		this.dirZ = Direction.z(dir);

		for (int i = 0; i < 4; i++) {
			int ind = i * 2;
			int vertIndices = (int) (this.quadVert >>> (12 * i));
			int x = (vertIndices >>> 0) & 0xF;
			int y = (vertIndices >>> 4) & 0xF;
			int z = (vertIndices >>> 8) & 0xF;

			if (dir == DOWN) {
				this.weightIndices[ind] = (byte) z;
				this.weightIndices[ind + 1] = (byte) -x;
			} else if (dir == UP) {
				this.weightIndices[ind] = (byte) z;
				this.weightIndices[ind + 1] = (byte) x;
			} else if (dir == NORTH) {
				this.weightIndices[ind] = (byte) -x;
				this.weightIndices[ind + 1] = (byte) y;
			} else if (dir == SOUTH) {
				this.weightIndices[ind] = (byte) y;
				this.weightIndices[ind + 1] = (byte) -x;
			} else if (dir == WEST) {
				this.weightIndices[ind] = (byte) z;
				this.weightIndices[ind + 1] = (byte) y;
			} else {
				this.weightIndices[ind] = (byte) z;
				this.weightIndices[ind + 1] = (byte) -y;
			}
		}
	}

	public void setTexInd(int x, int y, int z, int w) {
		for (int i = 0; i < 4; i++) {
			this.uvData[i * 4 + 0] = (short) VoxelMesher.MAP_ID_TO_UV[x + ROTATION[i]];
			this.uvData[i * 4 + 1] = (short) VoxelMesher.MAP_ID_TO_UV[y + ROTATION[i]];
			this.uvData[i * 4 + 2] = (short) VoxelMesher.MAP_ID_TO_UV[z + ROTATION[i]];
			this.uvData[i * 4 + 3] = (short) VoxelMesher.MAP_ID_TO_UV[w + ROTATION[i]];
		}
	}

	public void setBoundsTex(int a, int b, int c, int d) {
		this.bA = (byte) a;
		this.bB = (byte) b;
		this.bC = (byte) c;
		this.bD = (byte) d;
	}

	public float minUInd(float[] bounds) {
		return this.bA < 0 ? 1.0f - bounds[-this.bA] : bounds[this.bA];
	}

	public float minVInd(float[] bounds) {
		return this.bB < 0 ? 1.0f - bounds[-this.bB] : bounds[this.bB];
	}

	public float maxUInd(float[] bounds) {
		return this.bC < 0 ? 1.0f - bounds[-this.bC] : bounds[this.bC];
	}

	public float maxVInd(float[] bounds) {
		return this.bD < 0 ? 1.0f - bounds[-this.bD] : bounds[this.bD];
	}
}
