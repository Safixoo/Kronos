package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.util.ColorBGRManager;
import dev.safixo.client.render.util.MathExt;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.core.HookUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

public class CloudRenderer {
	private static final boolean DEBUG_WIREFRAME = false;

	private static final ResourceLocation CLOUD_TEXTURE = new ResourceLocation("textures/environment/clouds.png");
	private static final float CLOUD_ALPHA = 0.8F;
	private static final float CLOUD_WIDTH = 12.0F;
	private static final float CLOUD_HEIGHT = 4.0F;

	private static RenderBuffer VERTEX_BUFFER;
	private static CloudProgram CLOUD_SHADER;

	private static final float EPSILON = 1.0F / 1024.0F;

	static void setupRender() {
		if (VERTEX_BUFFER == null) {
			VERTEX_BUFFER = new RenderBuffer(DefaultVertexFormats.CLOUD_FORMAT, 1024 * 8192, GL15.GL_DYNAMIC_DRAW);
		}

		if (CLOUD_SHADER == null) {
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

		CloudRenderer.setupRender();

		float playerX = (float) (mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * partialTick);
		float playerY = (float) (mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * partialTick);
		float playerZ = (float) (mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * partialTick);

		int cloudTickCounter = (Integer) HookUtils.getFieldObj(mc.renderGlobal, "cloudTickCounter", "");
		double tickPosition = cloudTickCounter + partialTick;

		float viewY = theWorld.provider.getCloudHeight() - playerY + 0.33F;

		double worldX = (playerX + tickPosition * 0.03F) / CLOUD_WIDTH;
		double worldZ = (playerZ) / CLOUD_WIDTH + 0.33F;

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

		float f8 = MathExt.floor(worldX);
		float f9 = MathExt.floor(worldZ);

		float cloudFractX = (float) (worldX - MathExt.floor(worldX));
		float cloudFractZ = (float) (worldZ - MathExt.floor(worldZ));

		GL11.glScalef(CLOUD_WIDTH, 1, CLOUD_WIDTH);

		// Each cell is a texel of the cloud texture, each iteration passes 8 cells
		// from the cloud.
		for (int octCellZ = -4; octCellZ <= 4; octCellZ++) {
			for (int octCellX = -4; octCellX <= 4; octCellX++) {
				float cellX = (octCellX * 8);
				float cellZ = (octCellZ * 8);

				// Final coordinates for each cloud cell.
				final float cloudX = cellX - cloudFractX;
				final float cloudY = viewY;
				final float cloudZ = cellZ - cloudFractZ;

				if (cloudY > -5.0f) {
					setColor(r * 0.7F, g * 0.7F, b * 0.7F, CLOUD_ALPHA);
					addVertex(cloudX + 0, cloudY + 0, cloudZ + 8, (cellX + 0) + f8, (cellZ + 8) + f9);
					addVertex(cloudX + 8, cloudY + 0, cloudZ + 8, (cellX + 8) + f8, (cellZ + 8) + f9);
					addVertex(cloudX + 8, cloudY + 0, cloudZ + 0, (cellX + 8) + f8, (cellZ + 0) + f9);
					addVertex(cloudX + 0, cloudY + 0, cloudZ + 0, (cellX + 0) + f8, (cellZ + 0) + f9);
				}

				if (cloudY < 5.0f) {
					setColor(r, g, b, CLOUD_ALPHA);
					addVertex(cloudX + 0, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 8, (cellX + 0) + f8, (cellZ + 8) + f9);
					addVertex(cloudX + 8, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 8, (cellX + 8) + f8, (cellZ + 8) + f9);
					addVertex(cloudX + 8, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 0, (cellX + 8) + f8, (cellZ + 0) + f9);
					addVertex(cloudX + 0, cloudY + CLOUD_HEIGHT - EPSILON, cloudZ + 0, (cellX + 0) + f8, (cellZ + 0) + f9);
				}

				setColor(r * 0.9F, g * 0.9F, b * 0.9F, CLOUD_ALPHA);
				if (octCellX > -1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(cloudX + quad + 0, cloudY + 0, cloudZ + 8, (cellX + quad + 0.5F)  + f8, (cellZ + 8) + f9);
						addVertex(cloudX + quad + 0, cloudY + CLOUD_HEIGHT, cloudZ + 8, (cellX + quad + 0.5F)  + f8, (cellZ + 8) + f9);
						addVertex(cloudX + quad + 0, cloudY + CLOUD_HEIGHT, cloudZ + 0, (cellX + quad + 0.5F)  + f8, (cellZ + 0) + f9);
						addVertex(cloudX + quad + 0, cloudY + 0, cloudZ + 0, (cellX + quad + 0.5F)  + f8, (cellZ + 0) + f9);
					}
				}

				if (octCellX < 1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(cloudX + quad + 1 - EPSILON, cloudY + 0, cloudZ + 8, (cellX + quad + 0.5F) + f8, (cellZ + 8) + f9);
						addVertex(cloudX + quad + 1 - EPSILON, cloudY + CLOUD_HEIGHT, cloudZ + 8, (cellX + quad + 0.5F) + f8, (cellZ + 8) + f9);
						addVertex(cloudX + quad + 1 - EPSILON, cloudY + CLOUD_HEIGHT, cloudZ + 0, (cellX + quad + 0.5F) + f8, (cellZ + 0) + f9);
						addVertex(cloudX + quad + 1 - EPSILON, cloudY + 0, cloudZ + 0, (cellX + quad + 0.5F) + f8, (cellZ + 0) + f9);
					}
				}

				setColor(r * 0.8F, g * 0.8F, b * 0.8F, CLOUD_ALPHA);
				if (octCellZ > -1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(cloudX + 0, cloudY + CLOUD_HEIGHT, cloudZ + quad + 0, (cellX + 0) + f8, (cellZ + quad + 0.5F) + f9);
						addVertex(cloudX + 8, cloudY + CLOUD_HEIGHT, cloudZ + quad + 0, (cellX + 8) + f8, (cellZ + quad + 0.5F) + f9);
						addVertex(cloudX + 8, cloudY + 0, cloudZ + quad + 0, (cellX + 8) + f8, (cellZ + quad + 0.5F) + f9);
						addVertex(cloudX + 0, cloudY + 0, cloudZ + quad + 0, (cellX + 0) + f8, (cellZ + quad + 0.5F) + f9);
					}
				}

				if (octCellZ < 1) {
					for (int quad = 0; quad < 8; quad++) {
						addVertex(cloudX + 0, cloudY + CLOUD_HEIGHT, cloudZ + quad + 1 - EPSILON, (cellX + 0) + f8, (cellZ + quad + 0.5F) + f9);
						addVertex(cloudX + 8, cloudY + CLOUD_HEIGHT, cloudZ + quad + 1 - EPSILON, (cellX + 8) + f8, (cellZ + quad + 0.5F) + f9);
						addVertex(cloudX + 8, cloudY + 0, cloudZ + quad + 1 - EPSILON, (cellX + 8) + f8, (cellZ + quad + 0.5F) + f9);
						addVertex(cloudX + 0, cloudY + 0, cloudZ + quad + 1 - EPSILON, (cellX + 0) + f8, (cellZ + quad + 0.5F) + f9);
					}
				}
			}
		}

		if (DEBUG_WIREFRAME) {
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		}

		RenderBuffer buffer = VERTEX_BUFFER;
		int vertices = writer.getVertices();
		int dataSize = vertices * DefaultVertexFormats.CLOUD_FORMAT.getStride();

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

	private static void addVertex(float x, float y, float z, float u, float v) {
		VertexWriterManager writerManager = VertexWriterManager.getCurrentInstance();

		writerManager.x = x;
		writerManager.y = y;
		writerManager.z = z;

		writerManager.u = u;
		writerManager.v = v;

		writerManager.addVertex();
	}

	private static void setColor(float r, float g, float b, float a) {
		VertexWriterManager writerManager = VertexWriterManager.getCurrentInstance();
		writerManager.color = ColorBGRManager.packColor(r, g, b);
	}

	private static void setNormal(float x, float y, float z) {

	}
}
