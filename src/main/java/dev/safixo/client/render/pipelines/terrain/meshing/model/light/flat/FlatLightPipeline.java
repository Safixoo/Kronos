package dev.safixo.client.render.pipelines.terrain.meshing.model.light.flat;


import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.LightPipeline;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.LightDataAccess;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.QuadLightData;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.MeshDirection;
import org.joml.Vector3i;

import java.util.Arrays;

import static dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.LightDataAccess.*;

/**
 * A light pipeline which implements "classic-style" lighting through simply using the light value of the adjacent
 * block to a face.
 */
// TODO: Add check offset in cases where the block can suppress lighting and it is not aligned to the block grid.
public class FlatLightPipeline implements LightPipeline {
    /**
     * The cache which light data will be accessed from.
     */
    private final LightDataAccess lightCache;

    public FlatLightPipeline(LightDataAccess lightCache) {
        this.lightCache = lightCache;
    }

    @Override
    public void calculate(Quad quad, Vector3i pos, QuadLightData out, int side, boolean shade) {
        int lightmap;

        // To match vanilla behavior, use the cull face if it exists/is available
        if (side != MeshDirection.GENERIC) {
            lightmap = getOffsetLightmap(pos, side);
            Arrays.fill(out.br, VoxelMesher.SHADE_FULL_FACTOR[side]);
        } else {
			lightmap = getEmissiveLightmap(this.lightCache.get(pos));
			Arrays.fill(out.br, 1.0F); // change by an approx.
        }

        Arrays.fill(out.lm, lightmap);
    }

    /**
     * When vanilla computes an offset lightmap with flat lighting, it passes the original BlockState but the
     * offset BlockPos to {@link LevelRenderer#getLightColor(BlockAndTintGetter, BlockState, BlockPos)}.
     * This does not make much sense but fixes certain issues, primarily dark quads on light-emitting blocks
     * behind tinted glass. {@link LightDataAccess} cannot efficiently store lightmaps computed with
     * inconsistent values so this method exists to mirror vanilla behavior as closely as possible.
     */
    private int getOffsetLightmap(Vector3i pos, int face) {
        int word = this.lightCache.get(pos);

        // Check emissivity of the origin state
        if (unpackEM(word)) {
            return MathExt.getLightmapCoord(15, 15);
        }

        // Use light values from the offset pos, but luminance from the origin pos
        int adjWord = this.lightCache.get(pos, face);
        return MathExt.getLightmapCoord(unpackSL(adjWord), Math.max(unpackBL(adjWord), unpackLU(word)));
    }
}
