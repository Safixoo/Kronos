package dev.safixo.client.render;

import dev.safixo.client.render.util.MathExt;
import dev.safixo.core.HookUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class CloudRenderer {
	private static final ResourceLocation CLOUDS_TEXTURE = new ResourceLocation("textures/environment/clouds.png");

	private static final float TEX_SIZE = 256.0f;
	private static final float INV_TEX_SIZE = 1.0f / TEX_SIZE;

	private static final float CLOUDS_ALPHA = 0.8f;

	public void renderCloudsFancy(float partialTick) {
		Minecraft mc = Minecraft.getMinecraft();
		RenderGlobal global = mc.renderGlobal;
		TextureManager renderEngine = mc.getTextureManager();
		World world = mc.theWorld;
		EntityClientPlayerMP player = mc.thePlayer;

		float playerX = (float) (player.prevPosX + (player.posX - player.prevPosX) * partialTick);
		float playerY = (float) (player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTick);
		float playerZ = (float) (player.prevPosZ + (player.posZ - player.prevPosZ) * partialTick);

		int cloudTickCounter = (Integer) HookUtils.getFieldObj(global, "cloudTickCounter", "field_72773_u");

		float tickMovement = (cloudTickCounter + partialTick) * 0.03F;
		float f2 = 12.0F;

		float cloudX = (playerX + tickMovement) / f2;
		float cloudZ = (playerZ + 3.96f) / f2;
		float cloudY = world.provider.getCloudHeight() - playerY + 0.33F;

		int i = MathExt.floor(cloudX / 2048);
		int j = MathExt.floor(cloudZ / 2048);

		cloudX -= i * 2048;
		cloudZ -= j * 2048;
		renderEngine.bindTexture(CLOUDS_TEXTURE);

		GL11.glScalef(f2, 1.0F, f2);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		Vec3 rgb = world.getCloudColour(partialTick);
		float r = (float) rgb.xCoord;
		float g = (float) rgb.yCoord;
		float b = (float) rgb.zCoord;

		float floorU = MathExt.floor(cloudX) * INV_TEX_SIZE;
		float floorV = MathExt.floor(cloudZ) * INV_TEX_SIZE;

		float fractU = cloudX - MathExt.floor(cloudX);
		float fractV = cloudZ - MathExt.floor(cloudZ);

		float f13 = 1.0f / 1024.0f;


		Tessellator tessellator = Tessellator.instance;

		for (int pass = 0; pass < 2; ++pass) {
			if (pass == 0) {
				GL11.glColorMask(false, false, false, false);
			} else {
				GL11.glColorMask(true, true, true, true);
			}

			for (int cellX = -3; cellX <= 4; cellX++) {
				for (int cellZ = -3; cellZ <= 4; cellZ++) {
					tessellator.startDrawingQuads();

					float f14 = cellX * 8.0f;
					float f15 = cellZ * 8.0f;

					float f16 = f14 - fractU;
					float f17 = f15 - fractV;

					if (cloudY > -5.0f) {
						tessellator.setColorRGBA_F(r * 0.7F, g * 0.7F, b * 0.7F, CLOUDS_ALPHA);
						tessellator.setNormal(8, -1.0F, 8);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 8, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 8, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 8, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 8, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
					}

					if (cloudY <= 5.0f) {
						tessellator.setColorRGBA_F(r, g, b, CLOUDS_ALPHA);
						tessellator.setNormal(8, 1.0F, 8);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 4 - f13, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 4 - f13, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 4 - f13, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						tessellator.addVertexWithUV(f16 + 8, cloudY + 4 - f13, f17 + 8, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
					}

					tessellator.setColorRGBA_F(r * 0.9F, g * 0.9F, b * 0.9F, CLOUDS_ALPHA);
					if (cellX > -1) {
						tessellator.setNormal(-1.0F, 8, 8);

						for (int quad = 0; quad < 8; quad++) {
							tessellator.addVertexWithUV(f16 + quad + 0, cloudY + 0, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + quad + 0, cloudY + 4, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + quad + 0, cloudY + 4, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + quad + 0, cloudY + 0, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						}
					}

					if (cellX <= 1) {
						tessellator.setNormal(1, 8, 8);

						for (int quad = 0; quad < 8; quad++) {
							tessellator.addVertexWithUV(f16 + quad + 1 - f13, cloudY + 8, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + quad + 1 - f13, cloudY + 4, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + quad + 1 - f13, cloudY + 4, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + quad + 1 - f13, cloudY + 8, f17 + 8, (f14 + quad + 0.5F) * INV_TEX_SIZE + floorU, (f15 + 8) * INV_TEX_SIZE + floorV);
						}
					}

					tessellator.setColorRGBA_F(r * 0.8F, g * 0.8F, b * 0.8F, CLOUDS_ALPHA);
					if (cellZ > -1) {
						tessellator.setNormal(8, 8, -1.0F);

						for (int quad = 0; quad < 8; quad++) {
							tessellator.addVertexWithUV(f16 + 0, cloudY + 4, f17 + quad + 0, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + 8, cloudY + 4, f17 + quad + 0, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + 8, cloudY + 0, f17 + quad + 0, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + 0, cloudY + 0, f17 + quad + 0, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
						}
					}

					if (cellZ <= 1) {
						tessellator.setNormal(8, 8, 1.0F);

						for (int quad = 0; quad < 8; quad++) {
							tessellator.addVertexWithUV(f16 + 0, cloudY + 4, f17 + quad + 1 - f13, (f14 + 0) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + 8, cloudY + 4, f17 + quad + 1 - f13, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + 8, cloudY + 0, f17 + quad + 1 - f13, (f14 + 8) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
							tessellator.addVertexWithUV(f16 + 0, cloudY + 0, f17 + quad + 1 - f13, (f14 + 0) * INV_TEX_SIZE + floorU, (f15 + quad + 0.5F) * INV_TEX_SIZE + floorV);
						}
					}

					tessellator.draw();
				}
			}
		}

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(3042);
		GL11.glEnable(2884);
	}
}
