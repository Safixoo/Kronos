package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.collection.Vector8bi;
import org.joml.Vector3i;

import static dev.safixo.client.util.Direction.*;

public class FacingData {
	public byte dirX, dirY, dirZ;
	public byte aoCornerX0, aoCornerY0, aoCornerZ0;
	public byte aoCornerX1, aoCornerY1, aoCornerZ1;

	public int dirPacked;
	public short aoCorner0Packed, aoCorner1Packed;
	public byte aoCorner0, aoCorner1;

	public long v0, v1, v2, v3;
	public int uv0, uv1, uv2, uv3;

	public FacingData() {}

	public void setQuadVerts(int ind, Vector3i verts) {
		if (ind == 0) {
			this.v0 = TerrainFormat.transformAddedPosition(verts.x, verts.y, verts.z);
		} else if (ind == 1) {
			this.v1 = TerrainFormat.transformAddedPosition(verts.x, verts.y, verts.z);
		} else if (ind == 2) {
			this.v2 = TerrainFormat.transformAddedPosition(verts.x, verts.y, verts.z);
		} else {
			this.v3 = TerrainFormat.transformAddedPosition(verts.x, verts.y, verts.z);
		}
	}

	public void processCornersDir(int dir) {
		this.aoCornerX0 = Direction.x(this.aoCorner0);
		this.aoCornerY0 = Direction.y(this.aoCorner0);
		this.aoCornerZ0 = Direction.z(this.aoCorner0);

		this.aoCornerX1 = Direction.x(this.aoCorner1);
		this.aoCornerY1 = Direction.y(this.aoCorner1);
		this.aoCornerZ1 = Direction.z(this.aoCorner1);

		this.aoCorner0Packed = (short) SectionCache.makeBlockIndex(this.aoCornerX0, this.aoCornerY0, this.aoCornerZ0);
		this.aoCorner1Packed = (short) SectionCache.makeBlockIndex(this.aoCornerX1, this.aoCornerY1, this.aoCornerZ1);

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
