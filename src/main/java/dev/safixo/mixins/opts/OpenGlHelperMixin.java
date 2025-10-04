package dev.safixo.mixins.opts;

import net.minecraft.client.render.OpenGLHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = OpenGLHelper.class, remap = false)
public class OpenGlHelperMixin {
	/**
	 * @author Safixo
	 * @reason Tends to choke the driver, by forcing synchronization (it seems)
	 * disable it for now.
	 */
	@Overwrite
	public static void checkError(String info) {}
}
