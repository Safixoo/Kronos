package dev.safixo.client.render.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import org.lwjgl.opengl.GL20;
import dev.safixo.KronosMod;

import java.io.*;

public class ShaderLoader {
	private static final boolean OUTPUT_SHADER_CODE = false;
	private static final String SHADER_PATH = "shaders/";

	public static void compileShader(String relPath, int shaderId) {
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
			}

			shaderData = builder.toString();
		} catch (Exception genericException) {
			throw new RuntimeException(genericException);
		}

		GL20.glShaderSource(shaderId, shaderData);
		String shaderLog = GL20.glGetShaderInfoLog(shaderId, 250);

		final GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();

		if (!shaderLog.isEmpty()) {
			chat.printChatMessage("Shader " + relPath + " has logged the next info: ");
			chat.printChatMessage(shaderLog);
		}

		if (OUTPUT_SHADER_CODE) {
			chat.printChatMessage("");
			chat.printChatMessage("§eSHADER PATH: §r" + relPath);
			chat.printChatMessage("");
			chat.printChatMessage(shaderData);
		}

		GL20.glCompileShader(shaderId);
	}

}
