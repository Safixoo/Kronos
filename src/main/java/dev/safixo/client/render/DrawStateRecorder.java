package dev.safixo.client.render;

import dev.safixo.client.render.gfx.state.GlDrawTracker;
import org.lwjgl.opengl.GL30;

// Saves some raster states of each draw call and tries to consolidate as much as possible.
public class DrawStateRecorder {
	private static final int MAX_DRAW_COUNT = 2048;

	private final long[] statePerDraw = new long[MAX_DRAW_COUNT];
	private final int[] bytePerDraw = new int[MAX_DRAW_COUNT];
	private final byte[] flagPerDraw = new byte[MAX_DRAW_COUNT];

	private int count;

	public void addDraw(long currentState, int flags, int bytes) {
		this.statePerDraw[this.count] = currentState;
		this.flagPerDraw[this.count] = (byte) flags;
		this.bytePerDraw[this.count] = bytes;
		this.count++;
	}

	// PRE: The vertex array is bind and the vertex buffer is uploaded.
	// PRE: There is draws waiting to flush.
	public void flushAllDraws(ImprovedTessellator tes, int drawMode) {
		if (this.count == 0) {
			throw new IllegalStateException("Shouldn't flush draws, when there isn't");
		}

		long lastState = this.statePerDraw[0];
		int lastFlags = this.flagPerDraw[0];

		int first = 0;
		int countAcc = 0;

		for (int i = 0; i < this.count; i++) {
			int bytes = this.bytePerDraw[i];
			int flags = this.flagPerDraw[i];
			long state = this.statePerDraw[i];

			if (lastState != state || lastFlags != flags) {
				int stride = ImprovedTessellator.STRIDES[lastFlags];

				GL30.glBindVertexArray(tes.getVertexArray(lastFlags));
				GlDrawTracker.drawArraysByState(lastState, drawMode, first / stride, countAcc / stride);

				if (FrameDebug.DEBUG_DRAWS) {
					FrameDebug.DRAW_CALLS_EMITTED++;
				}

				first += countAcc;
				countAcc = 0;
				lastState = state;
				lastFlags = flags;
			}

			countAcc += bytes;
		}

		int stride = ImprovedTessellator.STRIDES[lastFlags];

		GL30.glBindVertexArray(tes.getVertexArray(lastFlags));
		GlDrawTracker.drawArraysByState(lastState, drawMode, first / stride, countAcc / stride);

		if (FrameDebug.DEBUG_DRAWS) {
			FrameDebug.DRAW_CALLS_EMITTED++;
		}

		this.count = 0;
	}

	public int getDrawCount() {
		return this.count;
	}
}
