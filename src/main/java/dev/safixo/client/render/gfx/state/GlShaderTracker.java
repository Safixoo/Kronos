package dev.safixo.client.render.gfx.state;

import org.lwjgl.BufferChecks;
import org.lwjgl.MemoryUtil;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class GlShaderTracker {
	public static int glGetUniformLocation(int program, ByteBuffer name) {
		return GL20.glGetUniformLocation(program, name);
	}

	public static int glGetUniformLocation(int program, CharSequence name) {
		return GL20.glGetUniformLocation(program, name);
	}

	public static int glCreateShader(int type) {
		return GL20.glCreateShader(type);
	}

	public static int glCreateProgram() {
		return GL20.glCreateProgram();
	}

	public static void glLinkProgram(int program) {
		GL20.glLinkProgram(program);
	}

	public static String glGetShaderInfoLog(int program, int maxLength) {
		return GL20.glGetShaderInfoLog(program, maxLength);
	}

	public static void glGetShaderInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
		GL20.glGetShaderInfoLog(program, length, infoLog);
	}

	public static String glGetProgramInfoLog(int program, int maxLength) {
		return GL20.glGetProgramInfoLog(program, maxLength);
	}

	public static void glGetProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
		GL20.glGetProgramInfoLog(program, length, infoLog);
	}

	public static void glCompileShader(int shader) {
		GL20.glCompileShader(shader);
	}

	public static void glAttachShader(int program, int shader) {
		GL20.glAttachShader(program, shader);
	}

	public static void glUseProgram(int program) {
		GL20.glUseProgram(program);
	}

	public static void glShaderSource(int shader, ByteBuffer string) {
		GL20.glShaderSource(shader, string);
	}

	public static void glShaderSource(int shader, CharSequence string) {
		GL20.glShaderSource(shader, string);
	}

	public static void glShaderSource(int shader, CharSequence[] strings) {
		GL20.glShaderSource(shader, strings);
	}
}
