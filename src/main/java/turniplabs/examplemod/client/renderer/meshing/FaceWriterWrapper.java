package turniplabs.examplemod.client.renderer.meshing;

import net.minecraft.core.util.phys.AABB;

public interface FaceWriterWrapper {
	void setUV(float minU, float minV, float maxU, float maxV);
	void vertexOne(ModelBoundsData modelData, int color);
	void vertexTwo(ModelBoundsData modelData, int color);
	void vertexThree(ModelBoundsData modelData, int color);
	void vertexFour(ModelBoundsData modelData, int color);
}
