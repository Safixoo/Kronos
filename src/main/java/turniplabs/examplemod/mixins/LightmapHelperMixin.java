package turniplabs.examplemod.mixins;

import net.minecraft.client.GLAllocation;
import net.minecraft.client.render.LightmapHelper;
import net.minecraft.client.render.OpenGLHelper;
import net.minecraft.core.util.helper.Buffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;

@Mixin(value = LightmapHelper.class, remap = false)
public class LightmapHelperMixin {

	@Shadow
	private int lightmapTexture;

	/**
	 * @author Safixo
	 * @reason Faster light-map texture setting.
	 */
	@Overwrite
	public void setLightmapTextureData(int[] data) {
		Buffer.checkBufferSize(1024);
		ByteBuffer buffer = Buffer.buffer;
		buffer.clear();

		for (int i = 0; i < data.length; ++i) {
			int color = data[i];
			int r = color >> 16 & 255;
			int g = color >> 8 & 255;
			int b = color & 255;
			buffer.put((byte)r);
			buffer.put((byte)g);
			buffer.put((byte)b);
		}

		buffer.position(0);
		buffer.limit(768);

		if (this.lightmapTexture == 0) {
			this.lightmapTexture = GLAllocation.generateTexture();
			GL13.glBindTexture(GL11.GL_TEXTURE_2D, this.lightmapTexture);
			GL13.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 16, 16, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, MemoryUtil.memAddress(buffer));

			GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

			GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
			GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

			return;
		}

		GL13.glBindTexture(GL11.GL_TEXTURE_2D, this.lightmapTexture);
		GL13.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 16, 16, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, MemoryUtil.memAddress(buffer));

		GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

		GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL13.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
	}
}
