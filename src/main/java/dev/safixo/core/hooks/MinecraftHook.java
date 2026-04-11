package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.ClientChunkListener;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.Matrix4Stack;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.HookUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

@SuppressWarnings("unused")
public class MinecraftHook {
	private static final int ITEM_STRIDE = 24;
	public static final Field LIGHTMAP_RESOURCE = HookUtils.getField(EntityRenderer.class,
		"locationLightMap", "field_110922_T");

	public static void checkGLError(Minecraft minecraft, String str) {
		if (!PrimitivesFlags.DETECTED) {
			PrimitivesFlags.processDevInfo();
		}

		// NO-OP
	}

	private static final int TOP_NORMAL = MathExt.packedNormal(0.0F, 1.0F, 0.0F);
	private static final int BOTTOM_NORMAL = MathExt.packedNormal(0.0F, -1.0F, 0.0F);

	private static final int FRONT_NORMAL = MathExt.packedNormal(0.0F, 0.0F, 1.0F);
	private static final int BEHIND_NORMAL = MathExt.packedNormal(0.0F, 0.0F, -1.0F);

	private static final int RIGHT_NORMAL = MathExt.packedNormal(1.0F, 0.0F, 0.0F);
	private static final int LEFT_NORMAL = MathExt.packedNormal(-1.0F, 0.0F, 0.0F);

	public static void renderItemIn2D(Tessellator tes, float minU, float maxV, float maxU, float minV, int width, int height, float scale) {
		ImprovedTessellator ver = (ImprovedTessellator) tes;
		ver.startDrawingQuads();

		long ptr = ver.vertexPtr + ver.offset;

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8 >= ver.capacity) {
			ver.resize();
		}

		Matrix4f matrix = GlStateTracker.MODEL_VIEW_STACK.top();

		if (matrix.m22() > 0) {
			// Front quad.
			ptr = addVertex(ptr, 0.0F, 0.0F, 0.0F, minU, minV, FRONT_NORMAL);
			ptr = addVertex(ptr, 1.0F, 0.0F, 0.0F, maxU, minV, FRONT_NORMAL);
			ptr = addVertex(ptr, 1.0F, 1.0F, 0.0F, maxU, maxV, FRONT_NORMAL);
			ptr = addVertex(ptr, 0.0F, 1.0F, 0.0F, minU, maxV, FRONT_NORMAL);
		}
		if (matrix.m22() < 0) {
			// Behind quad.
			ptr = addVertex(ptr, 0.0F, 1.0F, -scale, minU, maxV, BEHIND_NORMAL);
			ptr = addVertex(ptr, 1.0F, 1.0F, -scale, maxU, maxV, BEHIND_NORMAL);
			ptr = addVertex(ptr, 1.0F, 0.0F, -scale, maxU, minV, BEHIND_NORMAL);
			ptr = addVertex(ptr, 0.0F, 0.0F, -scale, minU, minV, BEHIND_NORMAL);
		}

		float invWidth = 1.0f / width;
		float invHeight = 1.0f / height;

		float halfTexelU = 0.5F * (minU - maxU) * invWidth;
		float haltTexelV = 0.5F * (minV - maxV) * invHeight;

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8L * width >= ver.capacity) {
			ver.resize();
		}

		for (int i = 0; i < width; i++) {
			float relW = i * invWidth;
			float nextRelW = relW + invWidth;
			float u = minU + (maxU - minU) * relW - halfTexelU;

			if (matrix.m02() > 0) {
				// +X
				ptr = addVertex(ptr, nextRelW, 1.0F, -scale, u, maxV, RIGHT_NORMAL);
				ptr = addVertex(ptr, nextRelW, 1.0F, 0.0F, u, maxV, RIGHT_NORMAL);
				ptr = addVertex(ptr, nextRelW, 0.0F, 0.0F, u, minV, RIGHT_NORMAL);
				ptr = addVertex(ptr, nextRelW, 0.0F, -scale, u, minV, RIGHT_NORMAL);
			}
			if (matrix.m02() < 0) {
				// -X
				ptr = addVertex(ptr, relW, 0.0F, -scale, u, minV, LEFT_NORMAL);
				ptr = addVertex(ptr, relW, 0.0F, 0.0F, u, minV, LEFT_NORMAL);
				ptr = addVertex(ptr, relW, 1.0F, 0.0F, u, maxV, LEFT_NORMAL);
				ptr = addVertex(ptr, relW, 1.0F, -scale, u, maxV, LEFT_NORMAL);
			}

		}

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8L * height >= ver.capacity) {
			ver.resize();
		}

		for (int i = 0; i < height; i++) {
			float relW = i * invHeight;
			float nextRelW = relW + invHeight;
			float v = minV + (maxV - minV) * relW - haltTexelV;

			if (matrix.m12() > 0) {
				// +Y
				ptr = addVertex(ptr, 0.0F, nextRelW, 0.0F, minU, v, TOP_NORMAL);
				ptr = addVertex(ptr, 1.0F, nextRelW, 0.0F, maxU, v, TOP_NORMAL);
				ptr = addVertex(ptr, 1.0F, nextRelW, -scale, maxU, v, TOP_NORMAL);
				ptr = addVertex(ptr, 0.0F, nextRelW, -scale, minU, v, TOP_NORMAL);
			}
			if (matrix.m12() < 0) {
				// -Y
				ptr = addVertex(ptr, 1.0F, relW, 0.0F, maxU, v, BOTTOM_NORMAL);
				ptr = addVertex(ptr, 0.0F, relW, 0.0F, minU, v, BOTTOM_NORMAL);
				ptr = addVertex(ptr, 0.0F, relW, -scale, minU, v, BOTTOM_NORMAL);
				ptr = addVertex(ptr, 1.0F, relW, -scale, maxU, v, BOTTOM_NORMAL);
			}
		}

		ver.offset = (int) (ptr - ver.vertexPtr);
		ver.vertices = ver.offset / ITEM_STRIDE;
		ver.flags = ImprovedTessellator.VERTEX_UV | ImprovedTessellator.VERTEX_NORMAL;
		ver.draw();
	}

	private static long addVertex(long ptr, float x, float y, float z, float u, float v, int normal) {
		UnsafeUtil.memPutFloat(ptr + 0, x);
		UnsafeUtil.memPutFloat(ptr + 4, y);
		UnsafeUtil.memPutFloat(ptr + 8, z);

		UnsafeUtil.memPutFloat(ptr + 12, u);
		UnsafeUtil.memPutFloat(ptr + 16, v);

		UnsafeUtil.memPutInt(ptr + 20, normal);

		return ptr + ITEM_STRIDE;
	}

	// Here chunkExists is avoided but shouldn't change behavior because this is used only for the client.
	public static int getLightBrightnessForSkyBlocks(World world, int x, int y, int z, int minBlockLight) {
		// if y < 0, makes y = 0, otherwise nothing.
		y &= ~(y >> 31);

		if (y >= 256 || x < -30000000 || z < -30000000 || x >= 30000000 || z >= 30000000) {
			return MathExt.getLightmapCoord(15, 0);
		}

		int chunkX = x >> 4;
		int chunkZ = z >> 4;

		Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);

		ExtendedBlockStorage blockStorage = chunk.getBlockStorageArray()[y >> 4];
		int blockLight = 0;
		int skyLight = 0;

		if (blockStorage != null) {
			int blockIndex = SectionCache.makeBlockIndex(x & 15, y & 15, z & 15);

			byte[] blockLightArray = blockStorage.getBlocklightArray().data;
			blockLight = SectionCache.getNibble(blockLightArray, blockIndex);

			if (blockStorage.getSkylightArray() != null && !world.provider.hasNoSky) {
				byte[] skyLightArray = blockStorage.getSkylightArray().data;
				skyLight = SectionCache.getNibble(skyLightArray, blockIndex);
			}
		} else if (chunk.canBlockSeeTheSky(x & 15, y, z & 15)) {
			skyLight = 15;
		}

		return MathExt.getLightmapCoord(skyLight, Math.max(minBlockLight, blockLight));
	}

	public static IChunkProvider createChunkProvider(WorldClient worldClient) {
		ClientChunkListener listener = new ClientChunkListener(worldClient);
		HookUtils.setField(worldClient, "clientChunkProvider", "field_73033_b", listener);

		return listener;
	}

	public static boolean shouldSideBeRendered(BlockSnow blockSnow, IBlockAccess worldAccess, int x, int y, int z, int dir) {
		if (dir == Direction.UP) {
			return true;
		}

		int blockId = worldAccess.getBlockId(x, y, z);

		if (PrimitivesFlags.SOLID[blockId] || dir > Direction.UP && blockId == Block.snow.blockID) {
			return false;
		}

		return true;
	}

	private static boolean SETUP_LIGHTING = false;

	public static void enableLightmap(EntityRenderer render, double partialTick) {
		OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		Minecraft.getMinecraft().getTextureManager().bindTexture((ResourceLocation) HookUtils.getFieldValue(LIGHTMAP_RESOURCE, render));

		if (!SETUP_LIGHTING) {
			float scale = 1.0f / 256.0f;
			GL11.glMatrixMode(GL11.GL_TEXTURE);

			GL11.glLoadIdentity();
			GL11.glScalef(scale, scale, scale);
			GL11.glTranslatef(8.0F, 8.0F, 8.0F);

			GL11.glMatrixMode(GL11.GL_MODELVIEW);

			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 10241, 9729);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 10240, 9729);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 10241, 9729);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 10240, 9729);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 10242, 10496);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 10243, 10496);

			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			SETUP_LIGHTING = true;
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
	}

	public static void disableLightmap(EntityRenderer render, double partialTick) {
		OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
	}
}
