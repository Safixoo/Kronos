package turniplabs.examplemod.client.render.meshing;

import org.joml.Vector2i;
import org.joml.Vector3i;
import org.joml.Vector4i;
import turniplabs.examplemod.client.util.Direction;

public class FacingRender {
	public final Vector3i[] quadVerts = new Vector3i[Direction.COUNT];
	public final int[] uvData = new int[4];
	public final int[] aoCorners = new int[2];

	public FacingRender() {

	}

	public void setTexInd(int x, int y, int z, int w) {
		this.uvData[0] = x;
		this.uvData[1] = y;
		this.uvData[2] = z;
		this.uvData[3] = w;
	}
}
