package dev.safixo.client.render;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.state.GlDrawTracker;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.hooks.AsyncBlockHook;
import dev.safixo.core.hooks.VertexRedirector;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;
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
	private static final Reference2ReferenceArrayMap<Thread, ImprovedTessellator> TESSELLATORS = new Reference2ReferenceArrayMap<>(16);
	private static final boolean INSTANT_FLUSH = false;

	protected static final byte[] STRIDES = new byte[0b1111 + 1];
	private static final int MIN_ALLOC = 512 * 1024;
	private static final int UNDEFINED_FORMAT = 12; // start position after position attribute.
	private static final int UNDEFINED_VERTEX_ARRAY = -1;

	public static final int VERTEX_UV     = 0b1000;
	public static final int VERTEX_COLOR  = 0b0010;
	public static final int VERTEX_NORMAL = 0b0001;
	public static final int VERTEX_LIGHT  = 0b0100;

	private VertexWriter current;
	private final int[] vertexArrays = new int[0b1111 + 1];
	private int drawMode;

	private final DrawStateRecorder drawRecorder = new DrawStateRecorder();
	private GlVertexBuffer vertexBuffer;

	private int capacity, lastOffset;
	private long vertexPtr;
	private ByteBuffer vertexPtrNio;

	private boolean disabledColor;
	public long headPtr, upperBoundCapacity;

	public int flags;

	public int color, light, normal;
	public double dx, dy, dz;

	static {
		Tessellator.instance = getTessellator();
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
		int newCapacity = MIN_ALLOC;
		long newVertexPtr = NativeBuffer.nmemAlloc(newCapacity);;

		this.headPtr = newVertexPtr;
		this.vertexPtr = newVertexPtr;
		this.vertexPtrNio = NativeBuffer.wrap(newVertexPtr);
		this.capacity = newCapacity;
		this.upperBoundCapacity = newCapacity + newVertexPtr - 64;

		Arrays.fill(this.vertexArrays, UNDEFINED_VERTEX_ARRAY);
	}

	// The vertex buffer is reused in cloud rendering, to avoid re-binding vertex-buffers
	public GlVertexBuffer getVertexBuffer() {
		if (this.vertexBuffer == null) {
			this.vertexBuffer = new GlVertexBuffer(MIN_ALLOC, GL15.GL_DYNAMIC_DRAW);
		}

		return this.vertexBuffer;
	}

	public void setCurrentVertexWriter(VertexWriter writer) {
		this.current = writer;
	}

	public static ImprovedTessellator getTessellator() {
		Thread thread = Thread.currentThread();
		ImprovedTessellator tes = TESSELLATORS.get(Thread.currentThread());

		if (tes == null) {
			TESSELLATORS.put(thread, tes = new ImprovedTessellator());
		}

		return tes;
	}

	public int getVertexArray(int flags) {
		int vertexArray = this.vertexArrays[flags];

		if (vertexArray != UNDEFINED_VERTEX_ARRAY) {
			return vertexArray;
		}

		vertexArray = GL30.glGenVertexArrays();
		int stride = STRIDES[flags];

		GL30.glBindVertexArray(vertexArray);

		this.vertexBuffer.bind();
		setupVertexState(flags, stride);

		GL30.glBindVertexArray(0);
		this.vertexBuffer.unbind();
		cleanupVertexState(flags);

		return this.vertexArrays[flags] = vertexArray;
	}

	protected static void setupVertexState(int flags, int stride) {
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);

		int offset = UNDEFINED_FORMAT;

		if ((flags & VERTEX_UV) != 0) {
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, offset);
			offset += 8;
		}
		if ((flags & VERTEX_COLOR) != 0) {
			GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
			GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, offset);
			offset += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
			GL11.glNormalPointer(GL11.GL_BYTE, stride, offset);
			offset += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			GL13.glClientActiveTexture(OpenGlHelper.lightmapTexUnit);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GL11.glTexCoordPointer(2, GL11.GL_SHORT, stride, offset);
		}
	}

	protected static void cleanupVertexState(int flags) {
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

		if ((flags & VERTEX_LIGHT) != 0) {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GL13.glClientActiveTexture(OpenGlHelper.defaultTexUnit);
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
		}
		if ((flags & VERTEX_COLOR) != 0) {
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		}
		if ((flags & VERTEX_UV) != 0) {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
	}

	@Override
	public int draw() {
		int byteCount = (int) (this.headPtr - this.vertexPtr) - this.lastOffset;

		if (FrameDebug.DEBUG_DRAWS) {
			FrameDebug.DRAW_CALLS_REQUESTED++;
			FrameDebug.VERTICES += byteCount / STRIDES[this.flags];
		}

		this.drawRecorder.addDraw(GlDrawTracker.TRACKED_STATE, this.flags, byteCount);

		if (INSTANT_FLUSH || this.disabledColor) {
			this.flushState();
		} else {
			this.lastOffset += byteCount;
		}

		this.flags = 0;
		this.disabledColor = false;

		return byteCount;
	}

	public void flushState() {
		if (this.headPtr == this.vertexPtr || this.drawRecorder.getDrawCount() == 0) {
			return;
		}

		if (this.vertexBuffer == null) {
			this.vertexBuffer = new GlVertexBuffer(MIN_ALLOC, GL15.GL_DYNAMIC_DRAW);
		}

		int offset = (int) (this.headPtr - this.vertexPtr);

		if (FrameDebug.DEBUG_DRAWS) {
			FrameDebug.BYTES += offset;
			FrameDebug.UPLOADS++;
		}

		this.vertexBuffer.bufferData(this.vertexPtrNio, 0, offset);
		this.drawRecorder.flushAllDraws(this, this.drawMode);

		this.headPtr = this.vertexPtr;
		this.lastOffset = 0;
	}

	public void resize() {
		int newCapacity = this.capacity * 2;
		long newVertexPtr = NativeBuffer.nmemAlloc(newCapacity);

		int currentOffset = (int) (this.headPtr - this.vertexPtr);

		UnsafeUtil.memCopy(this.vertexPtr, newVertexPtr, currentOffset);
		NativeBuffer.nmemFree(this.vertexPtr);

		this.headPtr = newVertexPtr + currentOffset;
		this.vertexPtr = newVertexPtr;
		this.vertexPtrNio = NativeBuffer.wrap(newVertexPtr);
		this.capacity = newCapacity;
		this.upperBoundCapacity = newVertexPtr + newCapacity - 64;
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
		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.setTextureUV(this.current, u, v);
			return;
		}

		this.flags |= VERTEX_UV;
		long ptr = this.headPtr;

		UnsafeUtil.memPutFloat(ptr + 12, (float) u);
		UnsafeUtil.memPutFloat(ptr + 16, (float) v);
	}

	@Override
	public void setBrightness(int light) {
		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.setLight(this.current, light);
			return;
		}

		this.flags |= VERTEX_LIGHT;
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
	public void setColorOpaque(int r, int g, int b) {
		this.setColorRGBA(r, g, b, 0xFF);
	}

	@Override
	public void setColorRGBA(int r, int g, int b, int a) {
		if (this.disabledColor) {
			return;
		}

		r &= 0xFF;
		g &= 0xFF;
		b &= 0xFF;
		a &= 0xFF;

		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
			this.color = a << 24 | b << 16 | g << 8 | r;
		} else {
			this.color = r << 24 | g << 16 | b << 8 | a;
		}

		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.setColor(this.current, this.color);
			return;
		}

		this.flags |= VERTEX_COLOR;
	}

	@Override
	public void addVertexWithUV(double x, double y, double z, double u, double v) {
		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.addVertexWithUV(this.current, x, y, z, u, v);
			return;
		}

		this.flags |= VERTEX_UV;

		if (this.headPtr >= this.upperBoundCapacity) {
			this.resize();
		}

		long ptr = this.headPtr;
		int flags = this.flags;

		float xP = (float) (x + this.dx);
		float yP = (float) (y + this.dy);
		float zP = (float) (z + this.dz);

		UnsafeUtil.memPutFloat(ptr, xP); ptr += 4;
		UnsafeUtil.memPutFloat(ptr, yP); ptr += 4;
		UnsafeUtil.memPutFloat(ptr, zP); ptr += 4;

		UnsafeUtil.memPutFloat(ptr, (float) u); ptr += 4;
		UnsafeUtil.memPutFloat(ptr, (float) v); ptr += 4;

		if ((flags & VERTEX_COLOR) != 0) {
			UnsafeUtil.memPutInt(ptr, this.color);
			ptr += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			UnsafeUtil.memPutInt(ptr, this.normal);
			ptr += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			UnsafeUtil.memPutInt(ptr, this.light);
			ptr += 4;
		}

		this.headPtr = ptr;
	}

	@Override
	public void addVertex(double x, double y, double z) {
		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.addVertex(this.current, x, y, z);
			return;
		}

		if (this.headPtr >= this.upperBoundCapacity) {
			this.resize();
		}

		long ptr = this.headPtr;
		int flags = this.flags;

		float xP = (float) (x + this.dx);
		float yP = (float) (y + this.dy);
		float zP = (float) (z + this.dz);

		UnsafeUtil.memPutFloat(ptr, xP); ptr += 4;
		UnsafeUtil.memPutFloat(ptr, yP); ptr += 4;
		UnsafeUtil.memPutFloat(ptr, zP); ptr += 4;

		ptr += (flags & VERTEX_UV);

		if ((flags & VERTEX_COLOR) != 0) {
			UnsafeUtil.memPutInt(ptr, this.color);
			ptr += 4;
		}
		if ((flags & VERTEX_NORMAL) != 0) {
			UnsafeUtil.memPutInt(ptr, this.normal);
			ptr += 4;
		}
		if ((flags & VERTEX_LIGHT) != 0) {
			UnsafeUtil.memPutInt(ptr, this.light);
			ptr += 4;
		}

		this.headPtr = ptr;
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
		this.normal = MathExt.packedNormal(normalX, normalY, normalZ);

		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.setNormal(this.current, this.normal);
			return;
		}

		this.flags |= VERTEX_NORMAL;
	}

	@Override
	public void setTranslation(double offX, double offY, double offZ) {
		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.setOffset(this.current, offX, offY, offZ);
			return;
		}

		this.dx = offX;
		this.dy = offY;
		this.dz = offZ;
	}

	@Override
	public void addTranslation(float offX, float offY, float offZ) {
		if (PrimitivesFlags.REDIRECT_DRAWING || AsyncBlockHook.isAsync()) {
			VertexRedirector.addOffset(this.current, offX, offY, offZ);
			return;
		}

		this.dx += offX;
		this.dy += offY;
		this.dz += offZ;
	}
}
