package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.writers.CloudFormat;
import dev.safixo.core.HookUtils;
import dev.safixo.core.hooks.GlStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.resources.Resource;
import net.minecraft.client.resources.ResourceManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import static dev.safixo.client.render.vertex.writers.CloudFormat.*;
import static dev.safixo.client.util.ColorBGRManager.multiplyColor;

public class CloudRenderer {
	private static final int CLOUD_STRIDE = DefaultVertexFormats.CLOUD_FORMAT.getStride();

	private static final ResourceLocation CLOUD_TEXTURE = new ResourceLocation("textures/environment/clouds.png");
	public static final int CLOUD_WIDTH = 12;
	private static final int CLOUD_HEIGHT = 4;

	private static final int CLOUD_BOTTOM_FACTOR = ColorBGRManager.normToFactor(0.7F);
	private static final int CLOUD_X_FACTOR = ColorBGRManager.normToFactor(0.9F);
	private static final int CLOUD_Z_FACTOR = ColorBGRManager.normToFactor(0.8F);

	private static final int[] CLOUD_TEX_COLOR = new int[256 * 256];

	private static final float CULL_Y = CLOUD_HEIGHT + 1;

	private static RenderBuffer VERTEX_BUFFER;
	private static CloudProgram CLOUD_SHADER;

	private static Field CLOUD_TICK_COUNTER;

	private static final int XP = Direction.EAST;
	private static final int XN = Direction.WEST;

	private static final int ZP = Direction.SOUTH;
	private static final int ZN = Direction.NORTH;

	public static final int MAX_CELL_DISTANCE = 50;

	private static final short[] CELL_XY_DATA = new short[MathExt.square(MAX_CELL_DISTANCE * 2 + 1)];
	private static final int[] MAX_DISTANCE_INDEX = new int[MAX_CELL_DISTANCE + 1];

	private static final int EMPTY_CLOUD = 0;

	static {
		Arrays.fill(MAX_DISTANCE_INDEX, Integer.MIN_VALUE);
		ArrayList<Short> indices = new ArrayList<>();

		for (int cellX = -MAX_CELL_DISTANCE; cellX <= MAX_CELL_DISTANCE; cellX++) {
			for (int cellZ = -MAX_CELL_DISTANCE; cellZ <= MAX_CELL_DISTANCE; cellZ++) {
				indices.add((short) ((cellX + MAX_CELL_DISTANCE) << 8 | (cellZ + MAX_CELL_DISTANCE) & 0xFF));
			}
		}

		Short[] cellIndicesBoxed = indices.toArray(new Short[0]);
		Arrays.sort(cellIndicesBoxed, new ShortDistanceComp());

		for (int i = 0; i < indices.size(); i++) {
			short cellData = cellIndicesBoxed[i];

			CELL_XY_DATA[i] = cellData;
			int cellZ = (cellData & 0xFF) - MAX_CELL_DISTANCE;
			int cellX = (cellData >>> 8) - MAX_CELL_DISTANCE;

			int distance = Math.min(MAX_CELL_DISTANCE, (int) (0.5D + Math.sqrt(MathExt.square(cellX) + MathExt.square(cellZ))));
			MAX_DISTANCE_INDEX[distance] = Math.max(MAX_DISTANCE_INDEX[distance], i);
		}
	}

	private static void setupRender(World world, ResourceManager manager, float partialTick, int distance) {
		Vec3 cloudColor = world.getCloudColour(partialTick);
		float r = (float) cloudColor.xCoord;
		float g = (float) cloudColor.yCoord;
		float b = (float) cloudColor.zCoord;

		VertexWriter.setCurrentInstance(VertexWriter.DEFAULT_INSTANCE);
		VertexWriter writer = VertexWriter.getCurrentInstance();

		writer.startDrawing();
		writer.setVertexFormat(DefaultVertexFormats.CLOUD_FORMAT);

		if (VERTEX_BUFFER == null) {
			VERTEX_BUFFER = new RenderBuffer(DefaultVertexFormats.CLOUD_FORMAT, 0, GL15.GL_STATIC_DRAW);
		}

		if (CLOUD_SHADER == null) {
			processCloudTexture(manager);
			CLOUD_TICK_COUNTER = HookUtils.getField(Minecraft.getMinecraft().renderGlobal, "cloudTickCounter", "field_72773_u");

			CLOUD_SHADER = new CloudProgram();
		}

		CLOUD_SHADER.useProgram();
		CLOUD_SHADER.uploadUniforms(r, g, b, distance);
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

		int renderDistance = MathExt.getCanonicalRenderDistance(mc.gameSettings);
		int cellDistance = Math.max((int) (GlStateTracker.FOG_END / CLOUD_WIDTH), (renderDistance * 16) / CLOUD_WIDTH) - 1;
		cellDistance = Math.min(cellDistance, MAX_CELL_DISTANCE);

		// Prepare for rendering the clouds.
		CloudRenderer.setupRender(world, mc.getResourceManager(), partialTick, cellDistance);

		EntityLivingBase player = mc.renderViewEntity;
		float playerX = (float) MathExt.lerp(player.lastTickPosX, player.posX, partialTick);
		float playerY = (float) MathExt.lerp(player.lastTickPosY, player.posY, partialTick);
		float playerZ = (float) MathExt.lerp(player.lastTickPosZ, player.posZ, partialTick);

		float cloudHeight = world.provider.getCloudHeight();
		float viewY = cloudHeight - playerY + 0.33F;

		int maxDistance = (int) (MathExt.square(cellDistance) - MathExt.square(viewY / CLOUD_WIDTH));
		cellDistance = (int) Math.sqrt(maxDistance);

		int cloudTickCounter = getCloudTickCounter(mc.renderGlobal);
		double tickPosition = cloudTickCounter + partialTick;

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

		FrustumCuller.prepareCloudFrustum(viewY);

		// Generate and write the clouds' geometry.
		buildGeometry(writer, cellDistance, worldFracX, worldFracZ, worldFloorX, worldFloorZ, viewY);

		// Upload the geometry.
		buffer.getVertexBuffer().bufferData(writer.getVertexDataNio(), writer.getOffset());

		// Draw the clouds.
		drawClouds(writer.getVertices(), worldFracX, viewY, worldFracZ);

		// Clear the state.
		clearState(writer);
	}

	private static void drawClouds(int vertices, float worldFracX, float viewY, float worldFracZ) {
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		CLOUD_SHADER.setOffset(-worldFracX - MAX_CELL_DISTANCE, viewY, -worldFracZ - MAX_CELL_DISTANCE);
		VERTEX_BUFFER.bindState(true);

		GlVertexBuffer vertexBuffer = VERTEX_BUFFER.getVertexBuffer();
		vertexBuffer.draw(vertices, 0);

		VERTEX_BUFFER.bindState(false);

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
	}

	private static final float EPSILON = 1E-1F;

	private static void buildGeometry(VertexWriter writer, int cellDistance,
									  float worldFracX, float worldFracZ,
									  int worldFloorX, int worldFloorZ, float viewY) {
		writer.ensureCapacity(CLOUD_STRIDE * 32768);

		int maxIteration = MAX_DISTANCE_INDEX[cellDistance];

		int startX = (worldFracX < 0.5f ? 0 : 1) + MAX_CELL_DISTANCE;
		int startZ = (worldFracZ < 0.5f ? 0 : 1) + MAX_CELL_DISTANCE;

		worldFloorX -= MAX_CELL_DISTANCE;
		worldFloorZ -= MAX_CELL_DISTANCE;

		int insideCellsInd = -viewY >= EPSILON && -viewY <= CLOUD_HEIGHT + EPSILON ? MAX_DISTANCE_INDEX[1] : -1;

		for (int i = 0; i <= maxIteration; i++) {
			int cellData = CELL_XY_DATA[i];

			int cellX = cellData >>> 8;
			int cellZ = cellData & 0xFF;

			int width = (cellX + worldFloorX) & 0xFF;
			int height = (cellZ + worldFloorZ) & 0xFF;
			int color = getCloudColor(width, height);

			if (color == EMPTY_CLOUD || !FrustumCuller.cloudWithinFrustumBounds(cellX, cellZ)) {
				continue;
			}

			int visibleMask = color >>> 24;
			long cellVertex = formatPosition(cellX, 0, cellZ);

			if (cellX < startX) {
				if ((visibleMask & (1 << XP)) != 0) {
					long ptr = writer.getTotalOffset();
					long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_X_FACTOR));
					// +X Face
					addVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
					addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
					addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
					addVertex(ptr, vertex + formatPosition(1, 0, 1));
					pushQuad(writer);
				}
			} else if ((visibleMask & (1 << XN)) != 0) {
				long ptr = writer.getTotalOffset();
				long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_X_FACTOR));
				// -X Face
				addVertex(ptr, vertex + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(0, 0, 0));
				pushQuad(writer);
			}

			if (cellZ < startZ) {
				if ((visibleMask & (1 << ZP)) != 0) {
					long ptr = writer.getTotalOffset();
					long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_Z_FACTOR));
					// +Z Face
					addVertex(ptr, vertex + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;
					addVertex(ptr, vertex + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
					addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
					addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1));
					pushQuad(writer);
				}
			} else if ((visibleMask & (1 << ZN)) != 0) {
				long ptr = writer.getTotalOffset();
				long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_Z_FACTOR));
				// -Z Face
				addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(0, 0, 0));
				pushQuad(writer);
			}

			long ptr = writer.getTotalOffset();

			if (viewY < -1) {
				long vertex = cellVertex | formatColor(color);
				addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0));
			} else {
				long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_BOTTOM_FACTOR));
				addVertex(ptr, vertex); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
				addVertex(ptr, vertex + formatPosition(0, 0, 1));
			}

			pushQuad(writer);

			// If were inside a cloud, build its interior geometry.
			if (i <= insideCellsInd) {
				buildCenterCells(writer, worldFloorX, worldFloorZ, viewY, i);
			}
		}
	}


	private static void buildCenterCells(VertexWriter writer, int worldFloorX, int worldFloorZ, float viewY, int index) {
		int cellData = CELL_XY_DATA[index];

		int cellX = cellData >>> 8;
		int cellZ = cellData & 0xFF;

		int width = (cellX + worldFloorX) & 0xFF;
		int height = (cellZ + worldFloorZ) & 0xFF;
		int color = getCloudColor(width, height);

		if (color == EMPTY_CLOUD) {
			return;
		}

		long cellVertex = formatPosition(cellX, 0, cellZ);
		long ptr = writer.getTotalOffset();

		// +Y Face.
		long vertexYP = cellVertex | formatColor(color);
		addVertex(ptr, vertexYP + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexYP + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexYP + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexYP + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		// -Y Face.
		long vertexYN = cellVertex | formatColor(multiplyColor(color, CLOUD_BOTTOM_FACTOR));
		addVertex(ptr, vertexYN + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexYN + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexYN + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexYN + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;

		long vertexZ = cellVertex | formatColor(multiplyColor(color, CLOUD_Z_FACTOR));
		// -Z Face
		addVertex(ptr, vertexZ + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexZ + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexZ + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexZ + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		// +Z Face
		addVertex(ptr, vertexZ + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexZ + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexZ + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexZ + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;

		long vertexX = cellVertex | formatColor(multiplyColor(color, CLOUD_X_FACTOR));
		// +X Face
		addVertex(ptr, vertexX + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexX + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexX + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexX + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
		// -X Face
		addVertex(ptr, vertexX + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexX + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexX + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		addVertex(ptr, vertexX + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;

		if (!(viewY < -1)) {
			long vertex = cellVertex | formatColor(color);
			addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
			addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
			addVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
			addVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0));
		} else {
			long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_BOTTOM_FACTOR));
			addVertex(ptr, vertex); ptr += CLOUD_STRIDE;
			addVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
			addVertex(ptr, vertex + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
			addVertex(ptr, vertex + formatPosition(0, 0, 1));
		}

		for (int quad = 0; quad < 7; quad++) {
			pushQuad(writer);
		}
	}

	private static int getCloudIndex(int x, int y) {
		return x << 8 | y;
	}

	private static int getCloudColor(int x, int y) {
		return CLOUD_TEX_COLOR[getCloudIndex(x, y)];
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

		int alphaThreshold = 100;

		for (int w = 0; w < 256; w++) {
			for (int h = 0; h < 256; h++) {
				int colorInd = getCloudIndex(w, h);

				int color = texture.getRGB(w, h);
				int alpha = color >>> 24;

				if (alpha < alphaThreshold) {
					CLOUD_TEX_COLOR[colorInd] = EMPTY_CLOUD;
					continue;
				}

				int visibleBits = Direction.UP_BIT | Direction.DOWN_BIT;

				for (int dir = Direction.NORTH; dir < Direction.COUNT; dir++) {
					int x = Direction.x(dir) + w;
					int z = Direction.z(dir) + h;

					int neighborColor = texture.getRGB(x & 0xFF, z & 0xFF);
					int neighborAlpha = neighborColor >>> 24;

					visibleBits |= neighborAlpha < alphaThreshold ? (1 << dir) : 0;
				}

				CLOUD_TEX_COLOR[colorInd] = color & 0xFFFFFF | visibleBits << 24;
			}
		}
	}

	private static void addVertex(long ptr, long vertex) {
		writeCloudVertex(ptr, vertex);
	}

	private static void pushQuad(VertexWriter writer) {
		writer.offset += CLOUD_STRIDE * 4;
		writer.vertices += 4;
	}

	private static class ShortDistanceComp implements Comparator<Short> {
		@Override
		public int compare(Short o0, Short o1) {
			int cellX0 = (o0 & 0xFF) - MAX_CELL_DISTANCE;
			int cellZ0 = (o0 >>> 8) - MAX_CELL_DISTANCE;

			int cellX1 = (o1 & 0xFF) - MAX_CELL_DISTANCE;
			int cellZ1 = (o1 >>> 8) - MAX_CELL_DISTANCE;

			return Integer.compare(MathExt.square(cellX0) + MathExt.square(cellZ0), MathExt.square(cellX1) + MathExt.square(cellZ1));
		}
	}
}
