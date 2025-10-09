package dev.safixo;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.safixo.client.render.util.data.BlocksFlags;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;

import java.util.logging.LogManager;
import java.util.logging.Logger;

@Mod(modid = KronosMod.MODID, name = "kronos", version = "1.0.0")
public class KronosMod {
	public static final String MODID = "kronos";
	public static final Logger LOGGER = LogManager.getLogManager().getLogger(MODID);

	public KronosMod() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Mod.EventHandler
	public void preInit(FMLInitializationEvent event) {
		//LOGGER.info("Hello from Minecraft!");
	}

	@ForgeSubscribe
	public void onMainMenu(GuiOpenEvent event) {
		BlocksFlags.computeFlagArrays();
		BlocksFlags.processModelMethods();
	}

}
