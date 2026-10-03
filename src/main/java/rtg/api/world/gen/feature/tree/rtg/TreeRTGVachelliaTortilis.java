package rtg.api.world.gen.feature.tree.rtg;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

public class TreeRTGVachelliaTortilis extends TreeRTG {

	public TreeRTGVachelliaTortilis() {
		this.lowestVariableTrunkProportion = 0.5f;
		this.trunkProportionVariability = 0.2f;
	}

    @Override
    public int furthestLikelyExtension() {
    	return 8;
    }
    
    @Override
    public float estimatedSize() {
    	return super.estimatedSize()/2f;
    }

	@Override
	public boolean generate(World worldIn, Random rand, BlockPos position) {
		return new Generation(worldIn,rand,position).generate();
	}

	private class Generation {
		final Random rand;
		final BlockPos start;
		final World world ;// just so I don't have to type it so much
	    SkylightTracker lightTracker;
		
		Generation(World _world, Random _rand, BlockPos _pos) {
			rand = _rand;
			start = _pos;
			world = _world;
		}
		
		boolean generate() {        
			if (!isGroundValid(world, start)) {
            return false;
	        }
	        
	       lightTracker = new SkylightTracker(furthestLikelyExtension(),start,world);
	       lightTracker.tolerableObstruction =4;
	
	        final int x = start.getX();
	        int y = start.getY();
	        final int z = start.getZ();
	
	
	        for (int i = 0; i < trunkSize +1; i++) {
	            placeTrunkBlock(world, new BlockPos(x, y + i, z), generateFlag, lightTracker);
	        }
	        
	        int branchCount = 3 + rand.nextInt(2);
	        // direction in radians
	        float baseDir = (float)(rand.nextFloat()*Math.PI*2.0f);
	        float increment = (float)(Math.PI*2.0f)/branchCount;
	        float variability = increment/4;
	        float direction = baseDir - variability/2;// all the branches have a random add; this is the pre-subtraction to average zero.
	        for (int branchNumber = 0; branchNumber < branchCount; branchNumber ++) {
	        	makeBranch(direction + rand.nextFloat()*variability, branchNumber ==0);
	        	direction += increment;
	        }
	        
		    return true;
		}
		
		void makeBranch(float direction,boolean backBranch) {
        	float ascent = crownSize - 1 - rand.nextInt(2);
        	if (ascent < 0) ascent = 0;
        	float length = 4 + rand.nextInt(3);
        	float verticalShift = ascent/length;
        	float effectiveLength = (float)Math.sqrt(length*length +ascent*ascent);
        	BlockPos splitStart = start.up(trunkSize);
        	RTGTreeBranch branch = new RTGTreeBranch(direction,verticalShift,effectiveLength,1,splitStart);
			while (branch.notDone() ) {
				lightTracker.testPlace(world, branch.moved(), branchBlock, generateFlag);
			}
			genLeaves(branch.location(),true);
		    if (backBranch) {
		    	int newX = (splitStart.getX() + branch.location().getX())/2;
		    	int newY = (splitStart.getY() + branch.location().getY())/2;
		    	int newZ = (splitStart.getZ() + branch.location().getZ())/2;
		    	float topAscent = splitStart.getY() + crownSize - newY + 1;
	        	float topLength = (float)Math.sqrt(length*length/4f +topAscent*topAscent/2f);
	        	RTGTreeBranch topBranch = new RTGTreeBranch(
	        			direction+Math.PI - .2f+ rand.nextFloat()*0.4f,
	        			topAscent/topLength,
	        			topLength,
	        			1,
	        			new BlockPos(newX,newY,newZ));
				while (topBranch.notDone() ) {
					lightTracker.testPlace(world, topBranch.moved(), branchBlock, generateFlag);
				}
				genLeaves(topBranch.location(),true);
		    }
		}
		
		void genLeaves(BlockPos pos, boolean bigger) {
			genLeaves(pos.getX(),pos.getY(),pos.getZ(),bigger);
		}
		
	    void genLeaves(int x, int y, int z, boolean bigger) {

	        if (!noLeaves) {
	        	
	        	int topRadius = 1;
	        	int bottomRadius = 2;
	        	
	        	if (bigger) {
	        		topRadius ++;
	        		bottomRadius ++;
	        	}
	        	
	        	
	            int i;
	            int j;
	            for (i = -topRadius; i <= topRadius; i++) {
	                for (j = -topRadius; j <= topRadius; j++) {
	                    if (Math.abs(i) + Math.abs(j) < topRadius+2) {
	                       placeLeavesBlock(world, new BlockPos(x + i, y + 1, z + j),leavesBlock, generateFlag, lightTracker);
	                    }
	                }
	            }

	            for (i = -bottomRadius; i <= bottomRadius; i++) {
	                for (j = -bottomRadius; j <= bottomRadius; j++) {
	                    if (Math.abs(i) + Math.abs(j) < bottomRadius+2) {
	                        placeLeavesBlock(world, new BlockPos(x + i, y, z + j), leavesBlock, generateFlag,  lightTracker);
	                    }
	                }
	            }
	        }

	        placeLogBlock(world, new BlockPos(x, y, z), logBlock, generateFlag, lightTracker);
	    }
	}
	

}