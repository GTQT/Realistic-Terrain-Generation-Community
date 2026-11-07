package rtg.api.world.gen.feature.tree.rtg;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * 树木放置前的天光可行性跟踪器：从树基向上扫描遮挡，判断哪些位置仍能接到天光。
 *
 * <p>所有数组都以树基为中心，索引是相对坐标；扫描必须先确定树基附近真正能透光的
 * 高度，再向外扩散。A5′ 的块存储直读路径见 {@link #scanColumn}。
 */
public class SkylightTracker {
	
	private final int distance;
	private final int width;
	private final int [] [] directOpacity; // 正上方直接遮挡
	private final int [] [] opacity; // 当前可用的最亮光照
	private final int [] [] lowest; // 受影响的最低方块
	private final OrthogonalDirection [] [] lightDirection; // 最亮光照来自哪个方向

	private final boolean [] [] relighting; // 正在重新计算的位置，避免重复尝试
	private final OrthogonalDirection [] [] backTrack; // 一次放置尝试中已经重算过的位置
	
	private final boolean [] [] trunk; // 树干：暗块一直落到地面，光无法从下方穿过
	private final BlockPos base;

	int tolerableObstruction = 5;
	private int hopelesslyDark = 15;
	
	private int lightingHeight = 50; // check this many blocks up for light obstructions
	
	
	public SkylightTracker( int distance, BlockPos base, World world) {
		
		// add on some distance for tracking 
		distance += 4;
		this.distance  = distance;
		this.base = base;
		width = distance * 2 + 1;// this creates a square array for all columns up to distance in either direction;
		opacity = new int [width][width];
		lowest = new int [width][width];
		
		directOpacity = new int [width][width];
		lightDirection = new OrthogonalDirection [width][width];
		relighting = new boolean [width] [width];
		backTrack = new OrthogonalDirection [width][width];
		trunk = new boolean [width] [width];
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				lightDirection [x][z] = OrthogonalDirection.CENTER;
				backTrack [x][z] = OrthogonalDirection.CENTER;
				lowest [x][z] = 255;
			}
		}
		ChunkGenerationProfiler.count(Counter.SKYLIGHT_TRACKERS);
		setLighting(world);
		initialSpread();
	}
	public SkylightTracker( int distance, BlockPos base, World world, int tolerableObstruction) {
		
		this(distance,base,world);
		this.tolerableObstruction = tolerableObstruction;
	}
	
	private final class Spotlight {
		
		int x;
		int z;
		boolean givingUp;
		int passedOpacity;
		int worstOpacity = 0;
		int totalBackTracks = 0;
		boolean interesting = false;
		
		Spotlight() {}
		
		Spotlight(int x, int z) {
			this();
			set(x,z);
		}	
		
		Spotlight(BlockPos location) {

			x = location.getX() - base.getX() + distance;
			z = location.getZ() - base.getZ() + distance;
		}
		
		void set(int x, int z) {
			this.x = x;
			this.z = z;
		}
		
		boolean legal() {return (x>0)&&(x<width)&&(z>0)&&(z<width);}
		
		int opacity() {return opacity [x][z];}
		
		void setOpacity(int newLighting) {
			opacity [x][z] = newLighting;
	    }
		
		int directOpacity() {return directOpacity [x][z];}
		
		boolean relighting() {return relighting [x][z];}
		
		void setRelighting(boolean state) {relighting [x][z] = state;}
		
		OrthogonalDirection direction() {return lightDirection [x][z];}
		
		OrthogonalDirection backTrack() {return backTrack [x][z];}
		
		void setBackTrack(OrthogonalDirection comingFrom) {
			backTrack [x][z] = comingFrom;
			totalBackTracks++;
		}		
		
		void clearBackTrack() {
			backTrack [x][z] = OrthogonalDirection.CENTER;
			totalBackTracks--;
		}
		
		void setDirection(OrthogonalDirection newDirection) {
			lightDirection [x][z] = newDirection;
		}
		
		boolean trunk() {return trunk [x] [z];}
		
		void setTrunk() {
			trunk [x] [z] = true;
			}		
		void setTrunk(boolean value) {
			trunk [x] [z] = value;
		}
		
		final void move(OrthogonalDirection direction) {
			switch(direction)  {
			case XDOWN: {
				x--; break;
			    }
			case ZDOWN: {
				z--; break;
		        }
			case XUP: {
				x++; break;
		        }
		    case ZUP: {
			    z++; break;
	        }
	        }
		}
		
		final void darken(OrthogonalDirection comingFrom) {
			this.setRelighting(true);
			this.setBackTrack(comingFrom);
			setOpacity(directOpacity());
			if (opacity()>passedOpacity) setOpacity(passedOpacity);
			
			// darken everything around that is getting light from this position and not already being relit
			move(OrthogonalDirection.XDOWN);
			if (legal()&&!relighting()&&direction()==OrthogonalDirection.XUP&&!trunk()) {
				// need to relight
				this.passedOpacity ++;
				darken(OrthogonalDirection.XUP);
				passedOpacity --;
			}
			move(OrthogonalDirection.XUP);
			
			move(OrthogonalDirection.ZDOWN);
			if (legal()&&!relighting()&&direction()==OrthogonalDirection.ZUP&&!trunk()) {
				// need to relight
				this.passedOpacity ++;
				darken(OrthogonalDirection.ZUP);
				passedOpacity --;
			}
			move(OrthogonalDirection.ZUP);
			
			move(OrthogonalDirection.XUP);
			if (legal()&&!relighting()&&direction()==OrthogonalDirection.XDOWN&&!trunk()) {
				// need to relight
				this.passedOpacity ++;
				darken(OrthogonalDirection.XDOWN);
				passedOpacity --;
			}
			move(OrthogonalDirection.XDOWN);
			
			move(OrthogonalDirection.ZUP);
			if (legal()&&!relighting()&&direction()==OrthogonalDirection.ZDOWN&&!trunk()) {
				// need to relight
				this.passedOpacity ++;
				darken(OrthogonalDirection.ZDOWN);
				passedOpacity --;
			}
			move(OrthogonalDirection.ZDOWN);
			
		}
		
		final void relight(OrthogonalDirection comingFrom) {

			// relight everything around that was getting light from this position and not already being relit
			move(OrthogonalDirection.XDOWN);
			if (legal()&&relighting()&&direction()==OrthogonalDirection.XUP) {
				// need to relight
				this.passedOpacity ++;
				relight(OrthogonalDirection.XUP);
				passedOpacity --;
			}
			move(OrthogonalDirection.XUP);
			
			move(OrthogonalDirection.ZDOWN);
			if (legal()&&relighting()&&direction()==OrthogonalDirection.ZUP) {
				// need to relight
				this.passedOpacity ++;
				relight(OrthogonalDirection.ZUP);
				passedOpacity --;
			}
			move(OrthogonalDirection.ZUP);
			
			move(OrthogonalDirection.XUP);
			if (legal()&&relighting()&&direction()==OrthogonalDirection.XDOWN) {
				// need to relight
				this.passedOpacity ++;
				relight(OrthogonalDirection.XDOWN);
				passedOpacity --;
			}
			move(OrthogonalDirection.XDOWN);
			
			move(OrthogonalDirection.ZUP);
			if (legal()&&relighting()&&direction()==OrthogonalDirection.ZDOWN) {
				// need to relight
				this.passedOpacity ++;
				relight(OrthogonalDirection.ZDOWN);
				passedOpacity --;
			}
			move(OrthogonalDirection.ZDOWN);
			// spread from adjacent areas
			this.setRelighting(false);
			
			spreadFrom(OrthogonalDirection.XUP);
			spreadFrom(OrthogonalDirection.ZUP);
			spreadFrom(OrthogonalDirection.XDOWN);
			spreadFrom(OrthogonalDirection.ZDOWN);
			spread(this);
			
			if (opacity() >= directOpacity()) {
				this.setDirection(OrthogonalDirection.CENTER);
				this.setOpacity(directOpacity());
			} 
	        if (opacity() >= passedOpacity) {
				this.setDirection(comingFrom);
				this.setOpacity(passedOpacity);
			}
	        
			
			
		}
		private void spreadFrom(OrthogonalDirection tested) {
			move(tested);
			if (legal()) {
				spread(this);
			}
			move(tested.opposite());
		}
		
		void setWorstOpacity(int newValue) {
			worstOpacity = newValue;
		}
		
		void checkBacktrackedOpacity() {
			checkOpacityTowards(OrthogonalDirection.XUP);
			checkOpacityTowards(OrthogonalDirection.ZUP);
			checkOpacityTowards(OrthogonalDirection.XDOWN);
			checkOpacityTowards(OrthogonalDirection.ZDOWN);
			if (!trunk()) { // for a trunk dark is acceptable since spawning is prevented physically.
				if (this.opacity()>worstOpacity) setWorstOpacity(opacity());
			}
			
		}
		
		void checkOpacityTowards(OrthogonalDirection towards) {
			move(towards);
			if (legal()) {
			    if (backTrack()==towards.opposite()) {
				    checkBacktrackedOpacity();
				    // clear backTrack
				    clearBackTrack();
			    } else {
			    	if (backTrack() != OrthogonalDirection.CENTER) {
			    	}
			    }
			}
			move(towards.opposite());
		}
		

		private void chooseLight() {
			int bestLight = this.directOpacity();
			OrthogonalDirection  bestDirection = OrthogonalDirection.CENTER;
			move(OrthogonalDirection.XUP);
			if (this.opacity()+1< bestLight) {
				bestLight = this.opacity()+1;
				bestDirection = OrthogonalDirection.XUP;
			}
			move(OrthogonalDirection.XDOWN);
			
			move(OrthogonalDirection.XDOWN);
			if (this.opacity()+1< bestLight) {
				bestLight = this.opacity()+1;
				bestDirection = OrthogonalDirection.XDOWN;
			}
			move(OrthogonalDirection.XUP);
			
			move(OrthogonalDirection.ZUP);
			if (this.opacity()+1< bestLight) {
				bestLight = this.opacity()+1;
				bestDirection = OrthogonalDirection.ZUP;
			}
			move(OrthogonalDirection.ZDOWN);
			move(OrthogonalDirection.ZDOWN);
			if (this.opacity()+1< bestLight) {
				bestLight = this.opacity()+1;
				bestDirection = OrthogonalDirection.ZDOWN;
			}
			move(OrthogonalDirection.ZUP);
		}
		
		public String toString() {
			if (legal()) {
			return "" + x + ','  +  z + " relighting " + relighting() + " opacity " + opacity() + " direct "+ directOpacity() + " trunk " + trunk () + '\r';
			} else return "Out of bounds";
			
		}
	}
	
	public static boolean isLog(IBlockState blockState) {
		if (blockState.getBlock() == Blocks.LOG) return true;
		if (blockState.getBlock() == Blocks.LOG2) return true;
		return false;
	}
	
	// ==================================================================
	// A5′：直接读 chunk section 的列扫描
	// ==================================================================

	interface ColumnAccess {
		IBlockState get(int y);

		boolean isEmptySection(int y);

		int sectionTop(int y);
	}

	static final class ColumnScan {
		int obstruction;
		boolean trunk;
		int reads;
		int emptySections;

		void reset() {
			obstruction = 0;
			trunk = false;
			reads = 0;
			emptySections = 0;
		}
	}

	private static final class ChunkColumnAccess implements ColumnAccess {
		private Chunk chunk;
		private ExtendedBlockStorage[] sections;
		private boolean debug;
		private int localX;
		private int localZ;

		void set(Chunk chunk, boolean debug, int localX, int localZ) {
			this.chunk = chunk;
			this.debug = debug;
			this.localX = localX;
			this.localZ = localZ;
			this.sections = debug ? null : chunk.getBlockStorageArray();
		}

		@Override
		public IBlockState get(int y) {
			if (debug) {
				return chunk.getBlockState(localX, y, localZ);
			}
			ExtendedBlockStorage section = sections[y >> 4];
			if (section == null || section.isEmpty()) {
				return Blocks.AIR.getDefaultState();
			}
			IBlockState state = section.get(localX, y & 15, localZ);
			return state == null ? Blocks.AIR.getDefaultState() : state;
		}

		@Override
		public boolean isEmptySection(int y) {
			if (debug) return false;
			ExtendedBlockStorage section = sections[y >> 4];
			return section == null || section.isEmpty();
		}

		@Override
		public int sectionTop(int y) {
			return ((y >> 4) + 1) << 4;
		}
	}

	/**
	 * 未加载区块的兜底列访问：保持 {@code World#getBlockState} 的加载/越界语义。
	 * <p>正常树生成不应走到这里；它是“邻区块恰好未加载”时的安全路径，不参与快路径。
	 */
	private static final class WorldColumnAccess implements ColumnAccess {
		private World world;
		private int x;
		private int z;

		void set(World world, int x, int z) {
			this.world = world;
			this.x = x;
			this.z = z;
		}

		@Override
		public IBlockState get(int y) {
			return world.getBlockState(new BlockPos(x, y, z));
		}

		@Override
		public boolean isEmptySection(int y) {
			return false;
		}

		@Override
		public int sectionTop(int y) {
			return y + 1;
		}
	}

	/** checkLighting 只需要知道“该在哪个坐标调用 checkLight”，便于合成测试。 */
	interface LightCheckSink {
		void check(int x, int y, int z);
	}

	/**
	 * 扫描一列 baseY..baseY+lightingHeight-1。语义与旧的逐格 world.getBlockState 完全一致，
	 * 唯一差别是第二段可以用 {@link ExtendedBlockStorage#isEmpty()} 整段跳过。
	 *
	 * <p>第一段不能在空段处跳过：它只处理底部的连续不透明块，遇到第一个非不透明块就结束。
	 * 跳段会把空段上方的原木/实心块也算进来，改变 trunk 与 obstruction。
	 *
	 * <p>注意 {@code baseY} 是树基位置，旧实现从 {@code base.up(up)} 开始扫描；
	 * 这里必须保持绝对 Y，否则会把扫描范围错移到世界底部。
	 */
	static void scanColumn(ColumnAccess access, int baseY, int lightingHeight, ColumnScan out) {
		out.reset();
		final int startY = Math.max(0, baseY);
		final int endY = Math.min(256, startY + lightingHeight);
		int up = startY;

		// 第一段：跳过底部连续的不透明块；遇到空气/空段必须停止。
		while (up < endY) {
			if (access.isEmptySection(up)) {
				break;
			}
			IBlockState examined = access.get(up);
			out.reads++;
			if (!examined.isOpaqueCube()) {
				break;
			}
			if (isLog(examined) && (up - startY) > 5) {
				out.obstruction = 255;
				out.trunk = true;
				return;
			}
			up++;
		}

		// 第二段：空气/水/非实心方块；整段空气可以安全跳过。
		while (up < endY) {
			if (access.isEmptySection(up)) {
				int next = access.sectionTop(up);
				if (next <= up) {
					break;
				}
				out.emptySections++;
				up = next;
				continue;
			}
			IBlockState examined = access.get(up);
			out.reads++;
			if (examined.isOpaqueCube()) {
				out.obstruction = 255;
				return;
			}
			// ignore water (same as the old scan)
			if (examined.getBlock() == Blocks.WATER) {
				examined = Blocks.AIR.getDefaultState();
			}
			out.obstruction += examined.getLightOpacity();
			if (out.obstruction > 1) {
				out.obstruction = 255;
				return;
			}
			up++;
		}
	}

	private void setLighting(World world) {
		final boolean debug = world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES;
		final int baseCX = base.getX() >> 4;
		final int baseCZ = base.getZ() >> 4;
		final Chunk[] chunkCache = new Chunk[25];
		final ChunkColumnAccess access = new ChunkColumnAccess();
		final ColumnScan scan = new ColumnScan();

		// 旧实现的 isBlockLoaded(columnBase) 会拒绝越界 Y；这里保持一致，
		// 否则负 baseY 会被 clamp 到 0 后扫出本不存在的遮挡。
		if (base.getY() < 0 || base.getY() >= 256) {
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < width; z++) {
					this.directOpacity[x][z] = 0;
				}
			}
			return;
		}

		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				final int worldX = base.getX() - distance + x;
				final int worldZ = base.getZ() - distance + z;
				final int chunkX = worldX >> 4;
				final int chunkZ = worldZ >> 4;
				final int dx = chunkX - baseCX + 2;
				final int dz = chunkZ - baseCZ + 2;

				Chunk chunk;
				if (dx >= 0 && dx < 5 && dz >= 0 && dz < 5) {
					final int cacheIndex = dz * 5 + dx;
					chunk = chunkCache[cacheIndex];
					if (chunk == null) {
						chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
						if (chunk != null) {
							chunkCache[cacheIndex] = chunk;
						}
					}
				} else {
					chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
				}

				if (chunk == null) {
					// 未加载：保持旧行为“无遮挡”。
					this.directOpacity[x][z] = 0;
					continue;
				}

				access.set(chunk, debug, worldX & 15, worldZ & 15);
				scanColumn(access, base.getY(), this.lightingHeight, scan);
				this.directOpacity[x][z] = scan.obstruction;
				if (scan.trunk) {
					this.trunk[x][z] = true;
				}

				ChunkGenerationProfiler.count(Counter.SKYLIGHT_COLUMNS);
				ChunkGenerationProfiler.add(Counter.SKYLIGHT_STATE_READS, scan.reads);
				ChunkGenerationProfiler.add(Counter.SKYLIGHT_EMPTY_SECTIONS, scan.emptySections);
			}
		}
	}

	private void initialSpread() {
		// start with direct lighting;
		boolean anyObstruction = false;
		for (int x = 0; x < width; x ++) {
			for (int z = 0; z < width; z ++) {
				int direct = directOpacity [x][z];
				opacity [x][z] = direct;
				if (direct != 0) {
					anyObstruction = true;
				}
			}
		}
		// A5 全亮快路径：所有列都没有遮挡时，spread 的每一格都以 opacity=0、
		// dimmed=1 进入，任何邻居都不满足 opacity > dimmed，整轮递归是恒等变换。
		// 这里直接跳过 width² 次调用；opacity/lightDirection 已处于构造时的全 0/CENTER。
		if (!anyObstruction) {
			ChunkGenerationProfiler.count(Counter.SKYLIGHT_BRIGHT_SKIPS);
			return;
		}
		Spotlight spotlight = new Spotlight();
		for (int x = 0; x < width; x ++) {
			for (int z = 0; z < width; z ++) {
				spotlight.set(x,z);
				spread(spotlight);
			}
		}
	}
	
	private void spread (Spotlight spotlight) {
		ChunkGenerationProfiler.count(Counter.SKYLIGHT_SPREAD_VISITS);
		if (spotlight.opacity() > spotlight.directOpacity()) {
			spotlight.setOpacity(spotlight.directOpacity());
			spotlight.setDirection(OrthogonalDirection.CENTER);
		}
		int spotLighting = spotlight.opacity();
		int oldX = spotlight.x;
		int oldZ = spotlight.z;
		int dimmed = spotLighting +1;
		if (dimmed>15) return; // too dark to make a difference.
		// check x down
		spotlight.x--;
		if (spotlight.legal()&&!spotlight.relighting()&&!spotlight.trunk()) {
			if (spotlight.opacity()>dimmed) {// higher numbers are darker
				spotlight.setOpacity(dimmed);
				spotlight.setDirection(OrthogonalDirection.XUP);// opposite because it points to where the lighting came from
				spread(spotlight);
			}
		}
		spotlight.x++;
		// check z down
		spotlight.z--;
		if (spotlight.legal()&&!spotlight.relighting()&&!spotlight.trunk()) {
			if (spotlight.opacity()>dimmed) {// higher numbers are darker
				spotlight.setOpacity(dimmed);
				spotlight.setDirection(OrthogonalDirection.ZUP);// opposite because it points to where the lighting came from
				spread(spotlight);
			}
		}
		spotlight.z++;
		

		// check x up
		spotlight.x++;
		if (spotlight.legal()&&!spotlight.relighting()&&!spotlight.trunk()) {
			if (spotlight.opacity()>dimmed) {// higher numbers are darker
				spotlight.setOpacity(dimmed);
				spotlight.setDirection(OrthogonalDirection.XDOWN);// opposite because it points to where the lighting came from
				spread(spotlight);
			}
		}
		spotlight.x--;
		// check z up
		spotlight.z++;
		if (spotlight.legal()&&!spotlight.relighting()&&!spotlight.trunk()) {
			if (spotlight.opacity()>dimmed) {// higher numbers are darker
				spotlight.setOpacity(dimmed);
				spotlight.setDirection(OrthogonalDirection.ZDOWN);// opposite because it points to where the lighting came from
				spread(spotlight);
			}
		}
		spotlight.z--;
			
		// debugging check
		if (oldX != spotlight.x) throw new RuntimeException();
		if (oldZ != spotlight.z) throw new RuntimeException();
		
	}
	
	boolean testPlace (World world, BlockPos location, IBlockState placed, int generateFlags) {
		int baseX = location.getX() - base.getX() + distance;
		int baseZ = location.getZ() - base.getZ() + distance;
		Spotlight spotlight = new Spotlight(baseX,baseZ);
		if (!inArray(baseX,baseZ)) return true;// if outside of examined area ignore.
		// make the change temporarily to test. May need to be undone before exiting procedure;
		int oldDirectOpacity = directOpacity [baseX][baseZ] ;
		// for now treat everything as opaque because of lighting rules
		directOpacity [baseX][baseZ] += 15;//placed.getLightOpacity();
		// max 255
		if (directOpacity [baseX][baseZ] >255)   directOpacity [baseX][baseZ] = 255;
		if (this.lightDirection [baseX][baseZ] != OrthogonalDirection.CENTER&&!spotlight.trunk()) {
			// this spot is lit from elsewhere, and will continue to be, so direct sky light doesn't matter
			world.setBlockState(location, placed, generateFlags);
			if (lowest[baseX][baseZ]>location.getY()) lowest[baseX][baseZ] =location.getY();
			return true;
		}
		// have to deal with interactions;
		spotlight.passedOpacity = spotlight.directOpacity();
		spotlight.setOpacity(spotlight.directOpacity());
		spotlight.darken(OrthogonalDirection.CENTER);// important: must run a relight check if we have changed this to trunk
		if ((spotlight.x != baseX)||(spotlight.z != baseZ)) throw new RuntimeException();  // testing the returns were right.
		spotlight.relight(OrthogonalDirection.CENTER);
		if ((spotlight.x != baseX)||(spotlight.z != baseZ)) throw new RuntimeException();  // testing the returns were right.
		
		// now find worst opacity in the relit area, clearing backtrack
		spotlight.worstOpacity = 0;
		spotlight.checkBacktrackedOpacity();
		spotlight.clearBackTrack();
		if (spotlight.worstOpacity <= this.tolerableObstruction) {
			//good to go!			
			world.setBlockState(location, placed, generateFlags);
			if (lowest[baseX][baseZ]>location.getY()) lowest[baseX][baseZ] =location.getY();
			return true;
		}
		// too dark
		directOpacity [baseX][baseZ] =oldDirectOpacity;
		lightDirection [baseX][baseZ] = OrthogonalDirection.CENTER;// had to be to try relighting.
		// have to relight
	    this.spread(spotlight);
		return false;
	}
	
	
	boolean debugTestPlace (World world, BlockPos location, IBlockState placed, int generateFlags) {
		int baseX = location.getX() - base.getX() + distance;
		int baseZ = location.getZ() - base.getZ() + distance;
		Spotlight spotlight = new Spotlight(baseX,baseZ);
		if (!inArray(baseX,baseZ)) return true;// if outside of examined area ignore.
		// make the change temporarily to test. May need to be undone before exiting procedure;
		int oldDirectOpacity = directOpacity [baseX][baseZ] ;
		directOpacity [baseX][baseZ] += placed.getLightOpacity();
		// max 255
		if (directOpacity [baseX][baseZ] >255)   directOpacity [baseX][baseZ] = 255;
		if (this.lightDirection [baseX][baseZ] != OrthogonalDirection.CENTER&&!spotlight.trunk()) {
			// this spot is lit from elsewhere, and will continue to be, so direct sky light doesn't matter
			world.setBlockState(location, placed, generateFlags);
			if (lowest[baseX][baseZ]>location.getY()) lowest[baseX][baseZ] =location.getY();
			return true;
		}
		// have to deal with interactions;
		spotlight.passedOpacity = spotlight.directOpacity();
		spotlight.setOpacity(spotlight.directOpacity());
		spotlight.darken(OrthogonalDirection.CENTER);// important: must run a relight check if we have changed this to trunk
		if ((spotlight.x != baseX)||(spotlight.z != baseZ)) throw new RuntimeException();  // testing the returns were right.
		spotlight.relight(OrthogonalDirection.CENTER);
		if ((spotlight.x != baseX)||(spotlight.z != baseZ)) throw new RuntimeException();  // testing the returns were right.
		
		// now find worst opacity in the relit area, clearing backtrack
		spotlight.worstOpacity = 0;
		spotlight.checkBacktrackedOpacity();
		spotlight.clearBackTrack();
		if (spotlight.worstOpacity <= this.tolerableObstruction) {
			//good to go!			
			world.setBlockState(location, placed, generateFlags);
			if (lowest[baseX][baseZ]>location.getY()) lowest[baseX][baseZ] =location.getY();
			return true;
		}
		// too dark
		directOpacity [baseX][baseZ] =oldDirectOpacity;
		lightDirection [baseX][baseZ] = OrthogonalDirection.CENTER;// had to be to try relighting.
		// have to relight
	    this.spread(spotlight);
		return false;
	}
	
	boolean testTrunk(World world, BlockPos location, IBlockState placed, int generateFlags) {
		Spotlight place  = new Spotlight(location);
		if (place.trunk()) {
			// already trunk; go ahead;
			return testPlace(world, location, placed, generateFlags);
		} else {
			// have to test place
			place.setTrunk();
			boolean successful = debugTestPlace(world, location, placed, generateFlags);;
			if (!successful) {
				place.setTrunk(false);
				place.chooseLight();
				spread(place);
		    } else {
		    	// set opacity to dark
		    	place.setOpacity(255);
		    }
			return successful;
		}
	}
	
	public void setTrunk(BlockPos set) {
		new Spotlight(set).setTrunk();
	}

	public void setNotTrunk(BlockPos set) {
		new Spotlight(set).setTrunk(false);
	}
	
	private boolean inArray(int x, int z) {
		return ((x>-1) && (x<width) && (z>-1) && (z<width));
	}
	
	/**
	 * 从 {@code startY} 向下扫到水面，遇到遮光块时只在该段第一次命中处调用 checkLight。
	 * 空 section 对 {@code looking} 的唯一影响是把它置回 true，因此可以整段跳过。
	 *
	 * @return 实际调用 sink 的次数（仅用于测试/诊断）
	 */
	static int scanCheckLightingColumn(ColumnAccess access, int worldX, int worldZ,
									   int startY, int waterTop, LightCheckSink sink) {
		int checks = 0;
		boolean looking = true;
		int y = startY;
		while (y > waterTop) {
			if (access.isEmptySection(y)) {
				ChunkGenerationProfiler.count(Counter.SKYLIGHT_CHECK_EMPTY_SECTIONS);
				// 整段空气：原逻辑会逐格把 looking 置 true；直接跳到段底下一格。
				y = ((y >> 4) << 4) - 1;
				looking = true;
				continue;
			}
			IBlockState state = access.get(y);
			ChunkGenerationProfiler.count(Counter.SKYLIGHT_CHECK_STATE_READS);
			if (state.getLightOpacity() > 10) {
				if (looking) {
					sink.check(worldX, y, worldZ);
					checks++;
				}
				looking = false;
			} else {
				looking = true;
			}
			y--;
		}
		return checks;
	}

	public void checkLighting(World world) {
		final boolean debug = world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES;
		final ChunkColumnAccess chunkAccess = new ChunkColumnAccess();
		final WorldColumnAccess worldAccess = new WorldColumnAccess();
		final int waterTop = rtg.api.world.WaterLevel.current().waterSurfaceTop();
		// WorldProfilerMixin 在 World#checkLight 入口统一计数；这里只负责给出坐标。
		final LightCheckSink sink = (x, y, z) -> world.checkLight(new BlockPos(x, y, z));

		for (int localX = 0; localX < width; localX++) {
			for (int localZ = 0; localZ < width; localZ++) {
				ChunkGenerationProfiler.count(Counter.SKYLIGHT_CHECK_COLUMNS);
				if ((opacity[localX][localZ] ==0)||(opacity[localX][localZ] >15)) continue; // either unaffected or hopelessly dark
				int y = lowest[localX][localZ] - 1;
				if (y>=254) continue; // this tree didn't place any blocks there
				final int worldX = base.getX()-distance + localX;
				final int worldZ = base.getZ()-distance + localZ;
				final Chunk chunk = world.getChunkProvider().getLoadedChunk(worldX >> 4, worldZ >> 4);

				if (chunk == null || debug) {
					// 保持旧行为：未加载区块仍由 World#getBlockState 决定是否加载/返回空气；
					// DEBUG 世界必须走 World/Chunk 的调试特例，不能直接读 storage。
					worldAccess.set(world, worldX, worldZ);
					scanCheckLightingColumn(worldAccess, worldX, worldZ, y, waterTop, sink);
				} else {
					chunkAccess.set(chunk, false, worldX & 15, worldZ & 15);
					scanCheckLightingColumn(chunkAccess, worldX, worldZ, y, waterTop, sink);
				}
			}
		}
	}
	
    public enum OrthogonalDirection {
    	CENTER,
        XDOWN,
        ZDOWN,
        XUP,
        ZUP;
        
        public OrthogonalDirection opposite() {
        	switch(this)  {
			case XDOWN: {
				return XUP;
			    }
			case ZDOWN: {
				return ZUP;
		        }
			case XUP: {
				return XDOWN;
		        }
		    case ZUP: {
			    return ZDOWN;
	            }
    		default: {
    			return CENTER;
    		    }
    	    }
        }
    }
}