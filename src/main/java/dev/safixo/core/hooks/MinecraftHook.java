package dev.safixo.core.hooks;

import dev.safixo.client.render.util.data.BlocksFlags;
import net.minecraft.client.Minecraft;

public class MinecraftHook {
	public static void checkGLError(Minecraft minecraft, String str) {
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		// NO-OP
	}
}
