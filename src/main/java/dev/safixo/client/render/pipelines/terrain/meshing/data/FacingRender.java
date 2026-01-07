package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.FullBlockMesher;
import dev.safixo.client.util.Direction;
import org.joml.Vector3i;

@SuppressWarnings("PointlessArithmeticExpression")
public class FacingRender {
	public byte aoCornerX0, aoCornerY0, aoCornerZ0;
	public byte aoCornerX1, aoCornerY1, aoCornerZ1;

	public final Vector3i[] quadVerts = new Vector3i[Direction.COUNT];
	public final int[] uvData = new int[16];
	public int[] texBounds = new int[4];

	public int aoCorner0;
	public int aoCorner1;

	public static final byte[] ROTATION = new byte[4 * Direction.COUNT];

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

	public void processCornersDir() {
		this.aoCornerX0 = Direction.x(this.aoCorner0);
		this.aoCornerY0 = Direction.y(this.aoCorner0);
		this.aoCornerZ0 = Direction.z(this.aoCorner0);

		this.aoCornerX1 = Direction.x(this.aoCorner1);
		this.aoCornerY1 = Direction.y(this.aoCorner1);
		this.aoCornerZ1 = Direction.z(this.aoCorner1);
	}

	public void setTexInd(int x, int y, int z, int w) {
		for (int i = 0; i < 4; i++) {
			this.uvData[i * 4 + 0] = FullBlockMesher.MAP_ID_TO_UV[x + ROTATION[i]];
			this.uvData[i * 4 + 1] = FullBlockMesher.MAP_ID_TO_UV[y + ROTATION[i]];
			this.uvData[i * 4 + 2] = FullBlockMesher.MAP_ID_TO_UV[z + ROTATION[i]];
			this.uvData[i * 4 + 3] = FullBlockMesher.MAP_ID_TO_UV[w + ROTATION[i]];
		}
	}

	public void setBoundsTex(int a, int b, int c, int d) {
		this.texBounds[0] = a;
		this.texBounds[1] = b;
		this.texBounds[2] = c;
		this.texBounds[3] = d;
	}

	public float minUInd(float[] bounds) {
		return (this.texBounds[0] < 0 ? 1.0f - bounds[-this.texBounds[0]] : bounds[this.texBounds[0]]) * 16.0f;
	}

	public float minVInd(float[] bounds) {
		return (this.texBounds[1] < 0 ? 1.0f - bounds[-this.texBounds[1]] : bounds[this.texBounds[1]]) * 16.0f;
	}

	public float maxUInd(float[] bounds) {
		return (this.texBounds[2] < 0 ? 1.0f - bounds[-this.texBounds[2]] : bounds[this.texBounds[2]]) * 16.0f;
	}

	public float maxVInd(float[] bounds) {
		return (this.texBounds[3] < 0 ? 1.0f - bounds[-this.texBounds[3]] : bounds[this.texBounds[3]]) * 16.0f;
	}
}
