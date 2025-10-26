package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.util.Direction;
import org.joml.Vector3i;

public class FacingRender {
	public byte aoCornerX0, aoCornerY0, aoCornerZ0;
	public byte aoCornerX1, aoCornerY1, aoCornerZ1;

	public final Vector3i[] quadVerts = new Vector3i[Direction.COUNT];
	public final int[] uvData = new int[4];

	public int aoCorner0;
	public int aoCorner1;

	public FacingRender() {

	}

	public void processCornersDir() {
		this.aoCornerX0 = Direction.x(this.aoCorner0);
		this.aoCornerY0 = Direction.y(this.aoCorner0);
		this.aoCornerZ0 = Direction.z(this.aoCorner0);

		this.aoCornerX1 = Direction.x(this.aoCorner1);
		this.aoCornerY1 = Direction.y(this.aoCorner1);
		this.aoCornerZ1 = Direction.z(this.aoCorner1);
	}

	public void setTexInd(int x, int y, int z, int w) {
		this.uvData[0] = x;
		this.uvData[1] = y;
		this.uvData[2] = z;
		this.uvData[3] = w;
	}
}
