package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.model.NormalUtil;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;

public class Quad {
	private static final int X = 0, Y = 4, Z = 8;
	private static final int U = 12, V = 16;
	private static final int COLOR = 20, LIGHT = 24;
	private static final int NORMAL = 28;

	private static final long VERTEX_SIZE = 32;
	private static final long VERTICES = 4;
	private static final long QUAD_SIZE = VERTEX_SIZE * VERTICES;

	private final long ptr;
	private int computedNormal = -1;

	public Quad() {
		this.ptr = NativeBuffer.nmemAlloc(QUAD_SIZE);
	}

	public float getX(int index) {
		return UnsafeUtil.memGetFloat(index * VERTEX_SIZE + this.ptr + X);
	}

	public float getY(int index) {
		return UnsafeUtil.memGetFloat(index * VERTEX_SIZE + this.ptr + Y);
	}

	public float getZ(int index) {
		return UnsafeUtil.memGetFloat(index * VERTEX_SIZE + this.ptr + Z);
	}

	public float getU(int index) {
		return UnsafeUtil.memGetFloat(index * VERTEX_SIZE + this.ptr + U);
	}

	public float getV(int index) {
		return UnsafeUtil.memGetFloat(index * VERTEX_SIZE + this.ptr + V);
	}

	public int getColor(int index) {
		return UnsafeUtil.memGetInt(index * VERTEX_SIZE + this.ptr + COLOR);
	}

	public int getLight(int index) {
		return UnsafeUtil.memGetInt(index * VERTEX_SIZE + this.ptr + LIGHT);
	}

	public int getNormal(int index) {
		return UnsafeUtil.memGetInt(index * VERTEX_SIZE + this.ptr + NORMAL);
	}

	public void setX(int index, float x) {
		UnsafeUtil.memPutFloat(index * VERTEX_SIZE + this.ptr + X, x);
	}

	public void setY(int index, float y) {
		UnsafeUtil.memPutFloat(index * VERTEX_SIZE + this.ptr + Y, y);
	}

	public void setZ(int index, float z) {
		UnsafeUtil.memPutFloat(index * VERTEX_SIZE + this.ptr + Z, z);
	}

	public void setU(int index, float u) {
		UnsafeUtil.memPutFloat(index * VERTEX_SIZE + this.ptr + U, u);
	}

	public void setV(int index, float v) {
		UnsafeUtil.memPutFloat(index * VERTEX_SIZE + this.ptr + V, v);
	}

	public void setColor(int index, int color) {
		UnsafeUtil.memPutInt(index * VERTEX_SIZE + this.ptr + COLOR, color);
	}

	public void setLight(int index, int light) {
		UnsafeUtil.memPutInt(index * VERTEX_SIZE + this.ptr + LIGHT, light);
	}

	public void setNormal(int index, int normal) {
		UnsafeUtil.memPutInt(index * VERTEX_SIZE + this.ptr + NORMAL, normal);
	}

	public void computeNormal() {
		this.computedNormal = NormalUtil.assignNormal(this);
	}

	public int getNormalVector() {
		return NormalUtil.getNormalVector(this.computedNormal);
	}

	public int getNormalEnum() {
		return NormalUtil.getNormalEnum(this.computedNormal);
	}

	public void reset() {
		this.computedNormal = -1;
	}

	public void delete() {
		NativeBuffer.nmemFree(this.ptr);
	}

	public float getPosRelX(int x, int index) {
		return this.getX(index) - (x & RegionConstants.BLOCK_BITS_X);
	}

	public float getPosRelY(int y, int index) {
		return this.getY(index) - (y & RegionConstants.BLOCK_BITS_Y);
	}

	public float getPosRelZ(int z, int index) {
		return this.getZ(index) - (z & RegionConstants.BLOCK_BITS_Z);
	}
}
