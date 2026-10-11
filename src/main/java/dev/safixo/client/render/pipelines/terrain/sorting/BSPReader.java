package dev.safixo.client.render.pipelines.terrain.sorting;

import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.Arrays;
import java.util.List;

public class BSPReader {
	private static final int FRONT = BSPWriter.FRONT;
	private static final int BACK = BSPWriter.BACK;

	public static final int IDENTITY_SORT = -1;
	public static final int UNCHANGED_SORT = -2;
	public static final int DEFAULT_SORT = -3;

	private static final int MAX_DIAMETER = Math.max(RegionConstants.DIAMETER_X, Math.max(RegionConstants.DIAMETER_Y, RegionConstants.DIAMETER_Z));
	private static final int RADIUS = 16;

	private static final int INTS_PER_NODE = 3;

	private static final int BACK_INDEX  = 0;
	private static final int FRONT_INDEX = 1;
	private static final int PLANE_MASK  = 2;

	static {
		if (BACK != BACK_INDEX) throw new IllegalArgumentException();
		if (FRONT != FRONT_INDEX) throw new IllegalArgumentException();
	}

	public final int sectionX, sectionY, sectionZ;
	private final int quadCount;

	private boolean unchangedSort;
	private int quadListIndex;

	private final int[][] quadData; // nodeIndex -> quadsIds

	// struct st_node {
	//		int backIndex
 	//		int frontIndex
	// 		int planeMask
 	// }
	private final int[] nodes;

	private final int[] currentEye = new int[3];

	private final int[] stack;
	private final int[] quadIndices;

	public BSPReader(BSPWriter writtenTree) {
		int treeSize = writtenTree.getTreeSize();
		List<BSPWriter.BSPNode> nodes = new ReferenceArrayList<>(treeSize);

		this.fillTree(writtenTree.root, nodes);

		this.quadCount = writtenTree.getQuadCount();

		this.sectionX = writtenTree.sectionX;
		this.sectionY = writtenTree.sectionY;
		this.sectionZ = writtenTree.sectionZ;

		this.stack = new int[treeSize];
		this.quadIndices = new int[treeSize];

		Arrays.fill(this.stack, -1);

		int[] nodesFlat = new int[treeSize * INTS_PER_NODE];
		int[][] quadData = new int[treeSize][];

		for (int i = 0; i < treeSize; i++) {
			BSPWriter.BSPNode node = nodes.get(i);

			nodesFlat[i * INTS_PER_NODE + PLANE_MASK] = setupPlaneMask(node);
			nodesFlat[i * INTS_PER_NODE + BACK_INDEX] = -1;
			nodesFlat[i * INTS_PER_NODE + FRONT_INDEX] = -1;

			quadData[i] = node.quads.toIntArray();
		}

		for (int i = 0; i < treeSize; i++) {
			BSPWriter.BSPNode node = nodes.get(i);

			for (int j = 0; j < treeSize; j++) {
				BSPWriter.BSPNode otherNode = nodes.get(j);

				if (otherNode == node.back) {
					nodesFlat[i * INTS_PER_NODE + BACK_INDEX] = j;
				}

				if (otherNode == node.front) {
					nodesFlat[i * INTS_PER_NODE + FRONT_INDEX] = j;
				}
			}
		}

		this.nodes = nodesFlat;
		this.quadData = quadData;
	}

	public static int[] getPlayerEye(float x, float y, float z) {
		x = MathExt.clamp(x, -RADIUS + 0.5F, MAX_DIAMETER + RADIUS - 0.5F);
		y = MathExt.clamp(y, -RADIUS + 0.5F, MAX_DIAMETER + RADIUS - 0.5F);
		z = MathExt.clamp(z, -RADIUS + 0.5F, MAX_DIAMETER + RADIUS - 0.5F);

		return new int[] {
			getUNorm(x),
			getUNorm(y),
			getUNorm(z)
		};
	}

	public int traverseBackFront(int[] eye, IndexBufferWriter indexWriter) {
		int[] stack = this.stack;
		int[] quadIndices = this.quadIndices;

		for (int i = 0; i < 3; i++) {
			this.currentEye[i] = eye[i];
		}

		this.quadListIndex = 0;
		this.traverseBackFront(stack, quadIndices, this.currentEye);

		if (this.unchangedSort) {
			return UNCHANGED_SORT;
		}

		if (this.checkForIdentitySort(quadIndices)) {
			return IDENTITY_SORT;
		}

		indexWriter.ensureQuadCapacity(this.quadCount);

		// There is no more fast-path, simply write the vertices.
		for (int i = 0; i < this.quadListIndex; i++) {
			int[] quads = this.quadData[quadIndices[i]];

			for (int j = 0; j < quads.length; j++) {
				indexWriter.writeQuad(quads[j]);
			}
		}

		Arrays.fill(this.stack, -1);

		this.unchangedSort = true;
		return DEFAULT_SORT;
	}

	private boolean checkForIdentitySort(int[] quadIndices) {
		int prefix = 0;

		for (int i = 0; i < this.quadListIndex; i++) {
			int[] quads = this.quadData[quadIndices[i]];
			int quadCount = quads.length;

			if (prefix != quads[0] || prefix + quadCount - 1 != quads[quads.length - 1]) {
				return false;
			}

			prefix += quadCount;
		}

		return true;
	}

	private void traverseBackFront(int[] stack, int[] quadsIndices, int[] eye) {
		int currIndex = 0;
		int stackTop = 0;

		while (currIndex != -1 || stackTop > 0) {
			while (currIndex != -1) {
				int planeIndex = currIndex * INTS_PER_NODE + PLANE_MASK;
				int planeMask = this.nodes[planeIndex];

				int lastSide = getLastSide(planeMask);
				int side = this.getSide(planeMask, eye);

				if (this.unchangedSort && side != lastSide) {
					this.nodes[planeIndex] = setLastSide(planeMask, side);
					this.unchangedSort = false;
				}

				stack[stackTop++] = currIndex;
				currIndex = this.nodes[currIndex * INTS_PER_NODE + (1 - side)];
			}

			currIndex = stack[--stackTop];
			quadsIndices[this.quadListIndex++] = currIndex;

			int planeMask = this.nodes[currIndex * INTS_PER_NODE + PLANE_MASK];
			currIndex = this.nodes[currIndex * INTS_PER_NODE + this.getSide(planeMask, eye)];
		}
	}

	private void fillTree(BSPWriter.BSPNode node, List<BSPWriter.BSPNode> nodes) {
		if (node == null) {
			return;
		}

		nodes.add(node);

		this.fillTree(node.front, nodes);
		this.fillTree(node.back, nodes);
	}

	private int getSide(int planeMask, int[] eye) {
		int diff = eye[getArrayDirection(planeMask)] - getCenterComp(planeMask);
		int frontValue = getFrontValue(planeMask);

		return diff > 0 ? frontValue : 1 - frontValue;
	}

	// Array direction: {0, 1, 2}
	// Front value: {0, 1}
	// Center comp: [0, 512]
	private static int setupPlaneMask(float centerComp, int arrayDirection, int frontValue, int lastSide) {
		int centerCompNorm = getUNorm(centerComp);
		return lastSide << 24 | centerCompNorm << 4 | arrayDirection << 1 | frontValue;
	}

	private static int setupPlaneMask(BSPWriter.BSPNode node) {
		return setupPlaneMask(node.centerComp, node.arrayDirection, node.frontValue, 0xFF);
	}

	private static int setLastSide(int planeMask, int lastSide) {
		return (planeMask & 0x00FFFFFF) | lastSide << 24;
	}

	private static int getCenterComp(int planeMask) {
		return (planeMask >>> 4) & 0xFFFFF;
	}

	private static int getLastSide(int planeMask) {
		return (planeMask >>> 24);
	}

	private static int getArrayDirection(int planeMask) {
		return (planeMask >>> 1) & 0b11;
	}

	private static int getFrontValue(int planeMask) {
		return planeMask & 0b1;
	}

	private static int getUNorm(float a) {
		return (int) (((a + RADIUS) / (MAX_DIAMETER + RADIUS * 2)) * (1 << 19));
	}
}
