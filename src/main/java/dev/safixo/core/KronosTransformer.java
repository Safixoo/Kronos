package dev.safixo.core;

import net.minecraft.launchwrapper.IClassTransformer;

public class KronosTransformer implements IClassTransformer {
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		System.out.println(transformedName);

		return basicClass;
	}

}
