package dev.safixo.client.render.pipelines.entity_model;

import dev.safixo.client.render.gfx.buffer.GlShaderStorageBuffer;
import dev.safixo.client.render.gfx.state.GlMatrixTracker;
import dev.safixo.client.render.gfx.state.GlTextureTracker;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.collection.Matrix4Stack;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.core.HookUtils;
import dev.safixo.core.hooks.MinecraftHook;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;

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
	public static final ModelQueue INSTANCE = new ModelQueue(32);
	public GlShaderStorageBuffer matrixDataBuffer;

	public Matrix4f viewMatrix = new Matrix4f();

	private long matricesPtr;
	private long drawData;
	private ResourceLocation[] textureResources;
	private int[] textures;
	private byte[] light;

	private int size;
	private int position;

	private ModelProgram modelProgram;
	public Reference2IntOpenHashMap<ResourceLocation> textureMap = new Reference2IntOpenHashMap<>();

	private ModelQueue(int size) {
		this.matricesPtr = NativeBuffer.nmemAlloc(64L * size);
		this.drawData = NativeBuffer.nmemAlloc(4L * size);
		this.textures = new int[size];
		this.textureResources = new ResourceLocation[size];
		this.light = new byte[size];

		this.textureMap.defaultReturnValue(-1);
		this.size = size;
	}

	private void resize() {
		int pos = this.position;
		int newSize = this.size << 1;

		ResourceLocation[] newTextureResource = new ResourceLocation[newSize];
		long newMatrices = NativeBuffer.nmemAlloc(64L * newSize);
		long newDrawData = NativeBuffer.nmemAlloc(4L * newSize);
		int[] newTextures = new int[newSize];
		byte[] newLight = new byte[newSize];

		for (int i = 0; i < pos; i++) {
			Matrix4Stack.copyMat(this.matricesPtr + i * 64L, newMatrices + i * 64L);
			memPutInt(newDrawData + i * 4L, memGetInt(this.drawData + i * 4L));
			newTextures[i] = this.textures[i];
			newLight[i] = this.light[i];
			newTextureResource[i] = this.textureResources[i];
		}

		NativeBuffer.nmemFree(this.matricesPtr);
		NativeBuffer.nmemFree(this.drawData);

		this.textureResources = newTextureResource;
		this.matricesPtr = newMatrices;
		this.textures = newTextures;
		this.light = newLight;
		this.drawData = newDrawData;
		this.size = newSize;
	}

	public void addToQueue(int drawData) {
		if (this.position >= this.size) {
			this.resize();
		}

		int pos = this.position++;
		int texture = this.textureMap.getInt(MinecraftHook.ENTITY_TEX);
		int light = TerrainFormat.compressLightmap((int) GlTextureTracker.MU & 0xFFFF | (int) GlTextureTracker.MV << 16);

		Matrix4Stack.copyMat(GlMatrixTracker.CURRENT_STACK.top(), this.matricesPtr + pos * 64L);
		memPutInt(this.drawData + pos * 4L, drawData);

		if (texture == -1) {
			texture = GlTextureTracker.TEXTURE_PER_UNIT[0];
			this.textureMap.put(MinecraftHook.ENTITY_TEX, texture);
		}

		this.textures[pos] = texture;
		this.light[pos] = (byte) light;
	}

	private GlVertexArrayObject lastVertexArray;

	public void drawAllQueue() {
		if (AdvancedModelRenderer.VERTEX_ARRAY_GL20 == null) {
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

		int maxPos = this.position;

		Minecraft mc = Minecraft.getMinecraft();
		EntityRenderer render = mc.entityRenderer;

		OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		mc.getTextureManager().bindTexture(render.locationLightMap);

		OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		GL11.glColor4f(1, 1, 1, 1);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);

		if (this.lastVertexArray != AdvancedModelRenderer.VERTEX_ARRAY_GL20) {
			this.lastVertexArray = AdvancedModelRenderer.VERTEX_ARRAY_GL20;

			this.lastVertexArray.bind(AdvancedModelRenderer.VERTEX_BUFFER);
			this.matrixDataBuffer.bindBase();
		} else {
			this.lastVertexArray.bind(AdvancedModelRenderer.VERTEX_BUFFER);
		}

		// minimize texture binds by sorting per texture.
		int[] indices = getIndicesSorted(this.textures, this.position);

		for (int readIndex = 0; readIndex < maxPos; readIndex++) {
			int dataIndex = indices[readIndex];

			int texture = this.textures[dataIndex];
			int light = this.light[dataIndex] & 0xFF;

			int drawData = memGetInt(this.drawData + dataIndex * 4L);
			int vertices = drawData & 0xFFFF;
			int offset = drawData >>> 16;

			// assume the texture is so it is not actually binding per iteration.
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL42.glDrawArraysInstancedBaseInstance(GL11.GL_QUADS, offset, vertices, 1, dataIndex | light << 16);
		}

		GL30.glBindVertexArray(0);
		GL20.glUseProgram(0);
		GL11.glColor4f(1, 1, 1, 1);
		GL11.glEnable(GL11.GL_BLEND);

		this.position = 0;
	}

	private static int[] getIndicesSorted(int[] textures, int position) {
		int maxTexture = Integer.MIN_VALUE;

		for (int i = 0; i < position; i++) {
			maxTexture = Math.max(maxTexture, textures[i]);
		}
		maxTexture += 1;

		int[] indices = new int[position];
		int[] hist = new int[maxTexture];

		for (int i = 0; i < position; i++) {
			hist[textures[i]]++;
		}

		// turns histogram into a prefix-sum array.
		for (int i = 1; i < maxTexture; i++) {
			hist[i] += hist[i - 1];
		}

		for (int i = 0; i < position; i++) {
			indices[--hist[textures[i]]] = i;
		}

		return indices;
	}
}
