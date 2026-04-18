package dev.safixo.client.render.pipelines.entity_model;

import dev.safixo.client.render.gfx.buffer.GlShaderStorageBuffer;
import dev.safixo.client.util.Matrix4Stack;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.core.hooks.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static dev.safixo.client.util.memory.UnsafeUtil.*;

// TODO:
//  - Extract all entity textures (excluding players) to its own texture atlas.
//  - Extract entity vertex data to its own SSBO with the UVs formatted for the
//    entity texture atlas.
//  - Instead of drawing for each entity/model, use a SSBO with the index of each model quad and
//    and its matrix index, and do a only draw command which dispatch all the indices to do vertex-pulling.
//  - Having control of each quad in drawing thanks to vertex-pulling with the SSBO, discard
//    back-faces taking into account the texture opacity.
// Note: With all the improvements, all the models/entities should be rendered in one draw-call
// and one texture bind, although some overhead would come up with the cost of uploading so much data
// with glBufferData. Also in many systems most of the overhead from entity-rendering comes from the state
// changes done and DataWatcher calls before and after ModelRenderer#render, so this doesn't solve everything.
public class ModelQueue {
	public static final ModelQueue MODEL_QUEUE = new ModelQueue(32);
	public GlShaderStorageBuffer matrixDataBuffer;

	public Matrix4f viewMatrix = new Matrix4f();

	private long matricesPtr;
	private long drawData;
	private int[] textures;

	private int size;
	private int position;

	private ModelProgram modelProgram;

	private ModelQueue(int size) {
		this.matricesPtr = NativeBuffer.nmemAlloc(64L * size);
		this.drawData = NativeBuffer.nmemAlloc(4L * size);
		this.textures = new int[size];

		this.size = size;
	}

	private void resize() {
		int pos = this.position;
		int newSize = this.size << 1;

		long newMatrices = NativeBuffer.nmemAlloc(64L * newSize);
		long newDrawData = NativeBuffer.nmemAlloc(4L * newSize);
		int[] newTextures = new int[newSize];

		for (int i = 0; i < pos; i++) {
			Matrix4Stack.copyMat(this.matricesPtr + i * 64L, newMatrices + i * 64L);
			memPutInt(newDrawData + i * 4L, memGetInt(this.drawData + i * 4L));
			newTextures[i] = this.textures[i];
		}

		NativeBuffer.nmemFree(this.matricesPtr);
		NativeBuffer.nmemFree(this.drawData);

		this.matricesPtr = newMatrices;
		this.textures = newTextures;
		this.drawData = newDrawData;
		this.size = newSize;
	}

	public void addToQueue(int texture, int drawData) {
		if (this.position >= this.size) {
			this.resize();
		}

		int pos = this.position++;

		Matrix4Stack.copyMat(GlStateTracker.CURRENT_STACK.top(), this.matricesPtr + pos * 64L);
		memPutInt(this.drawData + pos * 4L, drawData);
		this.textures[pos] = texture;
	}

	public void drawAllQueue() {
		if (AdvModelRenderer.VERTEX_ARRAY_GL20 == null) {
			this.position = 0;
		}

		if (this.position == 0) {
			return;
		}

		if (this.modelProgram == null) {
			this.modelProgram = new ModelProgram();
			this.matrixDataBuffer = new GlShaderStorageBuffer(GL15.GL_STATIC_DRAW, 0);
		}

		ByteBuffer matrixBuffer = NativeBuffer.wrap(this.matricesPtr);
		this.matrixDataBuffer.bufferData(matrixBuffer, this.position * 64);

		this.modelProgram.useProgram();
		this.modelProgram.uploadUniforms();

		int pos = this.position;

		GL11.glDisable(GL11.GL_CULL_FACE);
		AdvModelRenderer.VERTEX_ARRAY_GL20.bind(AdvModelRenderer.VERTEX_BUFFER);

		int[] indices = this.getIndicesSorted(this.textures);
		this.matrixDataBuffer.bindBase();

		for (int j = 0; j < pos; j++) {
			int i = indices[j];

			int texture = this.textures[i];
			int drawData = memGetInt(this.drawData + i * 4L);

			int vertices = drawData & 0xFFFF;
			int offset = drawData >>> 16;

			this.modelProgram.uploadMatrixIndex(i);

			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glDrawArrays(GL11.GL_QUADS, offset, vertices);
		}

		GL30.glBindVertexArray(0);
		GL20.glUseProgram(0);
		GL11.glColor4f(1, 1, 1, 1);

		this.position = 0;
	}

	private int[] getIndicesSorted(int[] textures) {
		int maxTexture = Integer.MIN_VALUE;

		for (int i = 0; i < this.position; i++) {
			maxTexture = Math.max(maxTexture, textures[i]);
		}
		maxTexture += 1;

		int[] indices = new int[this.position];
		int[] hist = new int[maxTexture];

		for (int i = 0; i < this.position; i++) {
			hist[textures[i]]++;
		}

		// turns histogram into a prefix-sum array.
		for (int i = 1; i < maxTexture; i++) {
			hist[i] += hist[i - 1];
		}

		for (int i = 0; i < this.position; i++) {
			indices[--hist[textures[i]]] = i;
		}

		return indices;
	}
}
