package dev.safixo.core;

import cpw.mods.fml.common.asm.transformers.AccessTransformer;

import java.io.IOException;

public class KronosAccessTransformer extends AccessTransformer {
	public KronosAccessTransformer() throws IOException {
		super("kronos_at.cfg");
	}
}
