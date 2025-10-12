package dev.safixo;

import cpw.mods.fml.common.Mod;
import dev.safixo.client.render.SectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.ChunkEvent;

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
	public void onChunkLoad(ChunkEvent.Load event) {
		Chunk chunk = event.getChunk();

		if (Minecraft.getMinecraft().thePlayer == null) {
			return;
		}

		for (int y = 0; y < 16; y++) {
			SectionManager.getCurrentInstance().updateExistentSections(chunk.xPosition, y, chunk.zPosition, true);
		}
	}
}
