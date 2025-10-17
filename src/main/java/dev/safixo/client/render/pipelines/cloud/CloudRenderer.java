package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.util.ColorBGRManager;
import dev.safixo.client.render.util.MathExt;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.writers.CloudFormat;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.core.HookUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.Resource;
import net.minecraft.client.resources.ResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.MemoryUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class CloudRenderer {
	private static final int CLOUD_STRIDE = DefaultVertexFormats.CLOUD_FORMAT.getStride();
	private static final boolean DEBUG_WIREFRAME = false;

	private static final ResourceLocation CLOUD_TEXTURE = new ResourceLocation("textures/environment/clouds.png");
	private static final float CLOUD_WIDTH = 12.0F;
	private static final float CLOUD_HEIGHT = 4.0F;

	private static final int CLOUD_BOTTOM_FACTOR = ColorBGRManager.normToFactor(0.7F);
	private static final int CLOUD_X_FACTOR = ColorBGRManager.normToFactor(0.9F);
	private static final int CLOUD_Z_FACTOR = ColorBGRManager.normToFactor(0.8F);

	private static RenderBuffer VERTEX_BUFFER;
	private static CloudProgram CLOUD_SHADER;

	private static final float EPSILON = 1.0F / 1024.0F;

	static void setupRender(ResourceManager manager) {
		if (VERTEX_BUFFER == null) {
			VERTEX_BUFFER = new RenderBuffer(DefaultVertexFormats.CLOUD_FORMAT, 1024 * 4096, GL15.GL_STREAM_DRAW);
		}

		if (CLOUD_SHADER == null) {
			processCloudTexture(manager);
			CLOUD_SHADER = new CloudProgram();
		}

		CLOUD_SHADER.useProgram();
		CLOUD_SHADER.uploadUniforms();
	}

	static void clearState() {
		CLOUD_SHADER.disableProgram();
	}

	public static void renderCloudsFancy(float partialTick) {
		Minecraft mc = Minecraft.getMinecraft();
		WorldClient theWorld = mc.theWorld;
		TextureManager textureManager = mc.getTextureManager();

		VertexWriterManager.setCurrentInstance(VertexWriterManager.DEFAULT_INSTANCE);
		VertexWriterManager writer = VertexWriterManager.getCurrentInstance();

		writer.startDrawing();
		writer.setVertexFormat(DefaultVertexFormats.CLOUD_FORMAT);

		CloudRenderer.setupRender(mc.getResourceManager());

		float playerX = (float) (mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * partialTick);
		float playerY = (float) (mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * partialTick);
		float playerZ = (float) (mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * partialTick);

		int cloudTickCounter = (Integer) HookUtils.getFieldObj(mc.renderGlobal, "cloudTickCounter", "");
		double tickPosition = cloudTickCounter + partialTick;

		float viewY = theWorld.provider.getCloudHeight() - playerY + 0.33F;

		double worldX = (playerX + tickPosition * 0.03F) / CLOUD_WIDTH;
		double worldZ = (playerZ + 3.96F) / CLOUD_WIDTH;

		int regionX = MathExt.floor(worldX / 2048.0F);
		int regionZ = MathExt.floor(worldZ / 2048.0F);

		worldX -= regionX * 2048;
		worldZ -= regionZ * 2048;

		textureManager.bindTexture(CLOUD_TEXTURE);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		Vec3 cloudColor = theWorld.getCloudColour(partialTick);
		float r = (float) cloudColor.xCoord;
		float g = (float) cloudColor.yCoord;
		float b = (float) cloudColor.zCoord;
		int color = ColorBGRManager.packColor(r, g, b);

		int worldFloorX = MathExt.floor(worldX);
		int worldFloorZ = MathExt.floor(worldZ);

		float worldFracX = (float) (worldX - worldFloorX);
		float worldFracZ = (float) (worldZ - worldFloorZ);

		GL11.glScalef(CLOUD_WIDTH, 1, CLOUD_WIDTH);

		// * Details about vanilla implementation:
		// - Each 1 in the position is one cell or texel of the cloud (12.0 block large cloud cell).
		// - Each iteration does a top and bottom 8x8 cell singular quad, but for
		//   z, x-axis it does one quad per 8x1 or 1x8 cells (which is the reason for the for-loop).
		// - It uses a depth pre-pass to avoid translucency problems, but it doesn't
		//   cache first pass geometry.
		// - Uses various ifs to check if the octCell is back-facing or above or below
		//   cloud height
		// - It seems that to avoid translucency problems is rendered in a different part
		//   of EntityRenderer.renderWorld depending on the position of the player to the cloud
		//   height, it can be tested by putting blocks behind clouds and moving and up and down.
		for (int octCellZ = -4; octCellZ <= 4; octCellZ++) {
			for (int octCellX = -4; octCellX <= 4; octCellX++) {
				float cellX = (octCellX * 8) + (1.0f / 1024.0f);
				float cellZ = (octCellZ * 8) + (1.0f / 1024.0f);

				// Final coordinates for each cloud cell.
				final float cloudX = cellX - worldFracX;
				final float cloudY = viewY;
				final float cloudZ = cellZ - worldFracZ;

				writer.ensureCapacity(CLOUD_STRIDE * 8);

				if (cloudY > -5.0f) {
					setColor(writer, ColorBGRManager.multiplyColor(color, CLOUD_BOTTOM_FACTOR));
					addVertex(writer, cloudX + 0, cloudY + 0, cloudZ + 8, cellX + 0 + worldFloorX, cellZ + 8 + worldFloorZ);
					addVertex(writer, cloudX + 8, cloudY + 0, cloudZ + 8, cellX + 8 + worldFloorX, cellZ + 8 + worldFloorZ);
					addVertex(writer, cloudX + 8, cloudY + 0, cloudZ + 0, cellX + 8 + worldFloorX, cellZ + 0 + worldFloorZ);
					addVertex(writer, cloudX + 0, cloudY + 0, cloudZ + 0, cellX + 0 + worldFloorX, cellZ + 0 + worldFloorZ);
				}

				if (cloudY < 5.0f) {
					setColor(writer, color);
					addVertex(writer, cloudX + 0, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 8, cellX + 0 + worldFloorX, cellZ + 8 + worldFloorZ);
					addVertex(writer, cloudX + 8, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 8, cellX + 8 + worldFloorX, cellZ + 8 + worldFloorZ);
					addVertex(writer, cloudX + 8, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 0, cellX + 8 + worldFloorX, cellZ + 0 + worldFloorZ);
					addVertex(writer, cloudX + 0, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 0, cellX + 0 + worldFloorX, cellZ + 0 + worldFloorZ);
				}

				writer.ensureCapacity(CLOUD_STRIDE * 4 * 16);

				setColor(writer, ColorBGRManager.multiplyColor(color, CLOUD_X_FACTOR));
				if (octCellX >= -1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(writer, cloudX + quad + 0, cloudY + 0, cloudZ + 8, (cellX + quad) + worldFloorX, cellZ + 8 + worldFloorZ);
						addVertex(writer, cloudX + quad + 0, cloudY + CLOUD_HEIGHT, cloudZ + 8, cellX + quad + worldFloorX, cellZ + 8 + worldFloorZ);
						addVertex(writer, cloudX + quad + 0, cloudY + CLOUD_HEIGHT, cloudZ + 0, cellX + quad + worldFloorX, cellZ + 0 + worldFloorZ);
						addVertex(writer, cloudX + quad + 0, cloudY + 0, cloudZ + 0, cellX + quad + worldFloorX, cellZ + 0 + worldFloorZ);
					}
				}

				if (octCellX <= 1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(writer, cloudX + quad + 1 - EPSILON, cloudY + 0, cloudZ + 8, cellX + quad + worldFloorX, cellZ + 8 + worldFloorZ);
						addVertex(writer, cloudX + quad + 1 - EPSILON, cloudY + CLOUD_HEIGHT, cloudZ + 8, cellX + quad + worldFloorX, cellZ + 8 + worldFloorZ);
						addVertex(writer, cloudX + quad + 1 - EPSILON, cloudY + CLOUD_HEIGHT, cloudZ + 0, cellX + quad + worldFloorX, cellZ + 0 + worldFloorZ);
						addVertex(writer, cloudX + quad + 1 - EPSILON, cloudY + 0, cloudZ + 0, cellX + quad + worldFloorX, cellZ + 0 + worldFloorZ);
					}
				}

				writer.ensureCapacity(CLOUD_STRIDE * 4 * 16);

				setColor(writer, ColorBGRManager.multiplyColor(color, CLOUD_Z_FACTOR));
				if (octCellZ >= -1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(writer, cloudX + 0, cloudY + CLOUD_HEIGHT, cloudZ + quad + 0, cellX + 0 + worldFloorX, cellZ + quad + worldFloorZ);
						addVertex(writer, cloudX + 8, cloudY + CLOUD_HEIGHT, cloudZ + quad + 0, cellX + 8 + worldFloorX, cellZ + quad + worldFloorZ);
						addVertex(writer, cloudX + 8, cloudY + 0, cloudZ + quad + 0, cellX + 8 + worldFloorX, cellZ + quad + worldFloorZ);
						addVertex(writer, cloudX + 0, cloudY + 0, cloudZ + quad + 0, cellX + 0 + worldFloorX, cellZ + quad + worldFloorZ);
					}
				}

				if (octCellZ <= 1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(writer, cloudX + 0, cloudY + CLOUD_HEIGHT, cloudZ + quad + 1 - EPSILON, cellX + 0 + worldFloorX, cellZ + quad + worldFloorZ);
						addVertex(writer, cloudX + 8, cloudY + CLOUD_HEIGHT, cloudZ + quad + 1 - EPSILON, cellX + 8 + worldFloorX, cellZ + quad + worldFloorZ);
						addVertex(writer, cloudX + 8, cloudY + 0, cloudZ + quad + 1 - EPSILON, cellX + 8 + worldFloorX, cellZ + quad + worldFloorZ);
						addVertex(writer, cloudX + 0, cloudY + 0, cloudZ + quad + 1 - EPSILON, cellX + 0 + worldFloorX, cellZ + quad + worldFloorZ);
					}
				}
			}
		}

		if (DEBUG_WIREFRAME) {
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		}

		RenderBuffer buffer = VERTEX_BUFFER;
		int vertices = writer.getVertices();
		int dataSize = vertices * CLOUD_STRIDE;

		buffer.upload(writer.getVertexData(), 0, dataSize);
		buffer.bindState(true);

		GlVertexBuffer vertexBuffer = buffer.getVertexBuffer();

		GL11.glColorMask(false, false, false, false);
		vertexBuffer.draw(vertices, 0);
		GL11.glColorMask(true, true, true, true);
		vertexBuffer.draw(vertices, 0);

		buffer.bindState(false);

		writer.stopDrawing();
		CloudRenderer.clearState();

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);

		if (DEBUG_WIREFRAME) {
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
		}
	}

	private static int getVisibleFaces(int x, int y) {
		return 0;
	}

	private static final byte[][] VISIBLE_FACES = new byte[256][256];

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


	}

	private static void addVertex(VertexWriterManager writer, float x, float y, float z, float u, float v) {
		CloudFormat.writeCloudVertex(writer.getTotalOffset(), x, y, z, u, v, writer.color);
		writer.addVertexCounter(CLOUD_STRIDE);
	}

	private static void setColor(VertexWriterManager writer, int color) {
		writer.color = color;
	}

	private static void setNormal(float x, float y, float z) {

	}
}
