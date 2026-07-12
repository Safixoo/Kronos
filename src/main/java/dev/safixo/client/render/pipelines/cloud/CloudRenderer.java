package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.state.GlFogTracker;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.core.HookUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.resources.Resource;
import net.minecraft.client.resources.ResourceManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
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
	public static final int CLOUD_HEIGHT = 4;

	private static final int CLOUD_BOTTOM_FACTOR = ColorBGRManager.normToFactor(0.7F);
	private static final int CLOUD_X_FACTOR = ColorBGRManager.normToFactor(0.9F);
	private static final int CLOUD_Z_FACTOR = ColorBGRManager.normToFactor(0.8F);

	private static final int[] CLOUD_TEX_COLOR = new int[256 * 256];

	private static GlVertexArrayObject VERTEX_ARRAY;
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

	// Save a sorted array of indices that we can iterate to generate the perfectly sorted clouds, also ordered
	// with distance so the fog distance check simplifies to simply stop the iteration after some index.
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

			int distance = Math.min(MAX_CELL_DISTANCE, (int) (0.25D + Math.sqrt(MathExt.square(cellX) + MathExt.square(cellZ))));
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

		if (VERTEX_ARRAY == null) {
			VERTEX_ARRAY = new GlVertexArrayObject(DefaultVertexFormats.CLOUD_FORMAT);
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
		return (Integer) HookUtils.getFieldValue(CLOUD_TICK_COUNTER, renderGlobal);
	}

	public static void renderCloudsFancy(float partialTick) {
		Minecraft mc = Minecraft.getMinecraft();
		WorldClient world = mc.theWorld;

		int cameraBlockId = ActiveRenderInfo.getBlockIdAtEntityViewpoint(mc.theWorld, mc.renderViewEntity, partialTick);

		// Don't render clouds underwater.
		if (cameraBlockId != 0 && Block.blocksList[cameraBlockId].blockMaterial.isLiquid()) {
			return;
		}

		Vec3 cameraPosition = ActiveRenderInfo.projectViewFromEntity(mc.renderViewEntity, partialTick);

		int renderDistance = MathExt.getCanonicalRenderDistance(mc.gameSettings);
		int cellDistance = Math.max((int) (GlFogTracker.FOG_END / CLOUD_WIDTH), (renderDistance * 16) / CLOUD_WIDTH) - 1;
		cellDistance = Math.min(cellDistance + 3, MAX_CELL_DISTANCE);

		// Prepare for rendering the clouds.
		CloudRenderer.setupRender(world, mc.getResourceManager(), partialTick, cellDistance);

		EntityLivingBase player = mc.renderViewEntity;
		float playerX = (float) MathExt.lerp(player.lastTickPosX, player.posX, partialTick);
		float playerY = (float) MathExt.lerp(player.lastTickPosY, player.posY, partialTick);
		float playerZ = (float) MathExt.lerp(player.lastTickPosZ, player.posZ, partialTick);

		float cloudHeight = world.provider.getCloudHeight();

		float cloudY = cloudHeight - playerY + 0.33F;
		float cloudCameraY = cloudHeight - (float) cameraPosition.yCoord + 0.33F;

		int maxDistance = (int) (MathExt.square(cellDistance) - MathExt.square(cloudY / CLOUD_WIDTH));
		cellDistance = (int) Math.sqrt(maxDistance);

		int cloudTickCounter = getCloudTickCounter(mc.renderGlobal);
		double tickPosition = cloudTickCounter + partialTick;

		double cloudX = MathExt.floorMod((playerX + tickPosition * 0.03F) / CLOUD_WIDTH, 2048);
		double cloudZ = MathExt.floorMod(((playerZ + 3.96F) / CLOUD_WIDTH), 2048);

		int cloudIntX = MathExt.floor(cloudX); float cloudFracX = (float) (cloudX - cloudIntX);
		int cloudIntZ = MathExt.floor(cloudZ); float cloudFracZ = (float) (cloudZ - cloudIntZ);

		VertexWriter writer = VertexWriter.getCurrentInstance();
		FrustumCuller.prepareCloudFrustum(cloudFracX, cloudY, cloudFracZ);

		int insideIndex = getInsideIndex(cloudCameraY);

		double cloudCamX = MathExt.floorMod((cameraPosition.xCoord + tickPosition * 0.03F) / CLOUD_WIDTH, 2048);
		double cloudCamZ = MathExt.floorMod(((cameraPosition.zCoord + 3.96F) / CLOUD_WIDTH), 2048);

		int cloudCamIntX = MathExt.floor(cloudCamX); float cloudCamFracX = (float) (cloudCamX - cloudCamIntX);
		int cloudCamIntZ = MathExt.floor(cloudCamZ); float cloudCamFracZ = (float) (cloudCamZ - cloudCamIntZ);

		// Generate and write the clouds' geometry.
		buildGeometry(writer, cellDistance, cloudCamFracX, cloudCamFracZ, cloudIntX, cloudIntZ, cloudCamIntX, cloudCamIntZ, cloudCameraY, insideIndex);

		// Upload the geometry.
		ImprovedTessellator.INSTANCE.getVertexBuffer().bufferData(writer.getWriterNio(), writer.getOffset());

		// Draw the clouds.
		drawClouds(writer.getVertices(), cloudFracX, cloudY, cloudFracZ);

		// Clear the state.
		clearState(writer);
	}

	private static int getInsideIndex(float cloudCameraY) {
		boolean insideClouds = (-cloudCameraY >= -EPSILON) && (-cloudCameraY <= CLOUD_HEIGHT + EPSILON);

		if (!insideClouds) {
			return -1;
		}

		return MAX_DISTANCE_INDEX[1];
	}

	private static void drawClouds(int vertices, float worldFracX, float viewY, float worldFracZ) {
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		CLOUD_SHADER.setOffset(-worldFracX - MAX_CELL_DISTANCE, viewY, -worldFracZ - MAX_CELL_DISTANCE);
		VERTEX_ARRAY.bind(ImprovedTessellator.INSTANCE.getVertexBuffer());

		GlVertexBuffer vertexBuffer = ImprovedTessellator.INSTANCE.getVertexBuffer();
		vertexBuffer.draw(vertices, 0);

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
	}

	private static final float EPSILON = 2E-1F;

	// Meshes a colored voxel for each cell/texel of the cloud texture, in contrast Vanilla generates a giant
	// tessellated mesh that covers all the texture in a way that only knowing the texture size it can
	// generate a 3D model of the texture without having info of the texture data (just like item rendering).
	private static void buildGeometry(VertexWriter writer, int cellDistance,
									  float fracX, float fracZ, int cloudX, int cloudZ,
									  int cloudCamX, int cloudCamZ, float viewY, int insideCellIndex) {
		writer.ensureCapacity(CLOUD_STRIDE * 32768);

		int maxIteration = MAX_DISTANCE_INDEX[cellDistance];

		int startX = (fracX < 0.5f ? 0 : 1) + MAX_CELL_DISTANCE;
		int startZ = (fracZ < 0.5f ? 0 : 1) + MAX_CELL_DISTANCE;

		int cloudDiffX = MathExt.floorDiv(-cloudX + cloudCamX, CLOUD_WIDTH);
		int cloudDiffZ = MathExt.floorDiv(-cloudZ + cloudCamZ, CLOUD_WIDTH);

		cloudX -= MAX_CELL_DISTANCE;
		cloudZ -= MAX_CELL_DISTANCE;

		long ptr = writer.getWriterPtr();

		for (int i = 0; i <= maxIteration; i++) {
			int cellData = CELL_XY_DATA[i];

			int cellX = cellData >>> 8;
			int cellZ = cellData & 0xFF;

			int texU = (cellX + cloudX) & 0xFF;
			int texV = (cellZ + cloudZ) & 0xFF;

			int color = getCloudColor(texU, texV);
			long cellVertex = formatPosition(cellX, 0, cellZ);

			cellX -= cloudDiffX;
			cellZ -= cloudDiffZ;

			// If the cloud texture is empty or is outside the frustum bounds, skip cell.
			if (color == EMPTY_CLOUD || !FrustumCuller.cloudWithinFrustumBounds(cellX, cellZ)) {
				continue;
			}

			int visibleMask = color >>> 24;

			if (cellX < startX) { // back-face cull check
				if ((visibleMask & (1 << XP)) != 0) {
					long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_X_FACTOR));
					// +X Face
					writeCloudVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
					writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
					writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
					writeCloudVertex(ptr, vertex + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
				}
			} else if ((visibleMask & (1 << XN)) != 0) {
				long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_X_FACTOR));
				// -X Face
				writeCloudVertex(ptr, vertex + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;
			}

			if (cellZ < startZ) { // back-face cull check
				if ((visibleMask & (1 << ZP)) != 0) {
					long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_Z_FACTOR));
					// +Z Face
					writeCloudVertex(ptr, vertex + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;
					writeCloudVertex(ptr, vertex + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
					writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
					writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
				}
			} else if ((visibleMask & (1 << ZN)) != 0) {
				long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_Z_FACTOR));
				// -Z Face
				writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;
			}

			// Treat as we are either over or below the clouds.
			if (viewY < -1) {
				long vertex = cellVertex | formatColor(color);
				writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
			} else {
				long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_BOTTOM_FACTOR));
				writeCloudVertex(ptr, vertex); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
				writeCloudVertex(ptr, vertex + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;
			}

			// If were inside a cloud, build its interior geometry.
			if (i <= insideCellIndex) {
				ptr += buildCenterCells(ptr, cloudX, cloudZ, viewY, i);
			}
		}

		writer.offset = (int) (ptr - writer.getWriterPtr());
		writer.vertices = writer.offset / CLOUD_STRIDE;
	}

	// Generates all inside faces in a way that they don't get discarded by gl back-face culling, also some extra bottom
	// and top faces for the inside cells.
	private static long buildCenterCells(long ptr, int worldFloorX, int worldFloorZ, float viewY, int index) {
		int cellData = CELL_XY_DATA[index];

		int cellX = cellData >>> 8;
		int cellZ = cellData & 0xFF;

		int width = (cellX + worldFloorX) & 0xFF;
		int height = (cellZ + worldFloorZ) & 0xFF;
		int color = getCloudColor(width, height);

		if (color == EMPTY_CLOUD) {
			return 0L;
		}

		long cellVertex = formatPosition(cellX, 0, cellZ);

		// +Y Face.
		long vertexYP = cellVertex | formatColor(color);
		writeCloudVertex(ptr, vertexYP + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexYP + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexYP + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexYP + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		// -Y Face.
		long vertexYN = cellVertex | formatColor(multiplyColor(color, CLOUD_BOTTOM_FACTOR));
		writeCloudVertex(ptr, vertexYN + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexYN + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexYN + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexYN + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;

		long vertexZ = cellVertex | formatColor(multiplyColor(color, CLOUD_Z_FACTOR));
		// -Z Face
		writeCloudVertex(ptr, vertexZ + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexZ + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexZ + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexZ + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		// +Z Face
		writeCloudVertex(ptr, vertexZ + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexZ + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexZ + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexZ + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;

		long vertexX = cellVertex | formatColor(multiplyColor(color, CLOUD_X_FACTOR));
		// +X Face
		writeCloudVertex(ptr, vertexX + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexX + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexX + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexX + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
		// -X Face
		writeCloudVertex(ptr, vertexX + formatPosition(0, 0, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexX + formatPosition(0, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexX + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
		writeCloudVertex(ptr, vertexX + formatPosition(0, 0, 1)); ptr += CLOUD_STRIDE;

		if (!(viewY < -1)) {
			long vertex = cellVertex | formatColor(color);
			writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
			writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 1)); ptr += CLOUD_STRIDE;
			writeCloudVertex(ptr, vertex + formatPosition(1, CLOUD_HEIGHT, 0)); ptr += CLOUD_STRIDE;
			writeCloudVertex(ptr, vertex + formatPosition(0, CLOUD_HEIGHT, 0));
		} else {
			long vertex = cellVertex | formatColor(multiplyColor(color, CLOUD_BOTTOM_FACTOR));
			writeCloudVertex(ptr, vertex); ptr += CLOUD_STRIDE;
			writeCloudVertex(ptr, vertex + formatPosition(1, 0, 0)); ptr += CLOUD_STRIDE;
			writeCloudVertex(ptr, vertex + formatPosition(1, 0, 1)); ptr += CLOUD_STRIDE;
			writeCloudVertex(ptr, vertex + formatPosition(0, 0, 1));
		}

		return (CLOUD_STRIDE * 4L) * 7L;
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

	private static class ShortDistanceComp implements Comparator<Short> {
		@Override
		public int compare(Short o0, Short o1) {
			int cellX0 = (o0 & 0xFF) - MAX_CELL_DISTANCE;
			int cellZ0 = (o0 >>> 8) - MAX_CELL_DISTANCE;

			int cellX1 = (o1 & 0xFF) - MAX_CELL_DISTANCE;
			int cellZ1 = (o1 >>> 8) - MAX_CELL_DISTANCE;

			return Integer.signum((MathExt.square(cellX0) + MathExt.square(cellZ0)) - (MathExt.square(cellX1) + MathExt.square(cellZ1)));
		}
	}
}
