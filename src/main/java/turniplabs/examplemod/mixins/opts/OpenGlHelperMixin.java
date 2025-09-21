package turniplabs.examplemod.mixins.opts;

import net.minecraft.client.render.OpenGLHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = OpenGLHelper.class, remap = false)
public class OpenGlHelperMixin {

	/**
	 * @author Safixo
	 * @reason Tends to choke the driver, probably enforces
	 * waiting for the GPU to finish.
	 */
	@Overwrite
	public static void checkError(String info) {}
}
