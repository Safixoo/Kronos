package dev.safixo.client.render.pipelines.sky;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.state.GlDrawTracker;
import dev.safixo.client.render.gfx.state.GlStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyAgreementSpi;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static dev.safixo.client.util.MatrixUtil.*;

public class SkyRenderer {
	private static SkyRenderer INSTANCE;

	protected final GlVertexBuffer skyStars = new GlVertexBuffer(GL15.GL_STATIC_DRAW);
	protected final GlVertexBuffer topSky = new GlVertexBuffer(GL15.GL_STATIC_DRAW);
	protected final GlVertexBuffer bottomSky = new GlVertexBuffer(GL15.GL_STATIC_DRAW);
	protected final GlVertexBuffer sunriseSky = new GlVertexBuffer(GL15.GL_STATIC_DRAW);

	protected int topVertices, bottomVertices, starVertices, sunriseVertices;

	public SkyRenderer() {
		new SkyBuilder().buildAllBuffers(this);
	}

	public static void renderSky(RenderGlobal renderGlobal, float partialTick) {
		if (INSTANCE == null) {
			INSTANCE = new SkyRenderer();
		}

		INSTANCE.render(renderGlobal, partialTick);
	}

	private void positionColor() {
		GL11.glVertexPointer(3, GL11.GL_FLOAT, 12 + 4, 0);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

		GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 12 + 4, 12);
		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
	}

	private void position() {
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glVertexPointer(3, GL11.GL_FLOAT, 12, 0);
	}

	private void unbindPosition() {
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
	}

	private void unbindColor() {
		GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
	}

	private void drawCelestials(TextureManager renderEngine, float starBrightness, float celestialAngle, float rainStrength) {
		rainStrength = 1.0F - rainStrength;
		GL11.glPushMatrix();
		GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(celestialAngle * 360.0F, 1.0F, 0.0F, 0.0F);

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, rainStrength);
		this.drawSun(renderEngine, ImprovedTessellator.getTessellator());
		this.drawMoon(renderEngine, ImprovedTessellator.getTessellator());
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		starBrightness *= rainStrength;
		if (starBrightness > 0.0F) {
			GL11.glColor4f(starBrightness, starBrightness, starBrightness, starBrightness);
			GL30.glBindVertexArray(0);

			this.skyStars.bind();
			this.position();
			this.skyStars.draw(this.starVertices, 0);
			this.unbindPosition();
			this.skyStars.unbind();
		}

		GL11.glPopMatrix();
	}

	private void drawSun(TextureManager renderEngine, ImprovedTessellator tes) {
		renderEngine.bindTexture(RenderGlobal.locationSunPng);
		tes.startDrawingQuads();
		tes.addVertexWithUV(-30.0F, 100.0D, -30.0F, 0.0D, 0.0D);
		tes.addVertexWithUV(30.0F, 100.0D, -30.0F, 1.0D, 0.0D);
		tes.addVertexWithUV(30.0F, 100.0D, 30.0F, 1.0D, 1.0D);
		tes.addVertexWithUV(-30.0F, 100.0D, 30.0F, 0.0D, 1.0D);
		tes.draw();
	}

	private void drawMoon(TextureManager renderEngine, ImprovedTessellator tes) {
		renderEngine.bindTexture(RenderGlobal.locationMoonPhasesPng);

		int moonPhase = Minecraft.getMinecraft().theWorld.getMoonPhase();
		int phaseU = moonPhase & 0b011;
		int phaseV = (moonPhase >> 2) & 0b1;

		float minU = (phaseU) / 4.0F;
		float minV = (phaseV) / 2.0F;
		float maxU = (phaseU + 1) / 4.0F;
		float maxV = (phaseV + 1) / 2.0F;

		tes.startDrawingQuads();
		tes.addVertexWithUV(-20.0f, -100.0, 20.0F, maxU, maxV);
		tes.addVertexWithUV(20.0F, -100.0, 20.0F, minU, maxV);
		tes.addVertexWithUV(20.0F, -100.0, -20.0f, minU, minV);
		tes.addVertexWithUV(-20.0f, -100.0, -20.0f, maxU, minV);
		tes.draw();
	}

	private void drawTopSky(RenderGlobal renderGlobal, Vec3 skyColor) {
		float skyR = (float) skyColor.xCoord;
		float skyG = (float) skyColor.yCoord;
		float skyB = (float) skyColor.zCoord;

		GL11.glColor3f(1, 1, 1);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		ImprovedTessellator tes = ImprovedTessellator.getTessellator();

		tes.startDrawing(GL11.GL_TRIANGLE_FAN);
		tes.setColorOpaque_F(skyR, skyG, skyB);
		tes.addVertex(0.0F, 16.0F, 0.0F);

		for (int i = -180; i <= 180; i += 45) {
			tes.addVertex(512 * MathHelper.cos(i * (float) (Math.PI / 180.0)), 16.0F, 512 * MathHelper.sin(i * (float) (Math.PI / 180.0)));
		}
		tes.draw();

		GL11.glDisable(GL11.GL_FOG);
	}

	private void drawSunrise(float[] sunriseColors, float celestialAngle) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		GL11.glPushMatrix();
		GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
		GL11.glRotatef(MathHelper.sin(celestialAngle * 2.0F * (float) Math.PI) < 0.0F ? 180.0F : 0.0F, 0.0F, 0.0F, 1.0F);
		GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);

		float sunriseR = sunriseColors[0];
		float sunriseG = sunriseColors[1];
		float sunriseB = sunriseColors[2];
		float sunriseA = sunriseColors[3];

		ImprovedTessellator tes = ImprovedTessellator.getTessellator();

		tes.startDrawing(GL11.GL_TRIANGLE_FAN);
		tes.setColorRGBA_F(sunriseR, sunriseG, sunriseB, sunriseA);
		tes.addVertex(0F, 100F, 0F);
		tes.setColorRGBA_F(sunriseR, sunriseG, sunriseB, 0.0F);

		for (int i = 0; i <= 16; i++) {
			float angle = (i * (float) (Math.PI * 2.0F)) / 16;
			float sinAngle = MathHelper.sin(angle);
			float cosAngle = MathHelper.cos(angle);
			tes.addVertex(sinAngle * 120.0F, cosAngle * 120.0F, -cosAngle * 40.0F * sunriseA);
		}

		tes.draw();
		GL11.glPopMatrix();
	}

	public void render(RenderGlobal renderGlobal, float partialTick) {
		Minecraft mc = Minecraft.getMinecraft();
		TextureManager renderEngine = mc.renderEngine;
		ImprovedTessellator tes = ImprovedTessellator.getTessellator();

		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		if (mc.theWorld.provider.dimensionId == 1) {
			GL11.glDisable(GL11.GL_FOG);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			RenderHelper.disableStandardItemLighting();
			renderEngine.bindTexture(RenderGlobal.locationEndSkyPng);

			Matrix4f mat = new Matrix4f();

			tes.startDrawingQuads();
			for (int i = 0; i < 6; ++i) {
				mat.identity();

				if (i == 1) {
					mat.rotate(90.0F, 1.0F, 0.0F, 0.0F);
				} else if (i == 2) {
					mat.rotate(-90.0f, 1.0F, 0.0F, 0.0F);
				} else if (i == 3) {
					mat.rotate(180.0F, 1.0F, 0.0F, 0.0F);
				} else if (i == 4) {
					mat.rotate(90.0F, 0.0F, 0.0F, 1.0F);
				} else if (i == 5) {
					mat.rotate(-90.0f, 0.0F, 0.0F, 1.0F);
				}

				Vector3f v0 = new Vector3f(-100.0F, -100.0F, -100.0F);
				Vector3f v1 = new Vector3f(-100.0F, -100.0F, 100.0F);
				Vector3f v2 = new Vector3f(100.0F, -100.0F, 100.0F);
				Vector3f v3 = new Vector3f(100.0F, -100.0F, -100.0F);

				tes.setColorOpaque_I(0x282828);
				tes.addVertexWithUV(transformX(mat, v0), transformY(mat, v0), transformZ(mat, v0), 0.0D, 0.0D);
				tes.addVertexWithUV(transformX(mat, v1), transformY(mat, v1), transformZ(mat, v1), 0.0D, 16.0D);
				tes.addVertexWithUV(transformX(mat, v2), transformY(mat, v2), transformZ(mat, v2), 16.0D, 16.0D);
				tes.addVertexWithUV(transformX(mat, v3), transformY(mat, v3), transformZ(mat, v3), 16.0D, 0.0D);
			}
			tes.draw();
		} else if (mc.theWorld.provider.isSurfaceWorld()) {
			Vec3 skyColor = mc.theWorld.getSkyColor(mc.renderViewEntity, partialTick);
			float starBrightness = mc.theWorld.getStarBrightness(partialTick);
			float celestialAngle = mc.theWorld.getCelestialAngle(partialTick);
			float rainStrength = mc.theWorld.getRainStrength(partialTick);

			this.drawTopSky(renderGlobal, skyColor);

			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			RenderHelper.disableStandardItemLighting();
			float[] sunriseColors = mc.theWorld.provider.calcSunriseSunsetColors(celestialAngle, partialTick);

			if (sunriseColors != null) {
				this.drawSunrise(sunriseColors, celestialAngle);
			}

			this.drawCelestials(renderEngine, starBrightness, celestialAngle, rainStrength);
		}

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glDepthMask(true);
	}


}
