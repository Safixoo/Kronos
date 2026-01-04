package dev.safixo.client.render.pipelines.entity_model;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.hooks.GLFunctions;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static dev.safixo.client.util.data.PrimitivesFlags.*;

public class AdvModelRenderer {
	private static final long DISPLAY_LIST_OFFSET;
	private static final long COMPILED;
	private static final VertexWriter WRITER = new VertexWriter(1024);

	static {
		long offset;
		long compiled;

		try {
			offset = UnsafeUtil.getFieldOffset(ModelRenderer.class.getDeclaredField(DEV_ENVIRONMENT ? "displayList" : "field_78811_r"));
			compiled = UnsafeUtil.getFieldOffset(ModelRenderer.class.getDeclaredField(DEV_ENVIRONMENT ? "compiled" : "field_78812_q"));
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}

		DISPLAY_LIST_OFFSET = offset;
		COMPILED = compiled;
	}

	public static boolean isCompiled(ModelRenderer model) {
		return UnsafeUtil.UNSAFE.getBoolean(model, COMPILED);
	}

	public static void setCompiled(ModelRenderer model, boolean cond) {
		 UnsafeUtil.UNSAFE.putBoolean(model, COMPILED, cond);
	}

	public static int getDisplayList(ModelRenderer model) {
		return UnsafeUtil.UNSAFE.getInt(model, DISPLAY_LIST_OFFSET);
	}

	public static void setDisplayList(ModelRenderer model, int displayList) {
		UnsafeUtil.UNSAFE.putInt(model, DISPLAY_LIST_OFFSET, displayList);
	}

	public static void render(ModelRenderer model, float scale) {
		if (model.isHidden || !model.showModel) {
			return;
		}

		if (!isCompiled(model)) {
			compileDisplayList(model, scale);
		}

		int displayList = getDisplayList(model);
		int vertices = displayList & 0xFFFF;
		int vertexArray = displayList >>> 16;

		GLFunctions.glPushMatrix();
		GLFunctions.glTranslatef(
			model.offsetX + model.rotationPointX * scale,
			model.offsetY + model.rotationPointY * scale,
			model.offsetZ + model.rotationPointZ * scale
		);

		if (model.rotateAngleY != 0.0F) {
			GLFunctions.glRotatef(model.rotateAngleY * (180F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
		}
		if (model.rotateAngleX != 0.0F) {
			GLFunctions.glRotatef(model.rotateAngleX * (180F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
		}
		if (model.rotateAngleZ != 0.0F) {
			GLFunctions.glRotatef(model.rotateAngleZ * (180F / (float)Math.PI), 0.0F, 0.0F, 1.0F);
		}

		GL30.glBindVertexArray(vertexArray);
		GL11.glDrawArrays(GL11.GL_QUADS, 0, vertices);

		if (model.childModels != null) {
			for (int i = 0; i < model.childModels.size(); i++) {
				((ModelRenderer) model.childModels.get(i)).render(scale);
			}
		}

		GLFunctions.glPopMatrix();
	}

	public static void renderWithRotation(ModelRenderer model, float scale) {
		if (model.isHidden || !model.showModel) {
			return;
		}

		if (!isCompiled(model)) {
			compileDisplayList(model, scale);
		}

		int displayList = getDisplayList(model);
		int vertices = displayList & 0xFFFF;
		int vertexArray = displayList >>> 16;

		GLFunctions.glPushMatrix();
		GLFunctions.glTranslatef(model.rotationPointX * scale, model.rotationPointY * scale, model.rotationPointZ * scale);
		if (model.rotateAngleY != 0.0F) {
			GLFunctions.glRotatef(model.rotateAngleY * (180F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
		}
		if (model.rotateAngleX != 0.0F) {
			GLFunctions.glRotatef(model.rotateAngleX * (180F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
		}
		if (model.rotateAngleZ != 0.0F) {
			GLFunctions.glRotatef(model.rotateAngleZ * (180F / (float)Math.PI), 0.0F, 0.0F, 1.0F);
		}

		GL30.glBindVertexArray(vertexArray);
		GL11.glDrawArrays(GL11.GL_QUADS, 0, vertices);

		GLFunctions.glPopMatrix();
	}

	public static void postRender(ModelRenderer model, float scale) {
		if (model.isHidden || !model.showModel) {
			return;
		}

		if (!isCompiled(model)) {
			compileDisplayList(model, scale);
		}

		if (model.rotateAngleX == 0.0F && model.rotateAngleY == 0.0F && model.rotateAngleZ == 0.0F) {
			if (model.rotationPointX != 0.0F || model.rotationPointY != 0.0F || model.rotationPointZ != 0.0F) {
				GL11.glTranslatef(model.rotationPointX * scale, model.rotationPointY * scale, model.rotationPointZ * scale);
			}
		} else {
			GL11.glTranslatef(model.rotationPointX * scale, model.rotationPointY * scale, model.rotationPointZ * scale);

			if (model.rotateAngleZ != 0.0F) {
				GL11.glRotatef(model.rotateAngleZ * (180F / (float)Math.PI), 0.0F, 0.0F, 1.0F);
			}
			if (model.rotateAngleY != 0.0F) {
				GL11.glRotatef(model.rotateAngleY * (180F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
			}
			if (model.rotateAngleX != 0.0F) {
				GL11.glRotatef(model.rotateAngleX * (180F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
			}
		}
	}

	private static void compileDisplayList(ModelRenderer model, float scale) {
		REDIRECT_DRAWING = true;
		WRITER.startDrawing();
		WRITER.setVertexFormat(DefaultVertexFormats.ENTITY_FORMAT);
		VertexWriter.setCurrentInstance(WRITER);

		Tessellator tessellator = Tessellator.instance;

		for (int i = 0; i < model.cubeList.size(); i++) {
			((ModelBox)model.cubeList.get(i)).render(tessellator, scale);
		}

		GlVertexBuffer buffer = new GlVertexBuffer(WRITER.getOffset(), GL15.GL_STATIC_DRAW);
		int vertexArray = GL30.glGenVertexArrays();

		GL30.glBindVertexArray(vertexArray);

		buffer.bind();
		GL11.glVertexPointer(3, GL11.GL_FLOAT, 24, 0);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

		GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 24, 12);
		GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

		GL11.glNormalPointer(GL11.GL_BYTE, 24, 20);
		GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
		buffer.unbind();

		GL30.glBindVertexArray(0);

		buffer.bufferData(WRITER.getVertexDataNio(), WRITER.getOffset());
		int drawData = WRITER.getVertices() | vertexArray << 16;

		WRITER.stopDrawing();
		WRITER.setVertexFormat(null);
		REDIRECT_DRAWING = false;
		setCompiled(model, true);
		setDisplayList(model, drawData);
	}
}
