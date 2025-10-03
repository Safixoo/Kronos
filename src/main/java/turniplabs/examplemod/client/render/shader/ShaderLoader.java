package turniplabs.examplemod.client.render.shader;

import org.lwjgl.Sys;
import org.lwjgl.opengl.GL20;
import turniplabs.examplemod.ExampleMod;

import java.io.*;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

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
			ExampleMod.LOGGER.error("Shader {} has logged the next info: ", relPath);
			ExampleMod.LOGGER.error(shaderLog);
		}

		if (false) {
			System.out.println(shaderData);
		}

		GL20.glCompileShader(shaderId);
	}
}
