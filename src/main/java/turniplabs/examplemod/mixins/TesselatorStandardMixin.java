package turniplabs.examplemod.mixins;

import net.minecraft.client.render.terrain.VertexConfig;
import net.minecraft.client.render.terrain.VertexData;
import net.minecraft.client.render.tessellator.TessellatorBase;
import net.minecraft.client.render.tessellator.TessellatorStandard;
import net.minecraft.core.util.helper.MathHelper;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.VertexWriterManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.apache.log4j.builders.appender.SocketAppenderBuilder.LOGGER;

@Mixin(value = TessellatorStandard.class, remap = false)
public abstract class TesselatorStandardMixin extends TessellatorBase {

	@Shadow
	public abstract void checkIsDrawing();

	@Shadow
	public VertexData data;

	@Shadow
	private double offsetX;

	@Shadow
	private double offsetY;

	@Shadow
	private double offsetZ;

	@Shadow
	private double textureU;

	@Shadow
	private double textureV;

	@Shadow
	private int color;

	@Shadow
	private int lightmapCoord;

	@Shadow
	private byte normalX;

	@Shadow
	private byte normalY;

	@Shadow
	private byte normalZ;

//	/**
//	 * @author Safixo
//	 * @reason Uses MemoryUtil fast memory ops.
//	 */
//	@Overwrite
//	public void addVertex(double x, double y, double z) {
//		this.checkIsDrawing();
//		if (this.data.buffer.capacity() < this.data.buffer.position() + 64) {
//			int newSize = this.data.buffer.capacity() * 2;
//			LOGGER.info("Expanding Tessellator Buffer (" + this.data.buffer.capacity() + " -> " + newSize + ")");
//			ByteBuffer newBuffer = ByteBuffer.allocateDirect(newSize).order(ByteOrder.nativeOrder());
//			this.data.buffer.flip();
//			newBuffer.put(this.data.buffer);
//			this.data.buffer = newBuffer;
//		}
//
//		long ptr = MemoryUtil.memAddress(this.data.buffer);
//
//		MemoryUtil.memPutFloat(ptr + 0L, (float) (this.offsetX + x));
//		MemoryUtil.memPutFloat(ptr + 4L, (float) (this.offsetY + y));
//		MemoryUtil.memPutFloat(ptr + 8L, (float) (this.offsetZ + z));
//
//		long offset = 12L;
//
//		VertexConfig config = this.data.config;
//		if (config.enableColor) {
//			MemoryUtil.memPutInt(ptr + offset, this.color);
//			offset += 4L;
//		}
//
//		if (config.enableTexture) {
//			MemoryUtil.memPutFloat(ptr + offset, (float) this.textureU);
//			offset += 4L;
//			MemoryUtil.memPutFloat(ptr + offset, (float) this.textureV);
//			offset += 4L;
//		}
//
//		if (config.enableLightmap) {
//			MemoryUtil.memPutInt(ptr + offset, this.lightmapCoord);
//			offset += 4L;
//		}
//
//		if (config.enableNormal) {
//			MemoryUtil.memPutByte(ptr + offset, this.normalX);
//			offset += 1L;
//			MemoryUtil.memPutByte(ptr + offset, this.normalY);
//			offset += 1L;
//			MemoryUtil.memPutByte(ptr + offset, this.normalZ);
//			offset += 1L;
//		}
//
//		this.data.buffer.position((int) (this.data.buffer.position() + offset));
//
//		this.data.vertexCount++;
//	}

	@Shadow
	public boolean drawing;

	@Inject(method = "addVertex", at = @At("HEAD"), cancellable = true)
	public void passPositionAndUV(double x, double y, double z, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing()) {
			return;
		}

		man.setPos(x, y, z);
		man.addVertex();

		ci.cancel();
	}

	@Inject(method = "setTextureUV", at = @At("HEAD"), cancellable = true)
	public void passUV(double u, double v, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing()) {
			return;
		}

		man.setUv(u, v);
		ci.cancel();
	}

	@Inject(method = "setColorRGBA", at = @At("HEAD"), cancellable = true)
	public void passColor(int r, int g, int b, int a, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing()) {
			return;
		}

		man.setColor(a << 24 | b << 16 | g << 8 | r);
		ci.cancel();
	}

	@Inject(method = "setLightmapCoord", at = @At("HEAD"), cancellable = true)
	public void setLightmap(int lightmapCoord, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing()) {
			return;
		}

		man.setLightMap(lightmapCoord);
		ci.cancel();
	}


	@Inject(method = "setTranslation", at = @At("HEAD"), cancellable = true)
	public void passTranslation(double x, double y, double z, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing()) {
			return;
		}

		man.setTranslation((float) x, (float) y, (float) z);
		ci.cancel();
	}

	@Inject(method = "offsetTranslation", at = @At("HEAD"), cancellable = true)
	public void addOffsetTranslation(float x, float y, float z, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing()) {
			return;
		}

		man.setTranslation(man.transX + x, man.transY + y, man.transZ + z);
		ci.cancel();
	}

	@Inject(method = "checkIsDrawing", at = @At("HEAD"), cancellable = true)
	public void manageDrawing(CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing()) {
			return;
		}

		ci.cancel();
	}
}
