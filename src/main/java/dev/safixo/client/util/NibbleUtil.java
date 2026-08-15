package dev.safixo.client.util;

public class NibbleUtil {
	public static int getNibble(byte[] array, int index) {
		int arrayIndex = index >> 1;
		int shift = (index << 2) & 0b100;

		return array[arrayIndex] >>> shift & 0xF;
	}

	public static void setNibble(byte[] array, int index, int value) {
		int arrayIndex = index >> 1;
		int shift = (index << 2) & 0b100;

		value = array[arrayIndex] & (0xF0 >>> shift) | (value & 0xF) << shift;
		array[arrayIndex] = (byte) value;
	}
}
