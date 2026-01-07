package dev.safixo.client.render.pipelines.entity_model;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.Matrix4Stack;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.hooks.GLFunctions;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.joml.Matrix4x3f;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

import static dev.safixo.client.util.data.PrimitivesFlags.*;

public class AdvModelRenderer {
	private static final long DISPLAY_LIST_OFFSET;
	private static final long COMPILED;

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

	private static final long PTR_BUFFER = NativeBuffer.nmemAlloc(16 * 4) - Matrix4Stack.M00_OFFSET;
	private static final FloatBuffer BUFFER = NativeBuffer.wrap(PTR_BUFFER + Matrix4Stack.M00_OFFSET).asFloatBuffer();
	private static final Matrix4f MATRIX = new Matrix4f();

	//
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

		Matrix4f modelView = MATRIX.identity();
		modelView.translation(
			model.offsetX + model.rotationPointX * scale,
			model.offsetY + model.rotationPointY * scale,
			model.offsetZ + model.rotationPointZ * scale
		);

		boolean rotY = model.rotateAngleY != 0.0F;
		boolean rotX = model.rotateAngleX != 0.0F;
		boolean rotZ = model.rotateAngleZ != 0.0F;

		if (rotX && rotY && rotZ) {
			modelView.rotateZYX(model.rotateAngleZ, model.rotateAngleY, model.rotateAngleX);
		} else {
			if (rotY) {
				modelView.rotateY(model.rotateAngleY);
			}
			if (rotX) {
				modelView.rotateX(model.rotateAngleX);
			}
			if (rotZ) {
				modelView.rotateZ(model.rotateAngleZ);
			}
		}

		Matrix4Stack.copyMat(modelView, PTR_BUFFER);
		GLFunctions.glMultMatrix(BUFFER);

		GlVertexArrayObject.bindVertexArray(vertexArray);
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

		Matrix4f modelView = MATRIX.identity();
		modelView.translation(
			model.rotationPointX * scale,
			model.rotationPointY * scale,
			model.rotationPointZ * scale
		);

		boolean rotY = model.rotateAngleY != 0.0F;
		boolean rotX = model.rotateAngleX != 0.0F;
		boolean rotZ = model.rotateAngleZ != 0.0F;

		if (rotX && rotY && rotZ) {
			modelView.rotateZYX(model.rotateAngleZ, model.rotateAngleY, model.rotateAngleX);
		} else {
			if (rotY) {
				modelView.rotateY(model.rotateAngleY);
			}
			if (rotX) {
				modelView.rotateX(model.rotateAngleX);
			}
			if (rotZ) {
				modelView.rotateZ(model.rotateAngleZ);
			}
		}

		GlVertexArrayObject.bindVertexArray(vertexArray);
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
		VertexWriter writer = new VertexWriter(512);

		REDIRECT_DRAWING = true;
		writer.startDrawing();
		writer.setVertexFormat(DefaultVertexFormats.ENTITY_FORMAT);
		VertexWriter.setCurrentInstance(writer);

		Tessellator tessellator = Tessellator.instance;

		for (int i = 0; i < model.cubeList.size(); i++) {
			((ModelBox)model.cubeList.get(i)).render(tessellator, scale);
		}

		GlVertexBuffer buffer = new GlVertexBuffer(writer.getOffset(), GL15.GL_STATIC_DRAW);
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

		buffer.bufferData(writer.getVertexDataNio(), writer.getOffset());
		int drawData = writer.getVertices() | vertexArray << 16;

		writer.stopDrawing();
		writer.setVertexFormat(null);
		REDIRECT_DRAWING = false;

		writer.clear();
		setCompiled(model, true);
		setDisplayList(model, drawData);
	}
}
