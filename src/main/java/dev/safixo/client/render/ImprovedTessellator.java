package dev.safixo.client.render;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.hooks.TessellatorHook;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.*;
import java.util.Arrays;

// Compared to the 1.6.4 Tessellator this one has the improvements:
// - Uses direct Unsafe writes to the upload buffer improving writing performance.
// - Changes immediate mode to a naive VBO drawing/uploading technique with a cached VAO per state combination.
// - Compacts vertex format based in the used attributes (vanilla uses 32-byte at all times).
// - Overall more optimized and clean code.
public class ImprovedTessellator extends Tessellator {
	private static final int UNDEFINED_FORMAT = 12; // start position after position attribute.
	private static final int UNDEFINED_VERTEX_ARRAY = -1;

	public static final int VERTEX_UV     = 0b0001;
	public static final int VERTEX_COLOR  = 0b0010;
	public static final int VERTEX_NORMAL = 0b0100;
	public static final int VERTEX_LIGHT  = 0b1000;

	private static final int MIN_ALLOC = 1024 * 128;

	private GlVertexBuffer vertexBuffer = new GlVertexBuffer(MIN_ALLOC, GL15.GL_STREAM_DRAW);

	private boolean disabledColor;
	public int drawMode, flags, capacity = MIN_ALLOC;

	private boolean isDrawing;
	public int vertices, offset;

	public float xOff, yOff, zOff;
	public int color, light, normal;

	public byte formatFlag = UNDEFINED_FORMAT;
	private byte colorOff, lightOff, normalOff;

	public int drawInd;

	public long vertexPtr = NativeBuffer.nmemAlloc(MIN_ALLOC);
	private ByteBuffer vertexPtrNio = NativeBuffer.wrap(this.vertexPtr);

	private final int[] VERTEX_ARRAYS = new int[0b1111 + 1];

	public ImprovedTessellator() {
		Arrays.fill(VERTEX_ARRAYS, UNDEFINED_VERTEX_ARRAY);
	}

	private int getVertexArray(int flags, int stride) {
		int vertexArray = VERTEX_ARRAYS[flags];

		if (vertexArray != UNDEFINED_VERTEX_ARRAY) {
			return vertexArray;
		}

		vertexArray = GL30.glGenVertexArrays();

		GlVertexArrayObject.bindVertexArray(vertexArray);
		this.vertexBuffer.bind();

		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);

		if ((flags & VERTEX_UV) != 0) {
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 12);
		}
		if ((flags & VERTEX_COLOR) != 0) {
			GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
			GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, this.colorOff);
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
			GL11.glNormalPointer(GL11.GL_BYTE, stride, this.normalOff);
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			GL13.glClientActiveTexture(OpenGlHelper.lightmapTexUnit);

			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GL11.glTexCoordPointer(2, GL11.GL_SHORT, stride, this.lightOff);
		}

		this.vertexBuffer.unbind();
		GlVertexArrayObject.bindVertexArray(0);

		if ((flags & VERTEX_LIGHT) != 0) {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GL13.glClientActiveTexture(OpenGlHelper.defaultTexUnit);
		}
		if ((flags & VERTEX_COLOR) != 0) {
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		}
		if ((flags & VERTEX_UV) != 0) {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
		}

		VERTEX_ARRAYS[flags] = vertexArray;

		return vertexArray;
	}

	@Override
	public int draw() {
		if (!this.isDrawing) {
			throw new IllegalStateException("Not tesselating!");
		}

		int drawMode = this.drawMode;
		int flags = this.flags;
		int offset = this.offset;
		int stride = this.formatFlag;

		int vertexArray = this.getVertexArray(flags, stride);

		GlVertexArrayObject.bindVertexArray(vertexArray);
		this.vertexBuffer.bufferData(this.vertexPtrNio, offset);

		int vertices = this.vertices;
		this.vertexBuffer.draw(drawMode, vertices, 0);
		this.drawInd++;
		this.isDrawing = false;

		return offset;
	}

	public int drawWithoutUpload() {
		int drawMode = this.drawMode;
		int flags = this.flags;
		int offset = this.offset;
		int stride = this.formatFlag;

		int vertexArray = this.getVertexArray(flags, stride);

		GlVertexArrayObject.bindVertexArray(vertexArray);

		int vertices = this.vertices;
		this.vertexBuffer.draw(drawMode, vertices, 0);

		return offset;
	}

	public void resize() {
		int newCapacity = this.capacity * 2;
		long newVertexPtr = NativeBuffer.nmemAlloc(newCapacity);

		this.vertexBuffer.allocate(UnsafeUtil.NULL, newCapacity);
		this.drawInd = -1;

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
		if (this.isDrawing) {
			throw new IllegalStateException("Already tesselating!");
		}

		this.drawMode = drawMode;
		this.formatFlag = UNDEFINED_FORMAT;
		this.isDrawing = true;
		this.flags = 0;
		this.offset = 0;
		this.vertices = 0;
	}

	@Override
	public void setTextureUV(double u, double v) {
		this.flags |= VERTEX_UV;

		if (VertexWriterManager.isCurrentDrawing()) {
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

		if (VertexWriterManager.isCurrentDrawing()) {
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

		if (VertexWriterManager.isCurrentDrawing()) {
			TessellatorHook.setColorRGBA(r, g, b, a);
			return;
		}

		this.flags |= VERTEX_COLOR;

		r &= 255;
		g &= 255;
		b &= 255;
		a &= 255;

		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
			this.color = a << 24 | b << 16 | g << 8 | r;
		} else {
			this.color = r << 24 | g << 16 | b << 8 | a;
		}
	}

	@Override
	public void addVertexWithUV(double x, double y, double z, double u, double v) {
		if (VertexWriterManager.isCurrentDrawing()) {
			TessellatorHook.addVertexWithUV(x + this.xOff, y + this.yOff, z + this.zOff, u, v);
			return;
		}

		if (this.offset + 64 >= this.capacity) {
			this.resize();
		}

		long ptr = this.vertexPtr + this.offset;

		this.flags |= VERTEX_UV;
		int flags = this.flags;

		float xP = (float) x + this.xOff;
		float yP = (float) y + this.yOff;
		float zP = (float) z + this.zOff;

		UnsafeUtil.memPutFloat(ptr + 0, xP);
		UnsafeUtil.memPutFloat(ptr + 4, yP);
		UnsafeUtil.memPutFloat(ptr + 8, zP);

		UnsafeUtil.memPutFloat(ptr + 12, (float) u);
		UnsafeUtil.memPutFloat(ptr + 16, (float) v);

		byte formatFlag = 20;

		if ((flags & VERTEX_COLOR) != 0) {
			this.colorOff = formatFlag;
			UnsafeUtil.memPutInt(ptr + this.colorOff, this.color);
			formatFlag += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			this.normalOff = formatFlag;
			UnsafeUtil.memPutInt(ptr + this.normalOff, this.normal);
			formatFlag += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			this.lightOff = formatFlag;
			UnsafeUtil.memPutInt(ptr + this.lightOff, this.light);
			formatFlag += 4;
		}

		this.formatFlag = formatFlag;
		this.offset += formatFlag;
		this.vertices++;
	}

	@Override
	public void addVertex(double x, double y, double z) {
		VertexWriterManager writer = VertexWriterManager.getCurrentInstance();

		if (writer.isDrawing) {
			TessellatorHook.addVertex(x + this.xOff, y + this.yOff, z + this.zOff);
			return;
		}

		if (this.offset + 64 >= this.capacity) {
			this.resize();
		}

		long ptr = this.vertexPtr + this.offset;
		int flags = this.flags;

		float xP = (float) x + this.xOff;
		float yP = (float) y + this.yOff;
		float zP = (float) z + this.zOff;

		UnsafeUtil.memPutFloat(ptr + 0, xP);
		UnsafeUtil.memPutFloat(ptr + 4, yP);
		UnsafeUtil.memPutFloat(ptr + 8, zP);

		byte formatFlag = (byte) (UNDEFINED_FORMAT + ((flags & VERTEX_UV) << 3));

		if ((flags & VERTEX_COLOR) != 0) {
			this.colorOff = formatFlag;
			UnsafeUtil.memPutInt(ptr + this.colorOff, this.color);
			formatFlag += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			this.normalOff = formatFlag;
			UnsafeUtil.memPutInt(ptr + this.normalOff, this.normal);
			formatFlag += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			this.lightOff = formatFlag;
			UnsafeUtil.memPutInt(ptr + this.lightOff, this.light);
			formatFlag += 4;
		}

		this.formatFlag = formatFlag;
		this.offset += formatFlag;
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
