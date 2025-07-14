package turniplabs.examplemod.mixins.injects;

import net.minecraft.core.util.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.interfaces.mixin.IBlockAABB;

@Mixin(value = AABB.class, remap = false)
public class AABBMixin implements IBlockAABB {
	@Shadow public double minY;
	@Shadow public double maxY;
	@Shadow public double minZ;
	@Shadow public double maxZ;
	@Shadow public double minX;
	@Shadow public double maxX;

	@Unique
	private final boolean[] boundChecks = new boolean[Direction.COUNT];

	@Override
	public boolean[] blockBoundsCheck() {
		return this.boundChecks;
	}

	@Override
	public void calculateBounds() {
		this.boundChecks[Direction.DOWN] = this.minY > 0.0F;
		this.boundChecks[Direction.UP] = this.maxY < 1.0F;
		this.boundChecks[Direction.NORTH] = this.minZ > 0.0F;
		this.boundChecks[Direction.SOUTH] = this.maxZ < 1.0F;
		this.boundChecks[Direction.WEST] = this.minX > 0.0F;
		this.boundChecks[Direction.EAST] = this.maxX < 1.0F;
	}
}
