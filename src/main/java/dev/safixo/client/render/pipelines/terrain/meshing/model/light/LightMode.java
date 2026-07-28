package dev.safixo.client.render.pipelines.terrain.meshing.model.light;

import net.minecraft.client.Minecraft;

public enum LightMode {
    SMOOTH,
    FLAT;

	public static LightMode getCurrent() {
		return Minecraft.getMinecraft().gameSettings.ambientOcclusion == 0 ? FLAT : SMOOTH;
	}
}
