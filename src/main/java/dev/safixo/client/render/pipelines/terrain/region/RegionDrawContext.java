package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import org.lwjgl.opengl.*;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class RegionDrawContext {
	private final boolean[] shouldCachePass = new boolean[RegionConstants.RENDER_PASSES];

	private final int[] drawCount = new int[RegionConstants.RENDER_PASSES];
	private final short[][] renderIndices = new short[RegionConstants.RENDER_PASSES][RegionConstants.REGION_SECTION_SIZE];
	private final int[] visibleCount = new int[RegionConstants.RENDER_PASSES];

	private int visibleSet = -1;

	// TODO: In some cases even this code isn't even correct (ex: inside a region sometimes the result is invalid but
	//  because the camera didn't move in the exact way to invalidate indices, the result is re-used and culling
	//  artifacts are seen).
	public boolean mismatchAndCopy(RegionRender region, CameraData camera, int pass) {
		int regionVis = getRegionVisibleFaces(camera.intX, camera.intY, camera.intZ, region.centerBlockX(), region.centerBlockY(), region.centerBlockZ());
		int oldRegionVis = this.visibleSet;

		this.visibleSet = regionVis;

		if (regionVis != oldRegionVis || this.visibleCount[pass] != region.getRenderIndex()) {
			this.visibleCount[pass] = region.getRenderIndex();
			return false;
		}

		final short[] lastRenderIndices = this.renderIndices[pass];
		final short[] renderIndices = region.renderIndices;
		final int maxIndex = region.getRenderIndex();

		int index = 0;

		// Mismatch of section indices.
		while (index < maxIndex && lastRenderIndices[index] == renderIndices[index]) {
			index++;
		}

		boolean canBeCached = index == maxIndex;

		// A mismatch was found, copy the indices from the mismatch index.
		while (index < maxIndex) {
			lastRenderIndices[index] = renderIndices[index++];
		}

		return canBeCached;
	}

	public static int getRegionVisibleFaces(int originX, int originY, int originZ, int centerRegionX, int centerRegionY, int centerRegionZ) {
		int planes = 1 << MeshDirection.GENERIC;

		planes |= greaterThan(originX, (centerRegionX - RegionConstants.RADIUS_X - 19)) << Direction.EAST;
		planes |= greaterThan(originY, (centerRegionY - RegionConstants.RADIUS_Y - 19)) << Direction.UP;
		planes |= greaterThan(originZ, (centerRegionZ - RegionConstants.RADIUS_Z - 19)) << Direction.SOUTH;

		planes |= lessThan(originX, (centerRegionX + RegionConstants.RADIUS_X + 19)) << Direction.WEST;
		planes |= lessThan(originY, (centerRegionY + RegionConstants.RADIUS_Y + 19)) << Direction.DOWN;
		planes |= lessThan(originZ, (centerRegionZ + RegionConstants.RADIUS_Z + 19)) << Direction.NORTH;

		return planes;
	}

	private static int lessThan(int a, int b) {
		return (a - b) >>> 31;
	}

	private static int greaterThan(int a, int b) {
		return (b - a) >>> 31;
	}


	public boolean isPassCacheable(int pass) {
		return this.shouldCachePass[pass];
	}

	public void invalidatePass(int pass) {
		this.shouldCachePass[pass] = false;
	}

	public void validatePass(int pass) {
		this.shouldCachePass[pass] = true;
	}

	public void setDrawCount(int pass, int size) {
		this.drawCount[pass] = size;
	}

	public void multiDrawData(RegionRender region, CameraData camera, TerrainProgram shader, int pass) {
		int drawCount = this.drawCount[pass];

		if (drawCount == 0) {
			return;
		}

		this.setupRegionState(region, shader, camera, pass);

		if (pass == RegionConstants.SOLID_PASS) {
			if (RegionManager.SUPPORT_INDIRECT) {
				this.multiDrawArraysIndirect(region, drawCount, pass);
			} else {
				this.multiDrawArraysDirect(region, drawCount, pass);
			}
		} else {
			if (RegionManager.SUPPORT_INDIRECT) {
				this.multiDrawElementsIndirect(region, drawCount, pass);
			} else {
				this.multiDrawElementsDirect(region, drawCount, pass);
			}
		}
	}

	public void setupRegionState(RegionRender region, TerrainProgram shader, CameraData camera, int pass) {
		RenderBuffer vertexBuffer = region.getBuffer();

		vertexBuffer.bind(region.getIndexBuffer());

		int blockRegionX = region.regionX << RegionConstants.BLOCK_SHIFT_X;
		int blockRegionY = region.regionY << RegionConstants.BLOCK_SHIFT_Y;
		int blockRegionZ = region.regionZ << RegionConstants.BLOCK_SHIFT_Z;

		// Setup camera and region offset.
		shader.setupRegionOffset(camera, blockRegionX, blockRegionY, blockRegionZ);
	}

	private void multiDrawArraysDirect(RegionRender region, int drawCount, int pass) {
		long pFirst = region.getFirstPtr(pass);
		long pCount = region.getCountPtr(pass);

		// As of now count and first use the pointer but with some offset, so simply offset
		// count itself to make the same effect.
		IntBuffer firstBuff = NativeBuffer.wrap(pFirst).asIntBuffer();
		IntBuffer countBuff = NativeBuffer.wrap(pCount).asIntBuffer();

		((Buffer) firstBuff).limit(drawCount);
		((Buffer) countBuff).limit(drawCount);

		GL14.glMultiDrawArrays(GL11.GL_QUADS, firstBuff, countBuff);
	}

	private void multiDrawArraysIndirect(RegionRender region, int drawCount, int pass) {
		ByteBuffer indirectBuffer = region.getIndirectBuffer(pass);

		((Buffer) indirectBuffer).limit(drawCount * RegionRender.INDIRECT_ARRAYS_STRUCT_SIZE);
		GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS, indirectBuffer, drawCount, 0);
	}

	private void multiDrawElementsDirect(RegionRender region, int drawCount, int pass) {
		long pFirst = region.getFirstPtr(pass);
		long pCount = region.getCountPtr(pass);
		long pIndex = region.getIndexPtr();

		// glMultiDrawElementsBaseVertex for poor people (people that doesn't have LWJGL3)
		for (int i = 0; i < drawCount; i++) {
			int count = UnsafeUtil.memGetInt(pCount + (i << 2L));
			int first = UnsafeUtil.memGetInt(pFirst + (i << 2L));
			int index = UnsafeUtil.memGetInt(pIndex + (i << 2L));

			GL32.glDrawElementsBaseVertex(GL11.GL_QUADS, count, GL11.GL_UNSIGNED_SHORT, index, first);
		}
	}

	private void multiDrawElementsIndirect(RegionRender region, int drawCount, int pass) {
		ByteBuffer indirectBuffer = region.getIndirectBuffer(pass);

		((Buffer) indirectBuffer).limit(drawCount * RegionRender.INDIRECT_ELEMENTS_STRUCT_SIZE);
		GL43.glMultiDrawElementsIndirect(GL11.GL_QUADS, GL11.GL_UNSIGNED_SHORT, indirectBuffer, drawCount, 0);
	}
}
