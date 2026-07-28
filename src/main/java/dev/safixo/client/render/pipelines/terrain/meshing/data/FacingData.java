package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.collection.Vector8bi;
import org.joml.Vector3i;

import static dev.safixo.client.util.Direction.*;

public class FacingData {
	public byte dirX, dirY, dirZ;
	public byte aoCornerX0, aoCornerY0, aoCornerZ0;
	public byte aoCornerX1, aoCornerY1, aoCornerZ1;

	// (0b111 * 3) * 4
	public long quadVert;
	public int uv0, uv1, uv2, uv3;

	public int dirPacked;
	public int aoCorner0Packed, aoCorner1Packed;
	public short aoCorner0, aoCorner1;

	public FacingData() {}

	public void setQuadVerts(int ind, Vector3i verts) {
		long data = (verts.x & 0xF) | (verts.y & 0xF) << 4 | (verts.z & 0xF) << 8;
		this.quadVert |= data << (12 * ind);
	}

	public void processCornersDir(int dir) {
		this.aoCornerX0 = Direction.x(this.aoCorner0);
		this.aoCornerY0 = Direction.y(this.aoCorner0);
		this.aoCornerZ0 = Direction.z(this.aoCorner0);

		this.aoCornerX1 = Direction.x(this.aoCorner1);
		this.aoCornerY1 = Direction.y(this.aoCorner1);
		this.aoCornerZ1 = Direction.z(this.aoCorner1);

		this.aoCorner0Packed = SectionCache.makeBlockIndex(this.aoCornerX0, this.aoCornerY0, this.aoCornerZ0);
		this.aoCorner1Packed = SectionCache.makeBlockIndex(this.aoCornerX1, this.aoCornerY1, this.aoCornerZ1);

		this.dirX = Direction.x(dir);
		this.dirY = Direction.y(dir);
		this.dirZ = Direction.z(dir);
		this.dirPacked = SectionCache.makeBlockIndex(this.dirX, this.dirY, this.dirZ);
	}

	public void setTexInd(int x, int y, int z, int w) {
		int a = VoxelMesher.MAP_ID_TO_UV[x];
		int b = VoxelMesher.MAP_ID_TO_UV[y];
		int c = VoxelMesher.MAP_ID_TO_UV[z];
		int d = VoxelMesher.MAP_ID_TO_UV[w];

		this.uv0 = a;
		this.uv1 = b;
		this.uv2 = c;
		this.uv3 = d;
	}
}
