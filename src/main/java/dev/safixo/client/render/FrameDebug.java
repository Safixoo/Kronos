package dev.safixo.client.render;

import dev.safixo.client.render.gfx.state.GlBooleanTracker;
import dev.safixo.core.KronosTransformer;
import org.lwjgl.input.Keyboard;

public class FrameDebug {
	public static int DRAW_CALLS_REQUESTED;
	public static int DRAW_CALLS_EMITTED;
	public static int BYTES;
	public static int VERTICES;
	public static int UPLOADS;

	public static boolean DEBUG_DRAWS = false;
	public static boolean DEBUG_GL_STATE = false;
	public static boolean DEBUG_UNKNOWN_FUNCTIONS = false;

	static int lastKey;
	static long lastTime;

	public static void debug() {
		FrameDebug.debugGlState();
		FrameDebug.debugDraws();
		FrameDebug.debugUnknownFunctions();

		lastKey = Keyboard.getEventKey();
	}

	static void debugGlState() {
		if (!DEBUG_GL_STATE) {
			return;
		}

		if (Keyboard.getEventKey() == Keyboard.KEY_B && lastKey != Keyboard.KEY_B) {
			StringBuilder builder = new StringBuilder();

			builder.append(System.lineSeparator()).append("--- All GL states used: ---");
			for (int cap : GlBooleanTracker.CAPS_OUTPUTTED) {
				builder.append("[X] ").append(KronosTransformer.CAP_TO_NAME.get(cap)).append(System.lineSeparator());
			}
			System.out.println(builder);
		}
	}

	static void debugUnknownFunctions() {
		if (!DEBUG_UNKNOWN_FUNCTIONS) {
			return;
		}

		if (Keyboard.getEventKey() == Keyboard.KEY_N && lastKey != Keyboard.KEY_N) {
			StringBuilder builder = new StringBuilder();

			builder.append(System.lineSeparator()).append("--- All Unknown Used LWJGL Functions: ---");
			for (String cap : KronosTransformer.UNKNOWN_FUNCTIONS) {
				if (!cap.startsWith("gl")) {
					continue;
				}

				builder.append(System.lineSeparator()).append("[X] ").append(cap);
			}
			System.out.println(builder);
		}
	}

	static void debugDraws() {
		if (!DEBUG_DRAWS) {
			return;
		}

		if (Math.abs(lastTime - System.nanoTime()) > 800_000_000) {
			StringBuilder stringBuilder = new StringBuilder().append(System.lineSeparator());

			stringBuilder.append("DRAWS DEBUG:").append(System.lineSeparator());
			stringBuilder.append("- Emitted:   ").append(FrameDebug.DRAW_CALLS_EMITTED).append(System.lineSeparator());
			stringBuilder.append("- Requested: ").append(FrameDebug.DRAW_CALLS_REQUESTED).append(System.lineSeparator());
			stringBuilder.append("- Vertices:  ").append(FrameDebug.VERTICES).append(System.lineSeparator());
			stringBuilder.append("- Uploads:   ").append(FrameDebug.UPLOADS).append(System.lineSeparator());
			stringBuilder.append("- Kilobits:  ").append(FrameDebug.BYTES >> 10).append("Kb");

			System.out.println(stringBuilder);
			lastTime = System.nanoTime();
		}

		FrameDebug.DRAW_CALLS_REQUESTED = 0;
		FrameDebug.DRAW_CALLS_EMITTED = 0;
		FrameDebug.VERTICES = 0;
		FrameDebug.BYTES = 0;
		FrameDebug.UPLOADS = 0;
	}
}
