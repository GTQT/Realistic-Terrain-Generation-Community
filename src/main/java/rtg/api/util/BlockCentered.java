package rtg.api.util;

import net.minecraft.util.math.BlockPos;

abstract public class BlockCentered {
	
	protected final int distance;
	protected final int width;
	protected final BlockPos base;
	
	public BlockCentered(int _dist, BlockPos _base) {
		distance = _dist;
		base = _base;
		width = distance*2 + 1;
	}
	
	protected final int index(int x, int z) {
		return (x+distance)*width + z+distance;
	}
	
	protected final boolean inRange(BlockPos tested) {
		return (Math.abs(base.getX()-tested.getX())<=distance)&&(Math.abs(base.getZ()-tested.getZ())<=distance);
	}
	
	protected final int index(BlockPos location) {
		return index(location.getX()-base.getX(),location.getZ()-base.getZ());
	}
	
	protected final int index(BlockPos location, Direction direction) {
		return index(location.getX()-base.getX() + direction.xOffset,location.getZ()-base.getZ() + direction.zOffset);
	}
	
	protected final int index(BlockPos location, Direction direction, Direction direction2) {
		return index(location.getX()-base.getX() + direction.xOffset + direction2.xOffset,
				location.getZ()-base.getZ() + direction.zOffset + direction2.zOffset);
	}
	protected final int index(BlockPos location, Direction direction, int howFar) {
		return index(location.getX()-base.getX() + direction.xOffset*howFar,location.getZ()-base.getZ() + direction.zOffset*howFar);
	}
	
	protected final int index(BlockPos location, Direction direction, int howFar, Direction direction2, int howFar2) {
		return index(location.getX()-base.getX() + direction.xOffset*howFar + direction2.xOffset*howFar2,
				location.getZ()-base.getZ() + direction.zOffset*howFar + direction2.zOffset*howFar2);
	}

}