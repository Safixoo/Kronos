package dev.safixo.client.render.pipelines.terrain.meshing.model.light.smooth;

import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.LightPipeline;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.LightDataAccess;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.QuadLightData;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.MeshDirection;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * A light pipeline which produces smooth interpolated lighting and ambient occlusion for model quads. This
 * implementation makes a number of improvements over vanilla's own "smooth lighting" option. In no particular order:
 * <p>
 * - Corner blocks are now selected from the correct set of neighbors above block faces (fixes MC-148689 and MC-12558)
 * - Shading issues caused by anisotropy are fixed by re-orientating quads to a consistent ordering (fixes MC-138211)
 * - Inset block faces are correctly shaded by their neighbors, fixing a number of problems with non-full blocks such as
 *   grass paths (fixes MC-11783 and MC-108621)
 * - Blocks next to emissive blocks are too bright (MC-260989)
 * - Synchronization issues between the main render thread's light engine and chunk build worker threads are corrected
 *   by copying light data alongside block states, fixing a number of inconsistencies in baked chunks (no open issue)
 * <p>
 * This implementation also includes a significant number of optimizations:
 * <p>
 * - Computed light data for a given block face is cached and re-used again when multiple quads exist for a given
 *   facing, making complex block models less expensive to render
 * - The light data cache encodes as much information as possible into integer words to improve cache locality and
 *   to eliminate the multiple array lookups that would otherwise be needed, significantly speeding up this section
 * - Block faces aligned to the block grid use a fast-path for mapping corner light values to vertices without expensive
 *   interpolation or blending, speeding up most block renders
 * - Some critical code paths have been re-written to hit the JVM's happy path, allowing it to perform auto-vectorization
 *   of the blend functions
 * - Information about a given model quad is cached to enable the light pipeline to make certain assumptions and skip
 *   unnecessary computation
 */
public class SmoothLightPipeline implements LightPipeline {
    /**
     * The cache which light data will be accessed from.
     */
    private final LightDataAccess lightCache;

    /**
     * The cached face data for each side of a block, both inset and outset.
     */
    private final AoFaceData[] cachedFaceData = new AoFaceData[6 * 2];

    /**
     * The position at which the cached face data was taken at.
     */
    private long cachedPos = Long.MIN_VALUE;

    /**
     * A temporary array for storing the intermediary results of weight data for non-aligned face blending.
     */
    private final float[] weights = new float[4];

    public SmoothLightPipeline(LightDataAccess cache) {
        this.lightCache = cache;

        for (int i = 0; i < this.cachedFaceData.length; i++) {
            this.cachedFaceData[i] = new AoFaceData();
        }
    }

    @Override
    public void calculate(Quad quad, Vector3i pos, QuadLightData out, int side, boolean shade) {
        this.updateCachedData(MathExt.asLong(pos.x, pos.y, pos.z));

		AoNeighborInfo neighborInfo = AoNeighborInfo.get(side);

		boolean aligned = this.isAligned(neighborInfo, quad, pos);

        if (side != MeshDirection.GENERIC && aligned) {
			if (LightDataAccess.unpackFO(this.lightCache.get(pos))) {
				this.applyAlignedFullFace(pos, side, out, shade);
			} else {
				this.applyAlignedPartialFace(neighborInfo, quad, pos, side, out, shade);
			}
        } else if (side != MeshDirection.GENERIC) {
            this.applyParallelFace(neighborInfo, quad, pos, side, out, shade);
        } else {
            this.applyNonParallelFace(neighborInfo, quad, pos, MeshDirection.YP, out, shade);
        }

		if (LightDataAccess.unpackEM(this.lightCache.get(pos))) {
			for (int i = 0; i < 4; i++) {
				out.br[i] = 1.0f;
			}
		}
    }

	private boolean isAligned(AoNeighborInfo neighborInfo, Quad quad, Vector3i pos) {
		float cx = getBlockRelX(quad, pos, 0);
		float cy = getBlockRelY(quad, pos, 0);
		float cz = getBlockRelZ(quad, pos, 0);

		return MathExt.equals(0.0F, neighborInfo.getDepth(cx, cy, cz));
	}

    /**
     * Quickly calculates the light data for a full grid-aligned quad. This represents the most common case (outward
     * facing quads on a full-block model) and avoids interpolation between neighbors as each corner will only ever
     * have two contributing sides.
     * Flags: IS_ALIGNED, !IS_PARTIAL
     */
    private void applyAlignedFullFace(Vector3i pos, int dir, QuadLightData out, boolean shade) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, true, shade);

		out.lm[0] = faceData.lm[0];
		out.lm[1] = faceData.lm[1];
		out.lm[2] = faceData.lm[2];
		out.lm[3] = faceData.lm[3];

		out.br[0] = faceData.ao[0];
		out.br[1] = faceData.ao[1];
		out.br[2] = faceData.ao[2];
		out.br[3] = faceData.ao[3];
    }

    /**
     * Calculates the light data for a grid-aligned quad that does not cover the entire block volume's face.
     * Flags: IS_ALIGNED, IS_PARTIAL
     */
    private void applyAlignedPartialFace(AoNeighborInfo neighborInfo, Quad quad, Vector3i pos, int dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = clamp(getBlockRelX(quad, pos, i));
            float cy = clamp(getBlockRelY(quad, pos, i));
            float cz = clamp(getBlockRelZ(quad, pos, i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);
            this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, true, shade);
        }
    }

    /**
     * This method is the same as {@link #applyNonParallelFace(AoNeighborInfo, Quad, Vector3i, int, QuadLightData,
	 * boolean)} but with the check for a depth of approximately 0 removed. If the quad is parallel but not
     * aligned, all of its vertices will have the same depth and this depth must be approximately greater than 0,
     * meaning the check for 0 will always return false.
     * Flags: !IS_ALIGNED, IS_PARALLEL
     */
    private void applyParallelFace(AoNeighborInfo neighborInfo, Quad quad, Vector3i pos, int dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
			float cx = clamp(getBlockRelX(quad, pos, i));
			float cy = clamp(getBlockRelY(quad, pos, i));
			float cz = clamp(getBlockRelZ(quad, pos, i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            float depth = neighborInfo.getDepth(cx, cy, cz);

            // If the quad is approximately grid-aligned (not inset) to the other side of the block, avoid unnecessary
            // computation by treating it is as aligned
            if (MathExt.equals(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, false, shade);
            } else {
                // Blend the occlusion factor between the blocks directly beside this face and the blocks above it
                // based on how inset the face is. This fixes a few issues with blocks such as farmland and paths.
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, weights, i, out, shade);
            }
        }
    }

    /**
     * Flags: !IS_ALIGNED, !IS_PARALLEL
     */
    private void applyNonParallelFace(AoNeighborInfo neighborInfo, Quad quad, Vector3i pos, int dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
			float cx = clamp(getBlockRelX(quad, pos, i));
			float cy = clamp(getBlockRelY(quad, pos, i));
			float cz = clamp(getBlockRelZ(quad, pos, i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            float depth = neighborInfo.getDepth(cx, cy, cz);

            // If the quad is approximately grid-aligned (not inset), avoid unnecessary computation by treating it is as aligned
            if (MathExt.equals(depth, 0.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, true, shade);
            } else if (MathExt.equals(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, false, shade);
            } else {
                // Blend the occlusion factor between the blocks directly beside this face and the blocks above it
                // based on how inset the face is. This fixes a few issues with blocks such as farmland and paths.
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, weights, i, out, shade);
            }
        }
    }

    private void applyAlignedPartialFaceVertex(Vector3i pos, int dir, float[] w, int i, QuadLightData out, boolean offset, boolean shade) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, offset, shade);

        if (!faceData.hasUnpackedLightData()) {
            faceData.unpackLightData();
        }

        float sl = faceData.getBlendedSkyLight(w);
        float bl = faceData.getBlendedBlockLight(w);
        float ao = faceData.getBlendedShade(w);

        out.br[i] = ao;
        out.lm[i] = getLightMapCoord(sl, bl);
    }

    private void applyInsetPartialFaceVertex(Vector3i pos, int dir, float n1d, float n2d, float[] w, int i, QuadLightData out, boolean shade) {
        AoFaceData n1 = this.getCachedFaceData(pos, dir, false, shade);

        if (!n1.hasUnpackedLightData()) {
            n1.unpackLightData();
        }

        AoFaceData n2 = this.getCachedFaceData(pos, dir, true, shade);

        if (!n2.hasUnpackedLightData()) {
            n2.unpackLightData();
        }

        // Blend between the direct neighbors and above based on the passed weights
        float ao = (n1.getBlendedShade(w) * n1d) + (n2.getBlendedShade(w) * n2d);
        float sl = (n1.getBlendedSkyLight(w) * n1d) + (n2.getBlendedSkyLight(w) * n2d);
        float bl = (n1.getBlendedBlockLight(w) * n1d) + (n2.getBlendedBlockLight(w) * n2d);

        out.br[i] = ao;
        out.lm[i] = getLightMapCoord(sl, bl);
    }

    /** used exclusively in irregular face to avoid new heap allocations each call. */
    private final Vector3f vertexNormal = new Vector3f();
    private final AoFaceData tmpFace = new AoFaceData();

    private AoFaceData gatherInsetFace(Quad quad, Vector3i blockPos, int vertexIndex, int lightFace, boolean shade) {
		float cx = getBlockRelX(quad, blockPos, vertexIndex);
		float cy = getBlockRelY(quad, blockPos, vertexIndex);
		float cz = getBlockRelZ(quad, blockPos, vertexIndex);

        final float w1 = AoNeighborInfo.get(lightFace).getDepth(cx, cy, cz);

        if (MathExt.equals(w1, 0)) {
            return getCachedFaceData(blockPos, lightFace, true, shade);
        } else if (MathExt.equals(w1, 1)) {
            return getCachedFaceData(blockPos, lightFace, false, shade);
        } else {
            this.tmpFace.reset();
            final float w0 = 1 - w1;
            return AoFaceData.weightedMean(getCachedFaceData(blockPos, lightFace, true, shade), w0, getCachedFaceData(blockPos, lightFace, false, shade), w1, tmpFace);
        }
    }

    /**
     * Calculates the light data for a quad that does not follow any grid and is not parallel to it's light face.
     * Flags: !IS_ALIGNED, !IS_PARTIAL, !IS_FULL
     */
    private void applyIrregularFace(Vector3i blockPos, Quad quad, QuadLightData out, boolean shade) {
        final float[] w = this.weights;
        final float[] aoResult = out.br;
        final int[] lightResult = out.lm;

        for (int i = 0; i < 4; i++) {
            Vector3f normal = MathExt.unpackNormal(quad.getNormalVector(), this.vertexNormal);

            float ao = 0, sky = 0, block = 0, maxAo = 0;
            float maxSky = 0, maxBlock = 0;

            final float x = normal.x();

			float cx = getBlockRelX(quad, blockPos, i);
			float cy = getBlockRelY(quad, blockPos, i);
			float cz = getBlockRelZ(quad, blockPos, i);

            if (!MathExt.equals(0f, x)) {
				int face = x > 0 ? Direction.EAST : Direction.WEST;
				AoFaceData fd = this.gatherInsetFace(quad, blockPos, i, face, shade);
                AoNeighborInfo.get(face).calculateCornerWeights(cx, cy, cz, w);
                 float n = x * x;
				float a = fd.getBlendedShade(w);
				float s = fd.getBlendedSkyLight(w);
				float b = fd.getBlendedBlockLight(w);
                ao += n * a;
                sky += n * s;
                block += n * b;
                maxAo = a;
                maxSky = s;
                maxBlock = b;
            }

            final float y = normal.y();

            if (!MathExt.equals(0f, y)) {
				int face = y > 0 ? Direction.UP : Direction.DOWN;
				AoFaceData fd = this.gatherInsetFace(quad, blockPos, i, face, shade);
                AoNeighborInfo.get(face).calculateCornerWeights(cx, cy, cz, w);
				float n = y * y;
				float a = fd.getBlendedShade(w);
				float s = fd.getBlendedSkyLight(w);
				float b = fd.getBlendedBlockLight(w);
                ao += n * a;
                sky += n * s;
                block += n * b;
                maxAo = Math.max(maxAo, a);
                maxSky = Math.max(maxSky, s);
                maxBlock = Math.max(maxBlock, b);
            }

            final float z = normal.z();

            if (!MathExt.equals(0f, z)) {
                final int face = z > 0 ? Direction.SOUTH : Direction.NORTH;
                final AoFaceData fd = this.gatherInsetFace(quad, blockPos, i, face, shade);
                AoNeighborInfo.get(face).calculateCornerWeights(cx, cy, cz, w);
				float n = z * z;
                float a = fd.getBlendedShade(w);
                final float s = fd.getBlendedSkyLight(w);
                final float b = fd.getBlendedBlockLight(w);
                ao += n * a;
                sky += n * s;
                block += n * b;
                maxAo = Math.max(maxAo, a);
                maxSky = Math.max(maxSky, s);
                maxBlock = Math.max(maxBlock, b);
            }

            aoResult[i] = (ao + maxAo) * 0.5f;
            lightResult[i] = (((int) ((sky + maxSky) * 0.5f) & 0xF0) << 16) | ((int) ((block + maxBlock) * 0.5f) & 0xF0);
        }
    }

    private void applySidedBrightness(AoFaceData out, int face, boolean shade) {
        float brightness = VoxelMesher.SIDE_LIGHT_MULTIPLIER[face];
        float[] ao = out.ao;

        for (int i = 0; i < ao.length; i++) {
            ao[i] *= brightness;
        }
    }

    /**
     * Returns the cached data for a given facing or calculates it if it hasn't been cached.
     */
    private AoFaceData getCachedFaceData(Vector3i pos, int face, boolean offset, boolean shade) {
        AoFaceData data = this.cachedFaceData[offset ? face : face + 6];

        if (!data.hasLightData()) {
            data.initLightData(this.lightCache, pos, face, offset);

			this.applySidedBrightness(data, face, shade);

            data.unpackLightData();
        }

        return data;
    }

    private void updateCachedData(long key) {
        if (this.cachedPos != key) {
            for (AoFaceData data : this.cachedFaceData) {
                data.reset();
            }

            this.cachedPos = key;
        }
    }

    /**
     * Clamps the given float to the range [0.0, 1.0].
     */
    private static float clamp(float v) {
        if (v < 0.0f) {
            return 0.0f;
        } else if (v > 1.0f) {
            return 1.0f;
        }

        return v;
    }

	/**
	 * Returns the X vertex pos relative to the block.
	 */
	private static float getBlockRelX(Quad quad, Vector3i block, int index) {
		return quad.getPosRelX(block.x, index);
	}

	/**
	 * Returns the Y vertex pos relative to the block.
	 */
	private static float getBlockRelY(Quad quad, Vector3i block, int index) {
		return quad.getPosRelY(block.y, index);
	}

	/**
	 * Returns the Z vertex pos relative to the block.
	 */
	private static float getBlockRelZ(Quad quad, Vector3i block, int index) {
		return quad.getPosRelZ(block.z, index);
	}

    /**
     * Returns a texture coordinate on the light map texture for the given block and sky light values.
     */
    private static int getLightMapCoord(float sl, float bl) {
        return (((int) sl & 0xFF) << 16) | ((int) bl & 0xFF);
    }

}
