package dev.safixo.client.render.pipelines.sky;

import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import net.minecraft.util.MathHelper;

import java.util.Random;

public class SkyBuilder {
	private final VertexWriter writer = new VertexWriter(512);

	private void buildStars() {
		Random random = new Random(10842L);
		this.writer.reset();
		this.writer.setQuadReceiver(DefaultVertexFormats.POSITION);

		for (int i = 0; i < 1500; i++) {
			double x = random.nextFloat() * 2.0F - 1.0F;
			double y = random.nextFloat() * 2.0F - 1.0F;
			double z = random.nextFloat() * 2.0F - 1.0F;

			if (x == 0.0D && z == 0.0D) {
				continue;
			}

			double d3 = 0.15F + random.nextFloat() * 0.1F;
			double length = x * x + y * y + z * z;

			if (length < 1.0D && length > 0.01D) {
				length = 1.0D / Math.sqrt(length);
				x *= length;
				y *= length;
				z *= length;
				double xs = x * 100.0D;
				double ys = y * 100.0D;
				double zs = z * 100.0D;

				double horizontal = Math.sqrt(x * x + z * z);
				double invHorizontal = 1.0 / horizontal;

				double d10 = z * invHorizontal;
				double d9 = x * invHorizontal;

				double d12 = horizontal;
				double d13 = y;

				double d14 = random.nextDouble() * (Math.PI * 2.0D);
				double d15 = Math.sin(d14);
				double d16 = Math.cos(d14);

				for (int j = 0; j < 4; j++) {
					double d18 = (double)((j & 2) - 1) * d3;
					double d19 = (double)((j + 1 & 2) - 1) * d3;
					double d20 = d18 * d16 - d19 * d15;
					double d21 = d19 * d16 + d18 * d15;
					double d22 = d20 * d12;
					double d23 = -d20 * d13;
					double d24 = d23 * d9 - d21 * d10;
					double d25 = d21 * d9 + d23 * d10;
					this.writer.addVertexToQuad((float) (xs + d24), (float) (ys + d22), (float) (zs + d25), true);
				}
			}
		}

	}

	public void buildAllBuffers(SkyRenderer skyRender) {
		this.buildStars();

		skyRender.skyStars.bufferData(this.writer.getNioPtr(), 0, this.writer.getOffset());
		skyRender.starVertices = this.writer.getVertices();

		this.buildSkyDisc(16.0F);

		skyRender.topSky.bufferData(this.writer.getNioPtr(), 0, this.writer.getOffset());
		skyRender.topVertices = this.writer.getVertices();

		this.buildSkyDisc(-16.0F);

		skyRender.bottomSky.bufferData(this.writer.getNioPtr(), 0, this.writer.getOffset());
		skyRender.bottomVertices = this.writer.getVertices();

		this.buildSunrise();

		skyRender.sunriseSky.bufferData(this.writer.getNioPtr(), 0, this.writer.getOffset());
		skyRender.sunriseVertices = this.writer.getVertices();
	}

	private void buildSunrise() {
		this.writer.reset();
		this.writer.setQuadReceiver(DefaultVertexFormats.POSITION_COLOR);

		this.writer.color = 0xFF_FFFFFF;
		this.writer.addVertex(0.0F, 100.0F, 0.0F);
		this.writer.color = 0x00_FFFFFF;

		for (int i = 0; i <= 16; i++) {
			float angle = (i * (float) (Math.PI * 2.0F)) / 16;
			float sinAngle = MathHelper.sin(angle);
			float cosAngle = MathHelper.cos(angle);
			this.writer.addVertex(sinAngle * 120.0F, cosAngle * 120.0F, -cosAngle * 40.0F);
		}
	}

	private void buildTopSky() {
		this.writer.reset();
		this.writer.setQuadReceiver(DefaultVertexFormats.POSITION);

		for (int x = -384; x <= 384; x += 64) {
			for (int z = -384; z <= 384; z += 64) {
				this.writer.addVertexToQuad(x + 0, 16, z + 0, true);
				this.writer.addVertexToQuad(x + 64, 16, z + 0, true);
				this.writer.addVertexToQuad(x + 64, 16, z + 64, true);
				this.writer.addVertexToQuad(x + 0, 16, z + 64, true);
			}
		}
	}

	private void buildSkyDisc(float yy) {
		this.writer.reset();
		this.writer.setQuadReceiver(DefaultVertexFormats.POSITION);

		float x = Math.signum(yy) * 512.0F;
		this.writer.addVertex(0.0F, yy, 0.0F);

		for (int i = -180; i <= 180; i += 45) {
			this.writer.addVertex(x * MathHelper.cos(i * (float) (Math.PI / 180.0)), yy, 512.0F * MathHelper.sin(i * (float) (Math.PI / 180.0)));
		}
	}

	private void buildBottomSky() {
		this.writer.reset();
		this.writer.setQuadReceiver(DefaultVertexFormats.POSITION);

		for (int x = -384; x <= 384; x += 64) {
			for (int z = -384; z <= 384; z += 64) {
				this.writer.addVertexToQuad(x + 64, -16, z + 0, true);
				this.writer.addVertexToQuad(x + 0, -16, z + 0, true);
				this.writer.addVertexToQuad(x + 0, -16, z + 64, true);
				this.writer.addVertexToQuad(x + 64, -16, z + 64, true);
			}
		}
	}

}
