package dev.safixo.client.render.gfx.state;

import org.lwjgl.opengl.*;

import java.nio.IntBuffer;

public class GlResourceTracker {
	public static int glGenBufferARB() {
		return glGenBuffers();
	}

	public static int glGenBuffers() {
		return GL15.glGenBuffers();
	}

	public static int glGenLists(int range) {
		return GL11.glGenLists(range);
	}

	public static int glGenTextures() {
		return GL11.glGenTextures();
	}

	public static int glGenQueriesARB() {
		return GL15.glGenQueries();
	}

	public static void glGenQueriesARB(IntBuffer buffer) {
		GL15.glGenQueries(buffer);
	}

	public static int glGenSamplers() {
		return GL33.glGenSamplers();
	}

	public static int glGenVertexArrays() {
		return GL30.glGenVertexArrays();
	}

	public static void glDeleteLists(int list, int range) {
		GL11.glDeleteLists(list, range);
	}

	public static void glDeleteTextures(int textures) {
		GL11.glDeleteTextures(textures);
	}

	public static void glDeleteTextures(IntBuffer textures) {
		GL11.glDeleteTextures(textures);
	}

	public static void glDeleteVertexArrays(IntBuffer arrays) {
		GL30.glDeleteVertexArrays(arrays);
	}

	public static void glDeleteVertexArrays(int vao) {
		GL30.glDeleteVertexArrays(vao);
	}

	public static void glDeleteShader(int shader) {
		GL20.glDeleteShader(shader);
	}

	public static void glDeleteProgram(int program) {
		GL20.glDeleteProgram(program);
	}

	public static void glDeleteBuffers(int buffer) {
		GL15.glDeleteBuffers(buffer);
	}
}
