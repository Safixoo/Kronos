package dev.safixo.client.render.pipelines.terrain.sorting;

import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.pipelines.terrain.meshing.model.NormalUtil;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MeshDirection;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.joml.Vector3f;

public class BSPWriter {
	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;

	public static final int IDENTITY_SORT = -1;
	public static final int UNCHANGED_SORT = -2;
	public static final int DEFAULT_SORT = -3;

	private static final float EPSILON = 0.01F;
	public int sectionX, sectionY, sectionZ;

	protected BSPNode root;

	private final float[] currentEye = new float[3];
	private int quadCount;

	public BSPWriter(int sectionX, int sectionY, int sectionZ) {
		this.sectionX = sectionX;
		this.sectionY = sectionY;
		this.sectionZ = sectionZ;
	}

	public BSPReader getReader() {
		return new BSPReader(this);
	}

	public int getQuadCount() {
		return this.quadCount;
	}

	public int getTreeSize() {
		return this.getChildSize(this.root);
	}

	int getChildSize(BSPNode tree) {
		if (tree == null) {
			return 0;
		}

		return this.getChildSize(tree.back) + this.getChildSize(tree.front) + 1;
	}

	public void addQuad(Quad quad) {
		double quadX = 0.0F;
		double quadY = 0.0F;
		double quadZ = 0.0F;

		for (int i = 0; i < 4; i++) {
			quadX += quad.getX(i);
			quadY += quad.getY(i);
			quadZ += quad.getZ(i);
		}

		this.currentEye[0] = (float) (quadX * 0.25);
		this.currentEye[1] = (float) (quadY * 0.25);
		this.currentEye[2] = (float) (quadZ * 0.25);

		this.root = this.insert(this.root, quad, this.currentEye, this.quadCount++);
	}

	private BSPNode insert(BSPNode tree, Quad quad, float[] center, int quadId) {
		if (tree == null) {
			return new BSPNode(center[0], center[1], center[2], quad, quadId);
		}

		int side = this.getSide(tree, center);

		if (side == FRONT) {
			tree.front = this.insert(tree.front, quad, center, quadId);
		} else if (side == BACK) {
			tree.back = this.insert(tree.back, quad, center, quadId);
		} else {
			tree.addQuad(quadId);
		}

		return tree;
	}

	public boolean isAutoSorted() {
		return this.root == null || (this.root.front == null && this.root.back == null);
	}

	protected static final int BACK   = 0;
	protected static final int FRONT  = 1;
	private static final int INSIDE = 2;

	private int getSide(BSPNode node, float[] center) {
		float diff = center[node.arrayDirection] - node.centerComp;

		if (diff > EPSILON) {
			return node.frontValue;
		}

		if (diff < -EPSILON) {
			return 1 - node.frontValue;
		}

		return INSIDE;
	}

	protected static class BSPNode {
		protected BSPNode front, back;

		protected final IntArrayList quads = new IntArrayList();
		protected final int arrayDirection, frontValue;
		protected final float centerComp;

		public BSPNode(float centerX, float centerY, float centerZ, Quad quad, int quadId) {
			int computedEnum = quad.getNormalEnum();

			if (computedEnum == MeshDirection.GENERIC) {
				int computedNormal = quad.getNormalVector();

				float x = NormalUtil.getNormalX(computedNormal);
				float y = NormalUtil.getNormalY(computedNormal);
				float z = NormalUtil.getNormalZ(computedNormal);

				float absX = Math.abs(x);
				float absY = Math.abs(y);
				float absZ = Math.abs(z);

				if (absX > absY && absX > absZ) { // x
					computedEnum = x > 0 ? MeshDirection.XP : MeshDirection.XN;
				} else if (absX > absZ) { // y
					computedEnum = y > 0 ? MeshDirection.YP : MeshDirection.YN;
				} else { // z
					computedEnum = z > 0 ? MeshDirection.ZP : MeshDirection.ZN;
				}
			}

			switch (computedEnum) {
				case MeshDirection.YP: case MeshDirection.YN:
					this.arrayDirection = Y;
					this.centerComp = centerY; break;
				case MeshDirection.XP: case MeshDirection.XN:
					this.arrayDirection = X;
					this.centerComp = centerX; break;
				default:
					this.arrayDirection = Z;
					this.centerComp = centerZ; break;
			}

			if (Direction.isPositive(computedEnum)) {
				this.frontValue = FRONT;
			} else {
				this.frontValue = BACK;
			}

			this.quads.add(quadId);
		}

		private void addQuad(int quadId) {
			this.quads.add(quadId);
		}
	}
}
