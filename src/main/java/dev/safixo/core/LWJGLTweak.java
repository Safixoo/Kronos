package dev.safixo.core;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

public class LWJGLTweak implements ITweaker {
	@Override
	public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {

	}

	public void injectIntoClassLoader(LaunchClassLoader classLoader) {
	}

	public String getLaunchTarget() {
		return "dev.safixo.core.NoopLaunchTarget";
	}

	@Override
	public String[] getLaunchArguments() {
		return new String[0];
	}
}

