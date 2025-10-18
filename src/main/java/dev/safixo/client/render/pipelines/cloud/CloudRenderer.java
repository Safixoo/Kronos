package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.util.ColorBGRManager;
import dev.safixo.client.render.util.Direction;
import dev.safixo.client.render.util.MathExt;
import dev.safixo.client.render.util.memory.NativeBuffer;
import dev.safixo.client.render.vertex.VertexWriterManager;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
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

		VertexWriterManager.setCurrentInstance(VertexWriterManager.DEFAULT_INSTANCE);
		VertexWriterManager writer = VertexWriterManager.getCurrentInstance();

		writer.startDrawing();
		writer.setVertexFormat(DefaultVertexFormats.CLOUD_FORMAT);

		if (VERTEX_BUFFER == null) {
			VERTEX_BUFFER = new RenderBuffer(DefaultVertexFormats.CLOUD_FORMAT, CLOUD_STRIDE * 8192, GL15.GL_STATIC_DRAW);
		}

		if (CLOUD_SHADER == null) {
			processCloudTexture(manager);
			CLOUD_TICK_COUNTER = HookUtils.getField(Minecraft.getMinecraft().renderGlobal, "cloudTickCounter", "field_72773_u");

			CLOUD_SHADER = new CloudProgram();
		}

		CLOUD_SHADER.useProgram();
		CLOUD_SHADER.uploadUniforms(distance, r, g, b);
	}

	private static void clearState(VertexWriterManager writer) {
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

		int renderDistanceBlocks = SectionManager.getCurrentInstance().getCamera().renderDistance;
		int cellDistance = Math.min(33, ((renderDistanceBlocks * 16) / CLOUD_WIDTH) + 7);

		// Prepare for rendering the clouds.
		CloudRenderer.setupRender(world, mc.getResourceManager(), cellDistance * 12, partialTick);

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

		VertexWriterManager writer = VertexWriterManager.getCurrentInstance();
		RenderBuffer buffer = VERTEX_BUFFER;

		buffer.bindBuffer(true);

		// Build and write cloud geometry.
		buildGeometry(writer, cellDistance, worldFloorX, worldFloorZ, worldFracX, viewY, worldFracZ);

		int vertices = writer.getVertices();

		// Upload geometry.
		uploadGeometry(writer, vertices);

		buffer.bindBuffer(false);

		// Draw clouds.
		drawClouds(writer.getVertices(), viewY);

		// Clear the state.
		CloudRenderer.clearState(writer);
	}

	private static void uploadGeometry(VertexWriterManager writer, int vertices) {
		int dataSize = vertices * CLOUD_STRIDE;

		ByteBuffer vertexDataBuffer = NativeBuffer.wrap(writer.getVertexData());
		((Buffer) vertexDataBuffer).limit(dataSize);

		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertexDataBuffer);
	}

	private static void drawClouds(int vertices, float viewY) {
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

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

	private static void buildPXPZ(VertexWriterManager writer,
								  float cloudX, float cloudY, float cloudZ,
								  int color, int visibleMask
	) {
		// -X Face
		if ((visibleMask & (1 << XN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX, cloudY, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX, cloudY, cloudZ, usedColor);
		}

		// -Z Face
		if ((visibleMask & (1 << ZN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ, usedColor);
			addVertex(writer, cloudX, cloudY, cloudZ, usedColor);
		}
	}

	private static void buildNXNZ(VertexWriterManager writer,
								  float cloudX, float cloudY, float cloudZ,
								  int color, int visibleMask
	) {
		// +X Face
		if ((visibleMask & (1 << XP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX + 1, cloudY, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ + 1, usedColor);
		}

		// +Z Face
		if ((visibleMask & (1 << ZP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, cloudY, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
		}
	}

	private static void buildNXPZ(VertexWriterManager writer,
								  float cloudX, float cloudY, float cloudZ,
								  int color, int visibleMask
	) {
		// -X Face
		if ((visibleMask & (1 << XN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX, cloudY, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX, cloudY, cloudZ, usedColor);
		}

		// +Z Face
		if ((visibleMask & (1 << ZP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, cloudY, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
		}
	}

	private static void buildPXNZ(VertexWriterManager writer,
								  float cloudX, float cloudY, float cloudZ,
								  int color, int visibleMask
	) {
		// +X Face
		if ((visibleMask & (1 << XP)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR);
			addVertex(writer, cloudX + 1, cloudY, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ + 1, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ + 1, usedColor);
		}

		// -Z Face
		if ((visibleMask & (1 << ZN)) != 0) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ, usedColor);
			addVertex(writer, cloudX, cloudY, cloudZ, usedColor);
		}
	}

	private static void buildNPY(VertexWriterManager writer,
								 float cloudX, float cloudY, float cloudZ,
								 int color
	) {
		// +Y Face
		if (cloudY < CULL_Y) {
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ + 1, color);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ + 1, color);
			addVertex(writer, cloudX + 1, cloudY + CLOUD_HEIGHT, cloudZ, color);
			addVertex(writer, cloudX, cloudY + CLOUD_HEIGHT, cloudZ, color);
		}

		// -Y Face
		if (cloudY > -CULL_Y) {
			int usedColor = ColorBGRManager.multiplyColor(color, CLOUD_BOTTOM_FACTOR);
			addVertex(writer, cloudX, cloudY, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ, usedColor);
			addVertex(writer, cloudX + 1, cloudY, cloudZ + 1, usedColor);
			addVertex(writer, cloudX, cloudY, cloudZ + 1, usedColor);
		}
	}

	private static void buildGeometry(VertexWriterManager writer,
									  int cellDistance, int worldFloorX, int worldFloorZ,
									  float worldFracX, float viewY, float worldFracZ)
	{
		int maxDistance = (cellDistance * 3) >>> 1;
		writer.ensureCapacity(CLOUD_STRIDE * 8192);

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

				float cloudX = cellX - worldFracX;
				float cloudZ = cellZ - worldFracZ;
				buildNPY(writer, cloudX, viewY, cloudZ, color);
				buildPXPZ(writer, cloudX, viewY, cloudZ, color, visibleMask);
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

				float cloudX = cellX - worldFracX;
				float cloudZ = cellZ - worldFracZ;
				buildNPY(writer, cloudX, viewY, cloudZ, color);
				buildNXNZ(writer, cloudX, viewY, cloudZ, color, visibleMask);
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

				float cloudX = cellX - worldFracX;
				float cloudZ = cellZ - worldFracZ;
				buildNPY(writer, cloudX, viewY, cloudZ, color);
				buildNXPZ(writer, cloudX, viewY, cloudZ, color, visibleMask);
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

				float cloudX = cellX - worldFracX;
				float cloudZ = cellZ - worldFracZ;
				buildNPY(writer, cloudX, viewY, cloudZ, color);
				buildPXNZ(writer, cloudX, viewY, cloudZ, color, visibleMask);
			}
		}

		for (int cellX = -cellDistance; cellX <= cellDistance; cellX++) {
			int width = (cellX + worldFloorX) & 0xFF;
			int visibleMask = getCellBits(width, worldFloorZ & 0xFF);

			if (visibleMask == 0) {
				continue;
			}
			int color = getCloudColor(width, worldFloorZ & 0xFF);
			float cloudX = cellX - worldFracX;

			buildNPY(writer, cloudX, viewY, -worldFracZ, color);

			if (cellX < 0) {
				buildNXNZ(writer, cloudX, viewY, -worldFracZ, color, visibleMask);
				buildNXPZ(writer, cloudX, viewY, -worldFracZ, color, visibleMask);
			} else {
				buildPXNZ(writer, cloudX, viewY, -worldFracZ, color, visibleMask);
				buildPXPZ(writer, cloudX, viewY, -worldFracZ, color, visibleMask);
			}
		}

		for (int cellZ = -cellDistance; cellZ <= cellDistance; cellZ++) {
			int height = (cellZ + worldFloorZ) & 0xFF;
			int visibleMask = getCellBits(worldFloorX & 0xFF, height);

			if (visibleMask == 0) {
				continue;
			}
			int color = getCloudColor(worldFloorX & 0xFF, height);
			float cloudZ = cellZ - worldFracZ;

			if (cellZ != 0) {
				buildNPY(writer, -worldFracX, viewY, cloudZ, color);
			}

			if (cellZ < 0) {
				buildNXNZ(writer, -worldFracX, viewY, cloudZ, color, visibleMask);
				buildPXPZ(writer, -worldFracX, viewY, cloudZ, color, visibleMask);
			} else {
				buildPXPZ(writer, -worldFracX, viewY, cloudZ, color, visibleMask);
				buildNXPZ(writer, -worldFracX, viewY, cloudZ, color, visibleMask);
			}
		}

		for (int cellZ = -1; cellZ <= 1; cellZ++) {
			for (int cellX = -1; cellX <= 1; cellX++) {
				float cloudZ = (cellZ - worldFracZ);
				float cloudX = (cellX - worldFracX);

				if (Math.abs(cloudX + 0.5) + Math.abs(cloudZ + 0.5) > 1.0) {
					continue;
				}

				int height = (cellZ + worldFloorZ) & 0xFF;
				int width = (cellX + worldFloorX) & 0xFF;

				int visibleMask = getCellBits(width, height);
				int color = getCloudColor(width, height);

				if (color == 0) {
					continue;
				}

				buildNXNZ(writer, cloudX, viewY, cloudZ, color, ~visibleMask);
				buildPXPZ(writer, cloudX, viewY, cloudZ, color, ~visibleMask);
				buildPXPZ(writer, cloudX, viewY, cloudZ, color, ~visibleMask);
				buildNXPZ(writer, cloudX, viewY, cloudZ, color, ~visibleMask);
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

	private static void addVertex(VertexWriterManager writer, float x, float y, float z) {
		CloudFormat.writeCloudVertex(writer.getTotalOffset(), x, y, z, writer.color);
		writer.addVertexCounter(CLOUD_STRIDE);
	}

	private static void addVertex(VertexWriterManager writer, float x, float y, float z, int color) {
		CloudFormat.writeCloudVertex(writer.getTotalOffset(), x, y, z, color);
		writer.addVertexCounter(CLOUD_STRIDE);
	}

	private static void setColor(VertexWriterManager writer, int color) {
		writer.color = color;
	}
}
