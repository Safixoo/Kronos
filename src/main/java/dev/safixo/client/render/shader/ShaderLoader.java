package dev.safixo.client.render.shader;

import org.lwjgl.opengl.GL20;
import dev.safixo.KronosMod;

import java.io.*;

public class ShaderLoader {
	private static final String SHADER_PATH = "shaders/";

	public static void compileShader(String relPath, int shaderId) {
		String shaderData;

		try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(SHADER_PATH + relPath)) {
			if (inputStream == null) {
				throw new RuntimeException("Input stream is NULL!");
			}

			String line;
			StringBuilder builder = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

			while ((line = reader.readLine()) != null) {
				builder.append(line).append(System.lineSeparator());
			}

			shaderData = builder.toString();
		} catch (Exception genericException) {
			throw new RuntimeException(genericException);
		}

		GL20.glShaderSource(shaderId, shaderData);
		String shaderLog = GL20.glGetShaderInfoLog(shaderId, 250);

		if (!shaderLog.isEmpty()) {
			KronosMod.LOGGER.error("Shader {} has logged the next info: ", relPath);
			KronosMod.LOGGER.error(shaderLog);
		}

		if (false) {
			System.out.println(shaderData);
		}

		GL20.glCompileShader(shaderId);
	}
}
