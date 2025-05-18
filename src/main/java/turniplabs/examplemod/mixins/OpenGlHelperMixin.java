package turniplabs.examplemod.mixins;

import net.minecraft.client.render.OpenGLHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = OpenGLHelper.class, remap = false)
public class OpenGlHelperMixin {

	/**
	 * @author Safixo
	 * @reason Disable error checking.
	 */
	@Overwrite
	public static void checkError(String info) {

	}
}
