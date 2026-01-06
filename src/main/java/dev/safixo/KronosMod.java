package dev.safixo;

import cpw.mods.fml.common.Mod;
import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.render.pipelines.terrain.meshing.RebuildListener;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.WorldEvent;

import java.util.logging.LogManager;
import java.util.logging.Logger;

@Mod(modid = KronosMod.MODID, name = "kronos", version = "1.0.0")
public class KronosMod {
	public static final String MODID = "kronos";
	public static final Logger LOGGER = LogManager.getLogManager().getLogger(MODID);

	public KronosMod() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@ForgeSubscribe
	public void onMainMenu(GuiOpenEvent event) {
	}
}
