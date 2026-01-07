package dev.safixo.client.render.gfx;

import dev.safixo.client.render.gfx.shader.ShaderDefine;
import org.lwjgl.opengl.GL20;

import java.io.*;
import java.util.List;

public class ShaderLoader {
	private static final String SHADER_PATH = "shaders/";

	public static void compileShader(List<ShaderDefine> shaderDefines, String relPath, int shaderId) {
		String shaderData;

		try (InputStream inputStream = ShaderLoader.class.getResourceAsStream("/" + SHADER_PATH + relPath)) {
			if (inputStream == null) {
				throw new RuntimeException("Input stream is NULL!");
			}

			String line;
			StringBuilder builder = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

			while ((line = reader.readLine()) != null) {
				builder.append(line).append(System.lineSeparator());

				if (line.contains("#version") && shaderDefines != null) {
					for (ShaderDefine define : shaderDefines) {
						builder.append("#define ").append(define.defineName).append(System.lineSeparator());
					}
				}
			}

			shaderData = builder.toString();
		} catch (Exception genericException) {
			throw new RuntimeException(genericException);
		}

		GL20.glShaderSource(shaderId, shaderData);
		GL20.glCompileShader(shaderId);
	}

}
