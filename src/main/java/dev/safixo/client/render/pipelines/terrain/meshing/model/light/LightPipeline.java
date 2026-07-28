package dev.safixo.client.render.pipelines.terrain.meshing.model.light;

import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.QuadLightData;
import net.minecraft.world.IBlockAccess;
import org.joml.Vector3i;

/**
 * Light pipelines allow model quads for any location in the level to be lit using various backends, including fluids
 * and block entities.
 */
public interface LightPipeline {
    /**
     * Calculates the light data for a given block model quad, storing the result in {@param out}.
     * @param quad The block model quad
     * @param pos The block position of the model this quad belongs to
     * @param out The data arrays which will store the calculated light data results
     * @param side The light face of the quad
     * @param shade True if the block is shaded by ambient occlusion
     */
    void calculate(Quad quad, Vector3i pos, QuadLightData out, int side, boolean shade);
}
