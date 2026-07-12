package dev.safixo.client.util.data;

public class FrameTimer {
	private static final int MAX_FRAMES = 240;

	private final long[] frames = new long[MAX_FRAMES];
	private int lastIndex, counter, index;

	private long lastNano;

	private static FrameTimer INSTANCE;

	public static FrameTimer getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new FrameTimer();
		}

		return INSTANCE;
	}

	public void addFrame() {
		long nano = System.nanoTime();
		long runningTime = (nano - this.lastNano);
		this.lastNano = nano;

		this.frames[this.index] = runningTime;
		this.index++;

		if (this.index == MAX_FRAMES) {
			this.index = 0;
		}

		if (this.counter < MAX_FRAMES) {
			this.lastIndex = 0;
			this.counter++;
		} else {
			this.lastIndex = this.parseIndex(this.index + 1);
		}
	}

	public int getLagometerValue(long time, int multiplier) {
		double a = (double) time / 1.6666666E7D;
		return (int) (a * multiplier);
	}

	public int getLastIndex() {
		return this.lastIndex;
	}

	public int getIndex() {
		return this.index;
	}

	public int parseIndex(int rawIndex) {
		return rawIndex % MAX_FRAMES;
	}

	public long[] getFrames() {
		return this.frames;
	}
}
