package dev.safixo.core.hooks;

import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.core.BlockTransformer;
import dev.safixo.core.KronosTransformer;
import net.minecraft.block.Block;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;


// To handle async meshing, some fields from the Block class are
public class AsyncBlockHook {
	private static Thread MESHING_THREAD;
	private static final long minX, minXMT;
	private static final long minY, minYMT;
	private static final long minZ, minZMT;
	private static final long maxX, maxXMT;
	private static final long maxY, maxYMT;
	private static final long maxZ, maxZMT;

	static {
		try {

			Field minXF = Block.class.getDeclaredField(KronosTransformer.IN_DEV ? "minX" : BlockTransformer.MINX_SRG);
			Field minYF = Block.class.getDeclaredField(KronosTransformer.IN_DEV ? "minY" : BlockTransformer.MINY_SRG);
			Field minZF = Block.class.getDeclaredField(KronosTransformer.IN_DEV ? "minZ" : BlockTransformer.MINZ_SRG);

			Field maxXF = Block.class.getDeclaredField(KronosTransformer.IN_DEV ? "maxX" : BlockTransformer.MAXX_SRG);
			Field maxYF = Block.class.getDeclaredField(KronosTransformer.IN_DEV ? "maxY" : BlockTransformer.MAXY_SRG);
			Field maxZF = Block.class.getDeclaredField(KronosTransformer.IN_DEV ? "maxZ" : BlockTransformer.MAXZ_SRG);

			Field minXFMT = Block.class.getDeclaredField("minXMT");
			Field minYFMT = Block.class.getDeclaredField("minYMT");
			Field minZFMT = Block.class.getDeclaredField("minZMT");

			Field maxXFMT = Block.class.getDeclaredField("maxXMT");
			Field maxYFMT = Block.class.getDeclaredField("maxYMT");
			Field maxZFMT = Block.class.getDeclaredField("maxZMT");

			minX = UnsafeUtil.getFieldOffset(minXF);
			minY = UnsafeUtil.getFieldOffset(minYF);
			minZ = UnsafeUtil.getFieldOffset(minZF);
			maxX = UnsafeUtil.getFieldOffset(maxXF);
			maxY = UnsafeUtil.getFieldOffset(maxYF);
			maxZ = UnsafeUtil.getFieldOffset(maxZF);

			minXMT = UnsafeUtil.getFieldOffset(minXFMT);
			minYMT = UnsafeUtil.getFieldOffset(minYFMT);
			minZMT = UnsafeUtil.getFieldOffset(minZFMT);
			maxXMT = UnsafeUtil.getFieldOffset(maxXFMT);
			maxYMT = UnsafeUtil.getFieldOffset(maxYFMT);
			maxZMT = UnsafeUtil.getFieldOffset(maxZFMT);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	public static void setupAsyncBounds(Block block) {
		UnsafeUtil.UNSAFE.putDouble(block, minXMT, block.getBlockBoundsMinX());
		UnsafeUtil.UNSAFE.putDouble(block, minYMT, block.getBlockBoundsMinY());
		UnsafeUtil.UNSAFE.putDouble(block, minZMT, block.getBlockBoundsMinZ());

		UnsafeUtil.UNSAFE.putDouble(block, maxXMT, block.getBlockBoundsMaxX());
		UnsafeUtil.UNSAFE.putDouble(block, maxYMT, block.getBlockBoundsMaxY());
		UnsafeUtil.UNSAFE.putDouble(block, maxZMT, block.getBlockBoundsMaxZ());
	}

	// SETTERS
	public static void setMinX(Block block, double x) {
		UnsafeUtil.UNSAFE.putDouble(block, isAsync() ? minXMT : minX, x);
	}
	public static void setMinY(Block block, double x) {
		UnsafeUtil.UNSAFE.putDouble(block, isAsync() ? minYMT : minY, x);
	}
	public static void setMinZ(Block block, double x) {
		UnsafeUtil.UNSAFE.putDouble(block, isAsync() ? minZMT : minZ, x);
	}
	public static void setMaxX(Block block, double x) {
		UnsafeUtil.UNSAFE.putDouble(block, isAsync() ? maxXMT : maxX, x);
	}
	public static void setMaxY(Block block, double x) {
		UnsafeUtil.UNSAFE.putDouble(block, isAsync() ? maxYMT : maxY, x);
	}
	public static void setMaxZ(Block block, double x) {
		UnsafeUtil.UNSAFE.putDouble(block, isAsync() ? maxZMT : maxZ, x);
	}

	// GETTERS
	public static double getMinX(Block block) {
		return UnsafeUtil.UNSAFE.getDouble(block, isAsync() ? minXMT : minX);
	}
	public static double getMinY(Block block) {
		return UnsafeUtil.UNSAFE.getDouble(block, isAsync() ? minYMT : minY);
	}
	public static double getMinZ(Block block) {
		return UnsafeUtil.UNSAFE.getDouble(block, isAsync() ? minZMT : minZ);
	}
	public static double getMaxX(Block block) {
		return UnsafeUtil.UNSAFE.getDouble(block, isAsync() ? maxXMT : maxX);
	}
	public static double getMaxY(Block block) {
		return UnsafeUtil.UNSAFE.getDouble(block, isAsync() ? maxYMT : maxY);
	}
	public static double getMaxZ(Block block) {
		return UnsafeUtil.UNSAFE.getDouble(block, isAsync() ? maxZMT : maxZ);
	}

	public static boolean isAsync() {
		return Thread.currentThread() == MESHING_THREAD;
	}

	public static void setMeshingThread(Thread thread) {
		MESHING_THREAD = thread;
	}
}
