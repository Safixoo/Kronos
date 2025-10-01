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

		try {
			InputStream inputStream;

			if (relPath.equals("terrain/terrain_vertex.glsl")) {
				File devFile = new File("terrain_vertex.glsl");
				if (devFile.exists()) {
					inputStream = new FileInputStream(devFile);
				} else {
					throw new FileNotFoundException("Shader file not found in dev path: " + devFile.getAbsolutePath());
				}
			} else {
				inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(SHADER_PATH + relPath);
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
