package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.writers.CloudFormat;
import dev.safixo.core.HookUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.resources.Resource;
import net.minecraft.client.resources.ResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

public class CloudRenderer {
	private static final int CLOUD_STRIDE = DefaultVertexFormats.CLOUD_FORMAT.getStride();

	private static final ResourceLocation CLOUD_TEXTURE = new ResourceLocation("textures/environment/clouds.png");
	private static final int CLOUD_WIDTH = 12;
	private static final int CLOUD_HEIGHT = 4;

	private static final int CLOUD_BOTTOM_FACTOR = ColorBGRManager.normToFactor(0.7F);
	private static final int CLOUD_X_FACTOR = ColorBGRManager.normToFactor(0.9F);
	private static final int CLOUD_Z_FACTOR = ColorBGRManager.normToFactor(0.8F);

	private static final byte[] VISIBLE_BITS = new byte[256 * 256];
	private static final int[] CLOUD_TEX_COLOR = new int[256 * 256];

	private static final float CULL_Y = CLOUD_HEIGHT + 1;

	private static RenderBuffer VERTEX_BUFFER;
	private static CloudProgram CLOUD_SHADER;

	private static Field CLOUD_TICK_COUNTER;

	private static final int XP = Direction.EAST;
	private static final int XN = Direction.WEST;

	private static final int ZP = Direction.SOUTH;
	private static final int ZN = Direction.NORTH;

	private static void setupRender(World world, ResourceManager manager, int distance, float partialTick) {
		Vec3 cloudColor = world.getCloudColour(partialTick);
		float r = (float) cloudColor.xCoord;
		float g = (float) cloudColor.yCoord;
		float b = (float) cloudColor.zCoord;

		VertexWriter.setCurrentInstance(VertexWriter.DEFAULT_INSTANCE);
		VertexWriter writer = VertexWriter.getCurrentInstance();

		writer.startDrawing();
		writer.setVertexFormat(DefaultVertexFormats.CLOUD_FORMAT);

		if (VERTEX_BUFFER == null) {
			VERTEX_BUFFER = new RenderBuffer(DefaultVertexFormats.CLOUD_FORMAT, CLOUD_STRIDE * 16384, GL15.GL_STREAM_DRAW);
		}

		if (CLOUD_SHADER == null) {
			processCloudTexture(manager);
			CLOUD_TICK_COUNTER = HookUtils.getField(Minecraft.getMinecraft().renderGlobal, "cloudTickCounter", "field_72773_u");

			CLOUD_SHADER = new CloudProgram();
		}

		CLOUD_SHADER.useProgram();
		CLOUD_SHADER.uploadUniforms(distance, r, g, b);
	}

	private static void clearState(VertexWriter writer) {
		writer.stopDrawing();
		CLOUD_SHADER.disableProgram();
	}

	private static int getCloudTickCounter(RenderGlobal renderGlobal) {
		try {
			return (Integer) CLOUD_TICK_COUNTER.get(renderGlobal);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void renderCloudsFancy(float partialTick) {
		Minecraft mc = Minecraft.getMinecraft();
		WorldClient world = mc.theWorld;

		int renderDistanceChunks = 22;
		int cellDistance = ((renderDistanceChunks + (32 / renderDistanceChunks)) * 16) / CLOUD_WIDTH;

		// Prepare for rendering the clouds.
		CloudRenderer.setupRender(world, mc.getResourceManager(), cellDistance * CLOUD_WIDTH, partialTick);

		float playerX = (float) (mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * partialTick);
		float playerY = (float) (mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * partialTick);
		float playerZ = (float) (mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * partialTick);

		int cloudTickCounter = getCloudTickCounter(mc.renderGlobal);
		double tickPosition = cloudTickCounter + partialTick;

		float viewY = world.provider.getCloudHeight() - playerY + 0.33F;

		double worldX = (playerX + tickPosition * 0.03F) / CLOUD_WIDTH;
		double worldZ = (playerZ + 3.96F) / CLOUD_WIDTH;

		int regionX = MathExt.floor(worldX / 2048.0F);
		int regionZ = MathExt.floor(worldZ / 2048.0F);

		worldX -= regionX * 2048;
		worldZ -= regionZ * 2048;

		int worldFloorX = MathExt.floor(worldX);
		int worldFloorZ = MathExt.floor(worldZ);

		float worldFracX = (float) (worldX - worldFloorX);
		float worldFracZ = (float) (worldZ - worldFloorZ);

		VertexWriter writer = VertexWriter.getCurrentInstance();
		RenderBuffer buffer = VERTEX_BUFFER;

		buffer.bindBuffer(true);

		// Build and write cloud geometry.
		buildGeometry(writer, cellDistance, worldFloorX, worldFloorZ, viewY);

		// Upload geometry.
		buffer.getVertexBuffer().bufferSubData(writer.getVertexDataNio(), 0, writer.getOffset());

		buffer.bindBuffer(false);

		// Draw clouds.
		drawClouds(writer.getVertices(), worldFracX, viewY, worldFracZ);

		// Clear the state.
		CloudRenderer.clearState(writer);
	}

	private static void drawClouds(int vertices, float worldFracX, float viewY, float worldFracZ) {
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		CLOUD_SHADER.setOffset(-worldFracX, viewY, -worldFracZ);

		if (Math.abs(viewY) < CULL_Y) {
			GL11.glDisable(GL11.GL_CULL_FACE);
		}

		VERTEX_BUFFER.bindState(true);

		GlVertexBuffer vertexBuffer = VERTEX_BUFFER.getVertexBuffer();

		// Render depth pre-pass.
		GL11.glColorMask(false, false, false, false);
		vertexBuffer.draw(vertices, 0);

		// Render the actual geometry.
		GL11.glColorMask(true, true, true, true);
		vertexBuffer.draw(vertices, 0);

		VERTEX_BUFFER.bindState(false);

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);

		if (Math.abs(viewY) < CULL_Y) {
			GL11.glEnable(GL11.GL_CULL_FACE);
		}
	}

	private static void buildPXPZ(VertexWriter writer, int cloudX, int cloudZ, int color, int visibleMask) {
		// -X Face
		if ((visibleMask & (1 << XN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX, 0, cloudZ, usedColor);
		}

		// -Z Face
		if ((visibleMask & (1 << ZN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ, usedColor);
			addVertex(writer, cloudX, 0, cloudZ, usedColor);
		}
	}

	private static void buildNXNZ(VertexWriter writer, int cloudX, int cloudZ, int color, int visibleMask) {
		// +X Face
		if ((visibleMask & (1 << XP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX + 1, 0, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ + 1, usedColor);
		}

		// +Z Face
		if ((visibleMask & (1 << ZP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ + 1, usedColor);
		}
	}

	private static void buildNXPZ(VertexWriter writer, int cloudX, int cloudZ, int color, int visibleMask) {
		// -X Face
		if ((visibleMask & (1 << XN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX, 0, cloudZ, usedColor);
		}

		// +Z Face
		if ((visibleMask & (1 << ZP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ + 1, usedColor);
		}
	}

	private static void buildPXNZ(VertexWriter writer, int cloudX, int cloudZ, int color, int visibleMask) {
		// +X Face
		if ((visibleMask & (1 << XP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX + 1, 0, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ + 1, usedColor);
		}

		// -Z Face
		if ((visibleMask & (1 << ZN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ, usedColor);
			addVertex(writer, cloudX, 0, cloudZ, usedColor);
		}
	}

	private static void buildNPY(VertexWriter writer, int cloudX, float cloudY, int cloudZ, int color) {
		// +Y Face
		if (cloudY < -1) {
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ + 1, color);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ + 1, color);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ, color);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ, color);
		} else {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_BOTTOM_FACTOR);
			addVertex(writer, cloudX, 0, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, 0, cloudZ + 1, usedColor);
		}
	}

	private static void buildYInverted(VertexWriter writer, int cloudX, float cloudY, int cloudZ, int color) {
		// -Y Face
		if (cloudY < -1) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_BOTTOM_FACTOR);
			addVertex(writer, cloudX, 0, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, 0, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, 0, cloudZ + 1, usedColor);
		} else {
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ + 1, color);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ + 1, color);
			addVertex(writer, cloudX + 1, CLOUD_HEIGHT, cloudZ, color);
			addVertex(writer, cloudX, CLOUD_HEIGHT, cloudZ, color);
		}
	}

	private static void buildGeometry(VertexWriter writer, int cellDistance, int worldFloorX, int worldFloorZ, float viewY) {
		int maxDistance = (cellDistance * 3) >>> 1;
		writer.ensureCapacity(CLOUD_STRIDE * 16384 * 4);

		// -+X -+Z
		for (int cellX = 1; cellX <= cellDistance; cellX++) {
			for (int cellZ = 1; cellZ <= cellDistance; cellZ++) {
				int width = (cellX + worldFloorX) & 0xFF;
				int height = (cellZ + worldFloorZ) & 0xFF;
				int visibleMask = getCellBits(width, height);

				if (visibleMask == 0 || cellZ + cellX > maxDistance) {
					continue;
				}
				int color = getCloudColor(width, height);

				buildNPY(writer, cellX, viewY, cellZ, color);
				buildPXPZ(writer, cellX, cellZ, color, visibleMask);
			}
		}
		for (int cellX = -cellDistance; cellX < 0; cellX++) {
			for (int cellZ = -cellDistance; cellZ < 0; cellZ++) {
				int width = (cellX + worldFloorX) & 0xFF;
				int height = (cellZ + worldFloorZ) & 0xFF;
				int visibleMask = getCellBits(width, height);

				if (visibleMask == 0 || -cellZ - cellX > maxDistance) {
					continue;
				}
				int color = getCloudColor(width, height);

				buildNPY(writer, cellX, viewY, cellZ, color);
				buildNXNZ(writer, cellX, cellZ, color, visibleMask);
			}
		}

		// +-X -+Z
		for (int cellX = 1; cellX <= cellDistance; cellX++) {
			for (int cellZ = -cellDistance; cellZ < 0; cellZ++) {
				int width = (cellX + worldFloorX) & 0xFF;
				int height = (cellZ + worldFloorZ) & 0xFF;
				int visibleMask = getCellBits(width, height);

				if (visibleMask == 0 || cellX - cellZ > maxDistance) {
					continue;
				}
				int color = getCloudColor(width, height);

				buildNPY(writer, cellX, viewY, cellZ, color);
				buildNXPZ(writer, cellX, cellZ, color, visibleMask);
			}
		}
		for (int cellX = -cellDistance; cellX < 0; cellX++) {
			for (int cellZ = 1; cellZ <= cellDistance; cellZ++) {
				int width = (cellX + worldFloorX) & 0xFF;
				int height = (cellZ + worldFloorZ) & 0xFF;
				int visibleMask = getCellBits(width, height);

				if (visibleMask == 0 || cellZ - cellX > maxDistance) {
					continue;
				}
				int color = getCloudColor(width, height);

				buildNPY(writer, cellX, viewY, cellZ, color);
				buildPXNZ(writer, cellX, cellZ, color, visibleMask);
			}
		}

		for (int cellX = -cellDistance; cellX <= cellDistance; cellX++) {
			int width = (cellX + worldFloorX) & 0xFF;
			int visibleMask = getCellBits(width, worldFloorZ & 0xFF);

			if (visibleMask == 0) {
				continue;
			}
			int color = getCloudColor(width, worldFloorZ & 0xFF);

			buildNPY(writer, cellX, viewY, 0, color);

			if (cellX < 0) {
				buildNXNZ(writer, cellX, 0, color, visibleMask);
				buildNXPZ(writer, cellX, 0, color, visibleMask);
			} else {
				buildPXNZ(writer, cellX, 0, color, visibleMask);
				buildPXPZ(writer, cellX, 0, color, visibleMask);
			}
		}

		for (int cellZ = -cellDistance; cellZ <= cellDistance; cellZ++) {
			int height = (cellZ + worldFloorZ) & 0xFF;
			int visibleMask = getCellBits(worldFloorX & 0xFF, height);

			if (visibleMask == 0) {
				continue;
			}
			int color = getCloudColor(worldFloorX & 0xFF, height);

			if (cellZ != 0) {
				buildNPY(writer, 0, viewY, cellZ, color);
			}

			if (cellZ < 0) {
				buildNXNZ(writer, 0, cellZ, color, visibleMask);
				buildPXPZ(writer, 0, cellZ, color, visibleMask);
			} else {
				buildPXPZ(writer, 0, cellZ, color, visibleMask);
				buildNXPZ(writer, 0, cellZ, color, visibleMask);
			}
		}

		if (Math.abs(viewY) < CULL_Y) {
			buildCenterCells(writer, worldFloorX, viewY, worldFloorZ);
		}
	}

	private static void buildCenterCells(VertexWriter writer, int worldFloorX, float viewY, int worldFloorZ) {
		for (int cellZ = -1; cellZ <= 1; cellZ++) {
			for (int cellX = -1; cellX <= 1; cellX++) {
				int height = (cellZ + worldFloorZ) & 0xFF;
				int width = (cellX + worldFloorX) & 0xFF;
				int color = getCloudColor(width, height);

				if (color == 0) {
					continue;
				}

				if (cellX == 0 && cellZ == 0) {
					buildYInverted(writer, cellX, viewY, cellZ, color);
					continue;
				}

				if (Math.abs(cellX + 0.5F) + Math.abs(cellZ + 0.5F) > 2.0F) {
					continue;
				}

				buildYInverted(writer, cellX, viewY, cellZ, color);

				int visibleMask = getCellBits(width, height);

				buildNXNZ(writer, cellX, cellZ, color, ~visibleMask);
				buildPXPZ(writer, cellX, cellZ, color, ~visibleMask);
				buildPXPZ(writer, cellX, cellZ, color, ~visibleMask);
				buildNXPZ(writer, cellX, cellZ, color, ~visibleMask);
			}
		}
	}

	private static int getCellBits(int x, int y) {
		return VISIBLE_BITS[y | (x << 8)];
	}

	private static int getCloudColor(int x, int y) {
		return CLOUD_TEX_COLOR[y | (x << 8)];
	}

	private static void processCloudTexture(ResourceManager manager) {
		Resource resource = manager.getResource(CLOUD_TEXTURE);
		InputStream inputStream = resource.getInputStream();
		BufferedImage texture;

		try {
			texture = ImageIO.read(inputStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (texture.getWidth() != 256 || texture.getHeight() != 256) {
			throw new RuntimeException("Cloud texture isn't 256x256!, width: " + texture.getWidth() + ", height: " + texture.getHeight());
		}

		Arrays.fill(VISIBLE_BITS, (byte) 0);

		for (int w = 0; w < 256; w++) {
			for (int h = 0; h < 256; h++) {
				int colorInd = h + (w * 256);

				int color = CLOUD_TEX_COLOR[colorInd] = texture.getRGB(w, h);
				int alpha = color >>> 24;

				if (alpha < 100) {
					VISIBLE_BITS[colorInd] = 0;
					CLOUD_TEX_COLOR[colorInd] = 0;
					continue;
				}

				VISIBLE_BITS[colorInd] = 0b11;

				for (int dir = Direction.NORTH; dir < Direction.COUNT; dir++) {
					int x = Direction.x(dir) + w;
					int z = Direction.z(dir) + h;

					color = texture.getRGB(x & 0xFF, z & 0xFF);

					VISIBLE_BITS[colorInd] |= (color >>> 24) < 100 ? (byte) (1 << dir) : 0;
				}
			}
		}
	}

	private static void addVertex(VertexWriter writer, int x, int y, int z, int color) {
		CloudFormat.writeCloudVertex(writer.getTotalOffset(), x, y, z, color);
		writer.addVertexCounter(CLOUD_STRIDE);
	}
}
