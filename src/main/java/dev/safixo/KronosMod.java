package dev.safixo;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.safixo.client.render.util.data.BlocksFlags;
import turniplabs.halplibe.util.GameStartEntrypoint;
import turniplabs.halplibe.util.RecipeEntrypoint;

public class KronosMod implements ModInitializer, RecipeEntrypoint, GameStartEntrypoint {
    public static final String MOD_ID = "kronos";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
    }

	@Override
	public void onRecipesReady() {
	}

	@Override
	public void initNamespaces() {

	}

	@Override
	public void beforeGameStart() {
	}

	@Override
	public void afterGameStart() {
		BlocksFlags.computeFlagArrays();
		BlocksFlags.processModelMethods();
	}
}
