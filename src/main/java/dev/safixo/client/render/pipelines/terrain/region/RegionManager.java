package dev.safixo.client.render.pipelines.terrain.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.pipelines.terrain.TerrainProgram;
import dev.safixo.client.render.pipelines.terrain.cull.BFSQueue;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.MathExt;

import java.nio.ByteBuffer;

public class RegionManager {
	public final Long2ReferenceOpenHashMap<RegionRender> regionMap = new Long2ReferenceOpenHashMap<>();

	private double lastUpdateX;
	private double lastUpdateZ;

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		int regionX = sectionX >> (RegionRender.BLOCK_SHIFT_X - 4);
		int regionY = sectionY >> (RegionRender.BLOCK_SHIFT_Y - 4);
		int regionZ = sectionZ >> (RegionRender.BLOCK_SHIFT_Z - 4);

		long position = MathExt.asLong(regionX, regionY, regionZ);
		RegionRender region = this.regionMap.get(position);

		if (region == null) {
			region = new RegionRender(sectionX, sectionY, sectionZ);
			this.regionMap.put(position, region);
		}

		return region;
	}

	public void update(CameraData camera, int renderDistance, boolean worldUpdated) {
		ReferenceCollection<RegionRender> regions = this.regionMap.values();

		if (worldUpdated) {
			this.lastUpdateX = camera.cameraXD();
			this.lastUpdateZ = camera.cameraZD();

			for (RegionRender region : regions) {
				region.clear();
			}

			if (RegionAllocation.SPARE_BUFFER != null) {
				RegionAllocation.SPARE_BUFFER.clear();
				RegionAllocation.SPARE_BUFFER = null;
			}

			this.regionMap.clear();
			return;
		}

		double diffX = Math.abs(camera.cameraXD() - this.lastUpdateX);
		double diffZ = Math.abs(camera.cameraZD() - this.lastUpdateZ);

		if (diffX + diffZ >= 80) {
			this.lastUpdateX = camera.cameraXD();
			this.lastUpdateZ = camera.cameraZD();

			this.sanitizeRegions(camera, renderDistance);
		}
	}

	public void sanitizeRegions(CameraData camera, int renderDistance) {
		ReferenceCollection<RegionRender> regions = this.regionMap.values();
		int renderDistanceBlocks = (renderDistance + 1) * 16;

		LongArrayList removedList = new LongArrayList();

		for (RegionRender region : regions) {
			if (MathExt.manhattanDistance(region, camera) > MathExt.square(renderDistanceBlocks)) {
				long regionPos = MathExt.asLong(region.regionX, region.regionY, region.regionZ);

				removedList.add(regionPos);
				region.clear();
			}
		}

		for (long position : removedList) {
			this.regionMap.remove(position);
		}
	}

	private int shadowFBO = -1;
	private int shadowTex = -1;

	public void prepareShadows() {
		if (this.shadowFBO != -1) {
			return;
		}

		GL13.glActiveTexture(GL13.GL_TEXTURE2);

		this.shadowFBO = GL30.glGenFramebuffers();
		this.shadowTex = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.shadowTex);

		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, 1024, 1024, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.shadowFBO);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL11.GL_DEPTH_COMPONENT, GL11.GL_TEXTURE_2D, this.shadowTex, 0);

		GL11.glDrawBuffer(GL11.GL_NONE);
		GL11.glReadBuffer(GL11.GL_NONE);

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
	}

	public void attachShadowRender() {
		GL11.glViewport(0, 0, 1024, 1024);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.shadowFBO);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
	}

	public void deAttachShadowRender() {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		Minecraft mc = Minecraft.getMinecraft();
		GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
	}

	public void drawAllRegions(TerrainProgram shader, BFSQueue queue, CameraData camera, int pass) {
		RegionRender[] regionRenders = queue.regionRenders;

		if (regionRenders == null) {
			return;
		}

		int index;
		int end;
		int inc;

		if (pass == 1) {
			index = queue.regionPos - 1;
			end = -1;
			inc = -1;
		} else {
			index = 0;
			end = queue.regionPos;
			inc = 1;
		}

		while (index != end) {
			RegionRender region = regionRenders[index];

			region.prepareAndDraw(shader, camera, pass);

			if (pass == 0) {
				SectionManager.getCurrentInstance().drawnSolidRenderers += region.sectionsToRender;
			}

			index += inc;
		}

		GL30.glBindVertexArray(0);
	}
 }
