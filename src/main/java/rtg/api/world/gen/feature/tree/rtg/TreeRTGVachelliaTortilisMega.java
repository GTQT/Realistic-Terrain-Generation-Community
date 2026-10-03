package rtg.api.world.gen.feature.tree.rtg;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

public class TreeRTGVachelliaTortilisMega extends TreeRTG {

	public TreeRTGVachelliaTortilisMega() {
		this.lowestVariableTrunkProportion = 0.3f;
		this.trunkProportionVariability = 0.2f;
	}

    @Override
    public int furthestLikelyExtension() {
    	return 14;
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
		final BlockPos trunkBase;
		final World world ;// just so I don't have to type it so much
	    SkylightTracker lightTracker;
		
		Generation(World _world, Random _rand, BlockPos _pos) {
			rand = _rand;
			trunkBase = _pos;
			world = _world;
		}
		
		boolean generate() {        
			if (!isGroundValid(world, trunkBase)) {
            return false;
	        }
	        
	       lightTracker = new SkylightTracker(furthestLikelyExtension(),trunkBase,world);
	       lightTracker.tolerableObstruction =4;
	
	        final int x = trunkBase.getX();
	        int y = trunkBase.getY();
	        final int z = trunkBase.getZ();
	
	
	        for (int i = 0; i < trunkSize +1; i++) {
	            placeTrunkBlock(world, new BlockPos(x, y + i, z), generateFlag, lightTracker);
	        }
	        
	        int branchCount = 2 + rand.nextInt(2);
	        // direction in radians
	        float baseDir = (float)(rand.nextFloat()*Math.PI*2.0f);
	        float increment = (float)(Math.PI*2.0f)/branchCount;
	        float variability = increment/4;
	        float direction = baseDir - variability/2;// all the branches have a random add; this is the pre-subtraction to average zero.
	        for (int branchNumber = 0; branchNumber < branchCount; branchNumber ++) {
	        	makeTrunkBranch(direction + rand.nextFloat()*variability,branchCount > 2);
	        	direction += increment;
	        }
	        
		    return true;
		}
		
		void makeTrunkBranch(float direction, boolean wide) {
        	float ascent = crownSize/2 - rand.nextInt(2);
        	if (ascent < 0) ascent = 0;
        	float length = 3 + rand.nextInt(3);
        	
        	if (wide) length += 2;
        	float verticalShift = ascent/length;
        	float effectiveLength = (float)Math.sqrt(length*length +ascent*ascent);
        	BlockPos splitStart = trunkBase.up(trunkSize);
        	RTGTreeBranch branch = new RTGTreeBranch(direction,verticalShift,effectiveLength,1,splitStart);
			while (branch.notDone() ) {
				lightTracker.testPlace(world, branch.moved(), branchBlock, generateFlag);
			}
	        int branchCount = 2 + rand.nextInt(2);
	        // direction in radians
	        float baseDir = (float)(rand.nextFloat()*Math.PI*2.0f);
	        float increment = (float)(Math.PI*2.0f)/branchCount;
	        float variability = increment/4;
	        float branchDirection = baseDir - variability/2;// all the branches have a random add; this is the pre-subtraction to average zero.
	        for (int branchNumber = 0; branchNumber < branchCount; branchNumber ++) {
	        	makeLeafBranch(branchDirection + rand.nextFloat()*variability,branch.location());
	        	branchDirection += increment;
	        }
		}
		
		void makeLeafBranch(float direction, BlockPos branchStart) {
        	float length = 3 + rand.nextInt(3);
        	// determine distance from base
        	float distanceOut;
        	{
        		BranchVector branchHorizontal = new BranchVector(direction,0);
        		BlockPos end = branchHorizontal.reposition(branchStart, length);
        		int xDist = trunkBase.getX() - end.getX();
        		int zDist = trunkBase.getZ() - end.getZ();
        		distanceOut = (float)(Math.sqrt(xDist*xDist + zDist*zDist));
        	}
        	float remainingHeight = trunkSize + crownSize + trunkBase.getY() - branchStart.getY();
        	float ascent = remainingHeight - rand.nextInt(3) - distanceOut/4f;
        	if (ascent < 0) ascent = 0;
        	float verticalShift = ascent/length;
        	float effectiveLength = (float)Math.sqrt(length*length +ascent*ascent);
        	RTGTreeBranch branch = new RTGTreeBranch(direction,verticalShift,effectiveLength,1,branchStart);
			while (branch.notDone() ) {
				lightTracker.testPlace(world, branch.moved(), branchBlock, generateFlag);
			}
			genLeaves(branch.location(),true);
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
	                       placeLeavesBlock(world, new BlockPos(x + i, y + 1, z + j), leavesBlock, generateFlag, lightTracker);
	                    }
	                }
	            }

	            for (i = -bottomRadius; i <= bottomRadius; i++) {
	                for (j = -bottomRadius; j <= bottomRadius; j++) {
	                    if (Math.abs(i) + Math.abs(j) < bottomRadius+2) {
	                        placeLeavesBlock(world, new BlockPos(x + i, y, z + j) ,leavesBlock, generateFlag, lightTracker);
	                    }
	                }
	            }
	        }

	        placeLogBlock(world, new BlockPos(x, y, z), logBlock, generateFlag, lightTracker);
	    }
	}

}