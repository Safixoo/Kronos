package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.pipelines.terrain.SectionManager;

public class RegionBuffer extends RenderBuffer {
	public RegionBuffer(int size, int hint) {
		super(size, hint);
	}

	@Override
	public void clear() {
		SectionManager.getCurrentInstance().removeMemory(this.getCapacity());
		super.clear();
	}

	@Override
	public void allocateSpace(int size, int hint) {
		SectionManager.getCurrentInstance().removeMemory(this.getCapacity());
		super.allocateSpace(size, hint);
		SectionManager.getCurrentInstance().addMemory(size);
	}
}
