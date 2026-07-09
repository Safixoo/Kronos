package dev.safixo.client.render.pipelines.entity_model;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.state.GlMatrixTracker;
import dev.safixo.client.render.gfx.state.GlTextureTracker;
import dev.safixo.client.render.gfx.util.GlBufferUtil;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.Matrix4Stack;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.hooks.MinecraftHook;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static dev.safixo.client.util.data.PrimitivesFlags.*;

@SuppressWarnings("unused")
public class AdvancedModelRenderer {
	private static final long DISPLAY_LIST_OFFSET;
	private static final long COMPILED;

	private static final long PTR_BUFFER = NativeBuffer.nmemAlloc(16 * 4);
	private static final FloatBuffer BUFFER = NativeBuffer.wrap(PTR_BUFFER).asFloatBuffer();
	private static final Matrix4f MATRIX = new Matrix4f();

	private static final FloatBuffer TEMP_BUFFER = NativeBuffer.memAllocFloat(16);

	// Global vertex buffer/array for model rendering.
	public static GlVertexBuffer VERTEX_BUFFER;
	public static GlVertexArrayObject VERTEX_ARRAY_FPP;
	public static GlVertexArrayObject VERTEX_ARRAY_GL20;

	// Current offset for writing in the vertex buffer.
	private static int OFFSET = 0;
	private static final ReferenceArrayList<ModelRenderer> MODELS = new ReferenceArrayList<>();

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

	private static boolean isCompiled(ModelRenderer model) {
		return UnsafeUtil.UNSAFE.getBoolean(model, COMPILED);
	}

	private static void setCompiled(ModelRenderer model, boolean cond) {
		UnsafeUtil.UNSAFE.putBoolean(model, COMPILED, cond);
	}

	private static int getDisplayList(ModelRenderer model) {
		return UnsafeUtil.UNSAFE.getInt(model, DISPLAY_LIST_OFFSET);
	}

	private static void setDisplayList(ModelRenderer model, int displayList) {
		UnsafeUtil.UNSAFE.putInt(model, DISPLAY_LIST_OFFSET, displayList);
	}

	private static void queueModel(ModelRenderer model, float scale, float offX, float offY, float offZ) {
		int displayList = getDisplayList(model);

		boolean rotY = model.rotateAngleY != 0.0F;
		boolean rotX = model.rotateAngleX != 0.0F;
		boolean rotZ = model.rotateAngleZ != 0.0F;

		Matrix4Stack matStack = GlMatrixTracker.MODEL_VIEW_STACK;

		if (!rotX && !rotY && !rotZ) {
			boolean notNullTranslation = offX != 0 || offY != 0 || offZ != 0;

			if (notNullTranslation) {
				matStack.top().translate(offX, offY, offZ);
			}

			ModelQueue.INSTANCE.addToQueue(displayList);

			if (model.childModels != null) {
				for (int i = 0; i < model.childModels.size(); i++) {
					((ModelRenderer) model.childModels.get(i)).render(scale);
				}
			}

			if (notNullTranslation) {
				matStack.top().translate(-offX, -offY, -offZ);
			}
		} else {
			Matrix4f modelView = MATRIX.identity();
			modelView.translation(offX, offY, offZ);

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
			matStack.push();
			matStack.top().mul(modelView);

			ModelQueue.INSTANCE.addToQueue(displayList);

			if (model.childModels != null) {
				for (int i = 0; i < model.childModels.size(); i++) {
					((ModelRenderer) model.childModels.get(i)).render(scale);
				}
			}

			matStack.pop();
		}
	}

	public static void render(ModelRenderer model, float scale) {
		if (model.isHidden || !model.showModel) {
			return;
		}

		if (!isCompiled(model)) {
			compileDisplayList(model, scale);
		}

		float offX = model.offsetX + model.rotationPointX * scale;
		float offY = model.offsetY + model.rotationPointY * scale;
		float offZ = model.offsetZ + model.rotationPointZ * scale;

		if (MinecraftHook.FAST_ENTITY_PATH) {
			queueModel(model, scale, offX, offY, offZ);
		} else {
			renderModel(model, scale, offX, offY, offZ);
		}
	}

	public static void renderWithRotation(ModelRenderer model, float scale) {
		if (model.isHidden || !model.showModel) {
			return;
		}

		if (!isCompiled(model)) {
			compileDisplayList(model, scale);
		}

		float offX = model.rotationPointX * scale;
		float offY = model.rotationPointY * scale;
		float offZ = model.rotationPointZ * scale;

		renderModel(model, scale, offX, offY, offZ);
	}

	public static void renderModel(ModelRenderer model, float scale, float offX, float offY, float offZ) {
		int displayList = getDisplayList(model);
		int vertices = displayList & 0xFFFF;
		int offset = displayList >>> 16;


		boolean rotY = model.rotateAngleY != 0.0F;
		boolean rotX = model.rotateAngleX != 0.0F;
		boolean rotZ = model.rotateAngleZ != 0.0F;

		if (!rotX && !rotY && !rotZ) {
			boolean notNullTranslation = offX != 0 || offY != 0 || offZ != 0;

			if (notNullTranslation) {
				GL11.glTranslatef(offX, offY, offZ);
			}

			GL30.glBindVertexArray(VERTEX_ARRAY_FPP.getHandle());
			GL11.glDrawArrays(GL11.GL_QUADS, offset, vertices);

			if (model.childModels != null) {
				for (int i = 0; i < model.childModels.size(); i++) {
					((ModelRenderer) model.childModels.get(i)).render(scale);
				}
			}

			if (notNullTranslation) {
				GL11.glTranslatef(-offX, -offY, -offZ);
			}
		} else {
			Matrix4f modelView = MATRIX.identity();
			modelView.translation(offX, offY, offZ);

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
			GL11.glPushMatrix();

			GL11.glMultMatrix(modelView.get(TEMP_BUFFER));

			GL30.glBindVertexArray(VERTEX_ARRAY_FPP.getHandle());
			GL11.glDrawArrays(GL11.GL_QUADS, offset, vertices);

			if (model.childModels != null) {
				for (int i = 0; i < model.childModels.size(); i++) {
					((ModelRenderer) model.childModels.get(i)).render(scale);
				}
			}

			GL11.glPopMatrix();
		}
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
		VertexWriter writer = new VertexWriter(4096);

		REDIRECT_DRAWING = true;
		MODELS.add(model);

		writer.startDrawing();
		writer.setVertexFormat(DefaultVertexFormats.ENTITY_FORMAT);
		VertexWriter.setCurrentInstance(writer);

		Tessellator tessellator = Tessellator.instance;

		for (int i = 0; i < model.cubeList.size(); i++) {
			((ModelBox)model.cubeList.get(i)).render(tessellator, scale);
		}

		if (VERTEX_BUFFER == null) {
			VERTEX_BUFFER = new GlVertexBuffer(512 * 1024, GL15.GL_STATIC_DRAW);
		}

		if (VERTEX_ARRAY_GL20 == null) {
			VERTEX_ARRAY_GL20 = new GlVertexArrayObject(DefaultVertexFormats.ENTITY_FORMAT);
			VERTEX_ARRAY_FPP = new GlVertexArrayObject(null);
		}

		VERTEX_ARRAY_FPP.bind(null);
		VERTEX_BUFFER.bind();
		{
			GL11.glVertexPointer(3, GL11.GL_FLOAT, 24, 0);
			GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 24, 12);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

			GL11.glNormalPointer(GL11.GL_BYTE, 24, 20);
			GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
		}
		VERTEX_ARRAY_FPP.unbind();
		VERTEX_BUFFER.unbind();

		VERTEX_BUFFER.bufferSubData(writer.getWriterNio(), OFFSET, writer.getOffset());
		int drawData = writer.getVertices() | (OFFSET / 24) << 16;

		OFFSET += writer.getOffset();

		writer.stopDrawing();
		writer.setVertexFormat(null);
		writer.delete();
		REDIRECT_DRAWING = false;

		setCompiled(model, true);
		setDisplayList(model, drawData);
	}

	public static void cleanupEntityModelPool() {
		if (VERTEX_BUFFER != null) VERTEX_BUFFER.delete();
		if (VERTEX_ARRAY_FPP != null) VERTEX_ARRAY_FPP.delete();
		if (VERTEX_ARRAY_GL20 != null) VERTEX_ARRAY_GL20.delete();

		VERTEX_ARRAY_GL20 = null;
		VERTEX_ARRAY_FPP = null;
		VERTEX_BUFFER = null;
		OFFSET = 0;

		for (ModelRenderer model : MODELS) {
			setCompiled(model, false);
			setDisplayList(model, 0);
		}

		MODELS.clear();
	}
}
