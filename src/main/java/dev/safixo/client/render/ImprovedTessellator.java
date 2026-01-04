package dev.safixo.client.render;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.hooks.GlStateManager;
import dev.safixo.core.hooks.TessellatorHook;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.*;

import java.nio.*;
import java.util.Arrays;

// TODO: Use newer LWJGL to abuse persistent mapped buffers to improve uploading overhead
//  and use u16 o i16 to compact UVs representations (idk).

// Compared to the 1.6.4 Tessellator this one has the improvements:
// - Uses direct Unsafe writes to the upload buffer improving writing performance.
// - Changes immediate mode to a naive VBO drawing/uploading technique with a cached VAO per state permutation.
// - Compacts vertex format based in the used attributes (vanilla uses 32-byte at all times).
// - Overall more optimized and clean code.
public class ImprovedTessellator extends Tessellator {
	public static final ImprovedTessellator TESSELLATOR = new ImprovedTessellator();

	private static final int UNDEFINED_FORMAT = 12; // start position after position attribute.
	private static final int UNDEFINED_VERTEX_ARRAY = -1;

	public static final int VERTEX_UV     = 0b0100;
	public static final int VERTEX_COLOR  = 0b0010;
	public static final int VERTEX_NORMAL = 0b0001;
	public static final int VERTEX_LIGHT  = 0b1000;

	private static final int MIN_ALLOC = 1024 * 128;

	private final GlVertexBuffer vertexBuffer = new GlVertexBuffer(MIN_ALLOC, GL15.GL_STREAM_DRAW);

	private boolean disabledColor;
	public int drawMode, flags, capacity = MIN_ALLOC;

	public int vertices, offset;

	public float xOff, yOff, zOff;
	public int color, light, normal;

	private int lastFlag = -1;

	public long vertexPtr = NativeBuffer.nmemAlloc(MIN_ALLOC);
	private ByteBuffer vertexPtrNio = NativeBuffer.wrap(this.vertexPtr);

	private final int[] VERTEX_ARRAYS = new int[0b1111 + 1];
	private static final byte[] STRIDES = new byte[0b1111 + 1];

	static {
		for (int flagInd = 0; flagInd <= 0b1111; flagInd++) {
			int stride = 12;

			stride += (flagInd & VERTEX_UV) != 0 ? 8 : 0;
			stride += (flagInd & VERTEX_NORMAL) != 0 ? 4 : 0;
			stride += (flagInd & VERTEX_LIGHT) != 0 ? 4 : 0;
			stride += (flagInd & VERTEX_COLOR) != 0 ? 4 : 0;

			STRIDES[flagInd] = (byte) stride;
		}
	}

	public ImprovedTessellator() {
		Arrays.fill(VERTEX_ARRAYS, UNDEFINED_VERTEX_ARRAY);
	}

	// Caches the stride and buffer state abusing the idea that each format attribute
	// order and offset in the written format is unique to the flag which saves the
	// permutation of current vertex attributes, which means that the count of every
	// permutation possible is 16 (1 bit per attribute, 4 possibles in total => 0b1111 + 1 =>
	// 15 + 1 (0b0000 counts)).
	private int getVertexArray(int flags) {
		int vertexArray = VERTEX_ARRAYS[flags];

		if (vertexArray != UNDEFINED_VERTEX_ARRAY) {
			return vertexArray;
		}

		vertexArray = GL30.glGenVertexArrays();
		int stride = STRIDES[flags];

		GlVertexArrayObject.bindVertexArray(vertexArray);
		this.vertexBuffer.bind();

		GlStateManager.glEnableClientStateDirect(GL11.GL_VERTEX_ARRAY);
		GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);

		int offset = UNDEFINED_FORMAT;

		if ((flags & VERTEX_UV) != 0) {
			GlStateManager.glEnableClientStateDirect(GL11.GL_TEXTURE_COORD_ARRAY);
			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, offset);
			offset += 8;
		}
		if ((flags & VERTEX_COLOR) != 0) {
			GlStateManager.glEnableClientStateDirect(GL11.GL_COLOR_ARRAY);
			GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, offset);
			offset += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			GlStateManager.glEnableClientStateDirect(GL11.GL_NORMAL_ARRAY);
			GL11.glNormalPointer(GL11.GL_BYTE, stride, offset);
			offset += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			GL13.glClientActiveTexture(OpenGlHelper.lightmapTexUnit);

			GlStateManager.glEnableClientStateDirect(GL11.GL_TEXTURE_COORD_ARRAY);
			GL11.glTexCoordPointer(2, GL11.GL_SHORT, stride, offset);
			offset += 4;
		}

		this.vertexBuffer.unbind();
		GlVertexArrayObject.bindVertexArray(0);

		if ((flags & VERTEX_LIGHT) != 0) {
			GlStateManager.glDisableClientStateDirect(GL11.GL_TEXTURE_COORD_ARRAY);
			GL13.glClientActiveTexture(OpenGlHelper.defaultTexUnit);
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			GlStateManager.glDisableClientStateDirect(GL11.GL_NORMAL_ARRAY);
		}
		if ((flags & VERTEX_COLOR) != 0) {
			GlStateManager.glDisableClientStateDirect(GL11.GL_COLOR_ARRAY);
		}
		if ((flags & VERTEX_UV) != 0) {
			GlStateManager.glDisableClientStateDirect(GL11.GL_TEXTURE_COORD_ARRAY);
		}

		VERTEX_ARRAYS[flags] = vertexArray | offset << 24;
		return vertexArray | offset << 24;
	}

	@Override
	public int draw() {
		this.lastFlag = this.flags;
		this.flags = 0;

		int offset = this.offset;

		if (this.disabledColor) {
			this.flushState();
		}

		this.disabledColor = false;
		return offset;
	}

	public void flushState() {
		if (this.vertices == 0 || this.lastFlag == -1) {
			return;
		}

		int vertices = this.vertices;
		int drawMode = this.drawMode;
		int flags = this.lastFlag;
		int offset = this.offset;

		this.vertices = 0;
		this.offset = 0;
		this.lastFlag = -1;

		int packedData = this.getVertexArray(flags);
		int vertexArray = packedData & 0xFFFFF;

		GlVertexArrayObject.bindVertexArray(vertexArray);

		this.vertexBuffer.bufferData(this.vertexPtrNio, offset);
		this.vertexBuffer.draw(drawMode, vertices, 0);
	}

	public void resize() {
		int newCapacity = this.capacity * 2;
		long newVertexPtr = NativeBuffer.nmemAlloc(newCapacity);

		this.vertexBuffer.allocate(UnsafeUtil.NULL, newCapacity);

		UnsafeUtil.memCopy(this.vertexPtr, newVertexPtr, this.offset);
		UnsafeUtil.nmemFree(this.vertexPtr);

		this.vertexPtr = newVertexPtr;
		this.vertexPtrNio = NativeBuffer.wrap(newVertexPtr);
		this.capacity = newCapacity;
	}

	@Override
	public void startDrawingQuads() {
		this.startDrawing(GL11.GL_QUADS);
	}

	@Override
	public void startDrawing(int drawMode) {
		if (this.drawMode != drawMode) {
			this.flushState();
		}

		this.drawMode = drawMode;
		this.flags = 0;
	}

	@Override
	public void setTextureUV(double u, double v) {
		this.flags |= VERTEX_UV;

		if (VertexWriter.isCurrentDrawing()) {
			TessellatorHook.setTextureUV(u, v);
			return;
		}

		long ptr = this.vertexPtr + this.offset;

		UnsafeUtil.memPutFloat(ptr + 12, (float) u);
		UnsafeUtil.memPutFloat(ptr + 16, (float) v);
	}

	@Override
	public void setBrightness(int light) {
		this.flags |= VERTEX_LIGHT;

		if (VertexWriter.isCurrentDrawing()) {
			TessellatorHook.setBrightness(light);
			return;
		}

		this.light = light;
	}

	@Override
	public void setColorOpaque_F(float r, float g, float b) {
		this.setColorOpaque((int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F));
	}

	@Override
	public void setColorRGBA_F(float r, float g, float b, float a) {
		this.setColorRGBA((int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F), (int) (a * 255.0F));
	}

	@Override
	public void setColorOpaque(int par1, int par2, int par3) {
		this.setColorRGBA(par1, par2, par3, 0xFF);
	}

	@Override
	public void setColorRGBA(int r, int g, int b, int a) {
		if (this.disabledColor) {
			return;
		}

		if (VertexWriter.isCurrentDrawing()) {
			TessellatorHook.setColorRGBA(r, g, b, a);
			return;
		}

		this.flags |= VERTEX_COLOR;

		r &= 0xFF;
		g &= 0xFF;
		b &= 0xFF;
		a &= 0xFF;

		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
			this.color = a << 24 | b << 16 | g << 8 | r;
		} else {
			this.color = r << 24 | g << 16 | b << 8 | a;
		}
	}

	@Override
	public void addVertexWithUV(double x, double y, double z, double u, double v) {
		if (PrimitivesFlags.REDIRECT_DRAWING) {
			TessellatorHook.addVertexWithUV(x + this.xOff, y + this.yOff, z + this.zOff, u, v);
			return;
		}

		if (this.offset + 64 >= this.capacity) {
			this.resize();
		}

		this.flags |= VERTEX_UV;

		if (this.lastFlag != this.flags && (this.vertices & 3) == 0) {
			this.flushState();
		}

		long ptr = this.vertexPtr + this.offset;
		int flags = this.flags;

		float xP = (float) x + this.xOff;
		float yP = (float) y + this.yOff;
		float zP = (float) z + this.zOff;

		UnsafeUtil.memPutFloat(ptr + 0, xP);
		UnsafeUtil.memPutFloat(ptr + 4, yP);
		UnsafeUtil.memPutFloat(ptr + 8, zP);

		UnsafeUtil.memPutFloat(ptr + 12, (float) u);
		UnsafeUtil.memPutFloat(ptr + 16, (float) v);

		long writePtr = ptr + 20;

		if ((flags & VERTEX_COLOR) != 0) {
			UnsafeUtil.memPutInt(writePtr, this.color);
			writePtr += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			UnsafeUtil.memPutInt(writePtr, this.normal);
			writePtr += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			UnsafeUtil.memPutInt(writePtr, this.light);
			writePtr += 4;
		}

		this.offset += (int) (writePtr - ptr);
		this.vertices++;
	}

	@Override
	public void addVertex(double x, double y, double z) {
		if (PrimitivesFlags.REDIRECT_DRAWING) {
			TessellatorHook.addVertex(x + this.xOff, y + this.yOff, z + this.zOff);
			return;
		}

		if (this.offset + 64 >= this.capacity) {
			this.resize();
		}

		if (this.lastFlag != this.flags && (this.vertices & 3) == 0) {
			this.flushState();
		}

		long ptr = this.vertexPtr + this.offset;
		int flags = this.flags;

		float xP = (float) x + this.xOff;
		float yP = (float) y + this.yOff;
		float zP = (float) z + this.zOff;

		UnsafeUtil.memPutFloat(ptr + 0, xP);
		UnsafeUtil.memPutFloat(ptr + 4, yP);
		UnsafeUtil.memPutFloat(ptr + 8, zP);

		long writePtr = ptr + UNDEFINED_FORMAT + (flags & VERTEX_UV);

		if ((flags & VERTEX_COLOR) != 0) {
			UnsafeUtil.memPutInt(writePtr, this.color);
			writePtr += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			UnsafeUtil.memPutInt(writePtr, this.normal);
			writePtr += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			UnsafeUtil.memPutInt(writePtr, this.light);
			writePtr += 4;
		}

		this.offset += (int) (writePtr - ptr);
		this.vertices++;
	}

	@Override
	public void setColorOpaque_I(int color) {
		int r = (color >>> 16) & 0xFF;
		int g = (color >>> 8) & 0xFF;
		int b = (color >>> 0) & 0xFF;

		this.setColorOpaque(r, g, b);
	}

	@Override
	public void setColorRGBA_I(int color, int alpha) {
		int r = (color >>> 16) & 0xFF;
		int g = (color >>> 8) & 0xFF;
		int b = (color >>> 0) & 0xFF;

		this.setColorRGBA(r, g, b, alpha);
	}

	@Override
	public void disableColor() {
		this.disabledColor = true;
	}

	@Override
	public void setNormal(float normalX, float normalY, float normalZ) {
		this.flags |= VERTEX_NORMAL;

		int nX = (byte) (normalX * 0x7F);
		int nY = (byte) (normalY * 0x7F);
		int nZ = (byte) (normalZ * 0x7F);

		this.normal = (nX & 0xFF) << 0 | (nY & 0xFF) << 8 | (nZ & 0xFF) << 16;

		if (PrimitivesFlags.REDIRECT_DRAWING) {
			TessellatorHook.setNormal(this.normal);
		}
	}

	@Override
	public void setTranslation(double offX, double offY, double offZ) {
		this.xOff = (float) offX;
		this.yOff = (float) offY;
		this.zOff = (float) offZ;
	}

	@Override
	public void addTranslation(float offX, float offY, float offZ) {
		this.xOff += offX;
		this.yOff += offY;
		this.zOff += offZ;
	}
}
