package dev.safixo;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import dev.safixo.core.KronosTransformer;

import java.util.Map;

@IFMLLoadingPlugin.Name("kronos")
@IFMLLoadingPlugin.MCVersion("1.6.4")
@IFMLLoadingPlugin.SortingIndex(200)
public class KronosCoreMod implements IFMLLoadingPlugin {
	@Override
	public String[] getLibraryRequestClass() {
		return null;
	}

	@Override
	public String[] getASMTransformerClass() {
		return new String[] {
			KronosTransformer.class.getName()
		};
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> map) {

	}
}
