package dev.safixo.client.util.collection;

import dev.safixo.client.util.MathExt;

public class PaletteArray {
	private byte[] palette;
	private byte[] values;

	private int valueSize;
	private int valueCapacity;
	private int bitsPerValue, maskPerValue;

	private int valuesPerByte;

	public PaletteArray(int size) {
		int nextPotSize = MathExt.nextPOT(size + 1);
		int bits = MathExt.nextPOT(MathExt.getLowestBitPosition(nextPotSize));
		int paletteSize = (bits * 4096) / 8;

		this.values = new byte[nextPotSize];
		this.palette = new byte[paletteSize];

		this.bitsPerValue = bits;
		this.maskPerValue = (1 << bits) - 1;

		this.valueCapacity = nextPotSize;
		this.valuesPerByte = 8 / bits;
	}

	public void feedArray(byte[] array) {
		if (array == null) {
			return;
		}

		for (int i = 0; i < 4096; i++) {
			array[i] = (byte) this.getValue(i);
		}
	}

	public int getValue(int index) {
		return this.values[this.getId(index)] & 0xFF;
	}

	public void setValue(int index, int value) {
		int valuePosition = this.findValuePosition(value);

		if (valuePosition == -1) {
			valuePosition = this.addValue(value);
		}

		int arrayIndex = index / this.valuesPerByte;
		int shift = (index & (this.valuesPerByte - 1)) * this.bitsPerValue;

		this.palette[arrayIndex] &= (byte) ~(this.maskPerValue << shift);
		this.palette[arrayIndex] |= (byte) (valuePosition << shift);
	}

	private int getId(int index) {
		int arrayIndex = index / this.valuesPerByte;
		int shift = (index & (this.valuesPerByte - 1)) * this.bitsPerValue;

		return (this.palette[arrayIndex] >>> shift) & this.maskPerValue;
	}

	private int addValue(int value) {
		if (this.valueSize + 1 >= this.valueCapacity) {
			this.resize(this.valueCapacity + 1);
		}

		this.values[this.valueSize] = (byte) value;
		return this.valueSize++;
	}

	private int findValuePosition(int value) {
		for (int i = 0; i < this.valueSize; i++) {
			if (this.values[i] == value) {
				return i;
			}
		}

		return -1;
	}

	private void resize(int neededCapacity) {
		int nextPotSize = MathExt.nextPOT(neededCapacity + 1);
		int bits = MathExt.nextPOT(MathExt.getLowestBitPosition(nextPotSize));
		int paletteSize = (bits * 4096) / 8;

		byte[] values = new byte[nextPotSize];
		byte[] palette = new byte[paletteSize];

		System.arraycopy(this.values, 0, values, 0, this.valueSize);

		int oldBitsPerValue = this.bitsPerValue;
		int oldValuesPerByte = this.valuesPerByte;
		int oldMaskPerValue = this.maskPerValue;

		byte[] oldPalette = this.palette;

		int newBitsPerValue = bits;
		int newValuesPerByte = 8 / bits;
		int newMaskPerValue = (1 << bits) - 1;

		for (int index = 0; index < 4096; index++) {
			int oldArrayIndex = index / oldValuesPerByte;
			int oldShift = (index & (oldValuesPerByte - 1)) * oldBitsPerValue;

			int oldPaletteValue = (oldPalette[oldArrayIndex] >>> oldShift) & oldMaskPerValue;

			int newArrayIndex = index / newValuesPerByte;
			int newShift = (index & (newValuesPerByte - 1)) * newBitsPerValue;

			palette[newArrayIndex] |= (byte) ((oldPaletteValue & newMaskPerValue) << newShift);
		}

		this.values = values;
		this.palette = palette;

		this.bitsPerValue = newBitsPerValue;
		this.maskPerValue = newMaskPerValue;

		this.valueCapacity = nextPotSize;
		this.valuesPerByte = newValuesPerByte;
	}
}
