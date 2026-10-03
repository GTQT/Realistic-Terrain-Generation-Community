package rtg.api.util;

import net.minecraft.util.math.BlockPos;

public class BlockCenteredPresence extends BlockCentered {
	
	private boolean [] presence;
	
	public BlockCenteredPresence(int _dist, BlockPos _base) {
		super(_dist,_base);
		presence = new boolean[width*width];
	}
	
	public void setPresent(BlockPos location) {
		presence[index(location)]  = true;
	}
	
	public void setAbsent(BlockPos location) {
		presence[index(location)]  = false;
	}
	
	public final boolean present(BlockPos location) {
		return presence[index(location)];
	}
	
	public final void set(BlockPos location, boolean value) {
		presence[index(location)]  = value;
	}
	
	public void setPresent(BlockPos location, Direction direction) {
		presence[index(location,direction)]  = true;
	}
	
	public void setAbsent(BlockPos location, Direction direction) {
		presence[index(location,direction)]  = false;
	}
	
	public boolean present(BlockPos location, Direction direction) {
		return presence[index(location,direction)];
	}
	
	public void setPresent(BlockPos location, Direction direction, int offset) {
		presence[index(location,direction,offset)]  = true;
	}
	
	public void setAbsent(BlockPos location, Direction direction, int offset) {
		presence[index(location,direction,offset)]  = false;
	}
	
	public boolean present(BlockPos location, Direction direction, int offset) {
		return presence[index(location,direction,offset)];
	}
	
	public void setPresent(BlockPos location, Direction direction, Direction direction2) {
		presence[index(location,direction,direction2)]  = true;
	}
	
	public void setAbsent(BlockPos location, Direction direction, Direction direction2) {
		presence[index(location,direction,direction2)]  = false;
	}
	
	public boolean present(BlockPos location, Direction direction, Direction direction2) {
		return presence[index(location,direction,direction2)];
	}
	
	public void setPresent(BlockPos location, Direction direction, int offset, Direction direction2, int offset2) {
		presence[index(location,direction,offset,direction2,offset2)]  = true;
	}
	
	public void setAbsent(BlockPos location, Direction direction, int offset, Direction direction2, int offset2) {
		presence[index(location,direction,offset,direction2,offset2)]  = false;
	}
	
	public boolean present(BlockPos location, Direction direction, int offset, Direction direction2, int offset2) {
		return presence[index(location,direction,offset,direction2,offset2)];
	}
	
	public void set(BlockPos location, boolean value, Direction direction, int offset, Direction direction2, int offset2) {
		presence[index(location,direction,offset,direction2,offset2)]  = value;
	}

}