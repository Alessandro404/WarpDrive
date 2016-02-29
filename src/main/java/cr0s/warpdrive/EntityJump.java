package cr0s.warpdrive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import blusunrize.immersiveengineering.api.energy.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.ImmersiveNetHandler;
import blusunrize.immersiveengineering.api.energy.ImmersiveNetHandler.Connection;
import am2.api.power.IPowerNode;
import am2.power.PowerNodeRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.util.Constants;
import cpw.mods.fml.common.Optional;
import cr0s.warpdrive.block.movement.TileEntityShipCore;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.JumpBlock;
import cr0s.warpdrive.data.MovingEntity;
import cr0s.warpdrive.data.Planet;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.world.SpaceTeleporter;

public class EntityJump extends Entity {
	// Jump vector
	private int moveX;
	private int moveY;
	private int moveZ;
	
	private int xCoord;
	private int yCoord;
	private int zCoord;
	private int dx;
	private int dz;
	private int distance;
	private int direction;
	public int shipLength;
	public int maxX;
	public int maxZ;
	public int maxY;
	public int minX;
	public int minZ;
	public int minY;
	
	private boolean isHyperspaceJump;
	
	private World targetWorld;
	private Ticket sourceWorldTicket;
	private Ticket targetWorldTicket;
	
	private boolean collisionDetected = false;
	private ArrayList<Vector3> collisionAtSource;
	private ArrayList<Vector3> collisionAtTarget;
	private float collisionStrength = 0;
	
	public boolean on = false;
	private JumpBlock ship[];
	private TileEntityShipCore shipCore;
	
	private final static int STATE_IDLE = 0;
	private final static int STATE_JUMPING = 1;
	private final static int STATE_REMOVING = 2;
	private int state = STATE_IDLE;
	private int currentIndexInShip = 0;
	
	private List<MovingEntity> entitiesOnShip;
	
	private boolean betweenWorlds;
	
	private int destX, destY, destZ;
	private boolean isCoordJump;
	
	private long msCounter = 0;
	private int ticks = 0;
	
	public EntityJump(World world) {
		super(world);
		targetWorld = worldObj;
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Entity created (empty) in dimension " + worldObj.getProviderName() + " - " + worldObj.getWorldInfo().getWorldName());
		}
	}
	
	public EntityJump(World world, int x, int y, int z, int _dx, int _dz, TileEntityShipCore _reactor, boolean _isHyperspaceJump, int _distance, int _direction,
			boolean _isCoordJump, int _destX, int _destY, int _destZ) {
		super(world);
		this.posX = x + 0.5D;
		this.posY = y + 0.5D;
		this.posZ = z + 0.5D;
		this.xCoord = x;
		this.yCoord = y;
		this.zCoord = z;
		this.dx = _dx;
		this.dz = _dz;
		this.shipCore = _reactor;
		this.isHyperspaceJump = _isHyperspaceJump;
		this.distance = _distance;
		this.direction = _direction;
		this.isCoordJump = _isCoordJump;
		this.destX = _destX;
		this.destY = _destY;
		this.destZ = _destZ;
		
		// set by reactor
		maxX = maxZ = maxY = minX = minZ = minY = 0;
		shipLength = 0;
		
		// set when preparing jump
		targetWorld = null;
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Entity created");
		}
	}
	
	public void killEntity(String reason) {
		if (!on) {
			return;
		}
		
		on = false;
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			if (reason == null || reason.isEmpty()) {
				WarpDrive.logger.info(this + " Killing jump entity...");
			} else {
				WarpDrive.logger.info(this + " Killing jump entity... (" + reason + ")");
			}
		}
		
		unforceChunks();
		worldObj.removeEntity(this);
	}
	
	@Override
	public boolean isEntityInvulnerable() {
		return true;
	}
	
	@Override
	public void onUpdate() {
		if (worldObj.isRemote) {
			return;
		}
		
		if (!on) {
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info(this + " Removing from onUpdate...");
			}
			worldObj.removeEntity(this);
			return;
		}
		
		if (minY < 0 || maxY > 255) {
			String msg = "Invalid Y coordinate(s), check ship dimensions...";
			messageToAllPlayersOnShip(msg);
			killEntity(msg);
			return;
		}
		
		ticks++;
		if (state == STATE_IDLE) {
			prepareToJump();
			if (on) {
				state = STATE_JUMPING;
			}
		} else if (state == STATE_JUMPING) {
			if (currentIndexInShip < ship.length - 1) {
				// moveEntities(true);
				moveShip();
			} else {
				moveEntities(false);
				currentIndexInShip = 0;
				state = STATE_REMOVING;
			}
		} else if (state == STATE_REMOVING) {
			removeShip();
			
			if (currentIndexInShip >= ship.length - 1) {
				finishJump();
				state = STATE_IDLE;
			}
		} else {
			String msg = "Invalid state, aborting jump...";
			messageToAllPlayersOnShip(msg);
			killEntity(msg);
			return;
		}
	}
	
	private boolean forceChunks(StringBuilder reason) {
		LocalProfiler.start("EntityJump.forceChunks");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Forcing chunks in " + worldObj.provider.getDimensionName() + " and " + targetWorld.provider.getDimensionName());
		}
		sourceWorldTicket = ForgeChunkManager.requestTicket(WarpDrive.instance, worldObj, Type.NORMAL); // Type.ENTITY);
		if (sourceWorldTicket == null) {
			reason.append("Chunkloading rejected in source world " + worldObj.getWorldInfo().getWorldName() + ". Aborting.");
			return false;
		}
		targetWorldTicket = ForgeChunkManager.requestTicket(WarpDrive.instance, targetWorld, Type.NORMAL);
		if (targetWorldTicket == null) {
			reason.append("Chunkloading rejected in target world " + worldObj.getWorldInfo().getWorldName() + ". Aborting.");
			return false;
		}
		// sourceWorldTicket.bindEntity(this);
		int x1 = minX >> 4;
		int x2 = maxX >> 4;
		int z1 = minZ >> 4;
		int z2 = maxZ >> 4;
		int chunkCount = 0;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				chunkCount++;
				if (chunkCount > sourceWorldTicket.getMaxChunkListDepth()) {
					reason.append("Ship is extending over too many chunks in source world. Max is currently set to " + sourceWorldTicket.getMaxChunkListDepth() + " in forgeChunkLoading.cfg. Aborting.");
					return false;
				}
				ForgeChunkManager.forceChunk(sourceWorldTicket, new ChunkCoordIntPair(x, z));
			}
		}
		
		x1 = (minX + moveX) >> 4;
		x2 = (maxX + moveX) >> 4;
		z1 = (minZ + moveZ) >> 4;
		z2 = (maxZ + moveZ) >> 4;
		chunkCount = 0;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				chunkCount++;
				if (chunkCount > targetWorldTicket.getMaxChunkListDepth()) {
					reason.append("Ship is extending over too many chunks in target world. Max is currently set to " + targetWorldTicket.getMaxChunkListDepth() + " in forgeChunkLoading.cfg. Aborting.");
					return false;
				}
				ForgeChunkManager.forceChunk(targetWorldTicket, new ChunkCoordIntPair(x, z));
			}
		}
		LocalProfiler.stop();
		return true;
	}
	
	private void unforceChunks() {
		LocalProfiler.start("EntityJump.unforceChunks");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Unforcing chunks");
		}
		
		int x1, x2, z1, z2;
		if (sourceWorldTicket != null) {
			x1 = minX >> 4;
			x2 = maxX >> 4;
			z1 = minZ >> 4;
			z2 = maxZ >> 4;
			for (int x = x1; x <= x2; x++) {
				for (int z = z1; z <= z2; z++) {
					ForgeChunkManager.unforceChunk(sourceWorldTicket, new ChunkCoordIntPair(x, z));
				}
			}
			ForgeChunkManager.releaseTicket(sourceWorldTicket);
			sourceWorldTicket = null;
		}
		
		if (targetWorldTicket != null) {
			x1 = (minX + moveX) >> 4;
			x2 = (maxX + moveX) >> 4;
			z1 = (minZ + moveZ) >> 4;
			z2 = (maxZ + moveZ) >> 4;
			for (int x = x1; x <= x2; x++) {
				for (int z = z1; z <= z2; z++) {
					ForgeChunkManager.unforceChunk(targetWorldTicket, new ChunkCoordIntPair(x, z));
				}
			}
			ForgeChunkManager.releaseTicket(targetWorldTicket);
			targetWorldTicket = null;
		}
		
		LocalProfiler.stop();
	}
	
	private void messageToAllPlayersOnShip(String msg) {
		if (entitiesOnShip == null) {
			shipCore.messageToAllPlayersOnShip(msg);
		} else {
			WarpDrive.logger.info(this + " messageToAllPlayersOnShip: " + msg);
			for (MovingEntity me : entitiesOnShip) {
				if (me.entity instanceof EntityPlayer) {
					WarpDrive.addChatMessage((EntityPlayer) me.entity, "["
							+ ((shipCore != null && shipCore.shipName.length() > 0) ? shipCore.shipName : "WarpCore") + "] " + msg);
				}
			}
		}
	}
	
	public static String getDirectionLabel(int direction) {
		switch (direction) {
		case -1:
			return "UP";
		case -2:
			return "DOWN";
		case 0:
			return "FRONT";
		case 180:
			return "BACK";
		case 90:
			return "LEFT";
		case 255:
			return "RIGHT";
		default:
			return direction + " degrees";
		}
	}
	
	private void prepareToJump() {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Preparing to jump...");
		}
		LocalProfiler.start("EntityJump.prepareToJump");
		
		StringBuilder reason = new StringBuilder();
		
		boolean isInSpace = (worldObj.provider.dimensionId == WarpDriveConfig.G_SPACE_DIMENSION_ID);
		boolean isInHyperSpace = (worldObj.provider.dimensionId == WarpDriveConfig.G_HYPERSPACE_DIMENSION_ID);
		
		boolean toSpace = (direction == -1) && (maxY + distance > 255) && (!isInSpace) && (!isInHyperSpace);
		boolean fromSpace = (direction == -2) && (minY - distance < 0) && isInSpace;
		betweenWorlds = fromSpace || toSpace || isHyperspaceJump;
		moveX = moveY = moveZ = 0;
		
		if (!isHyperspaceJump && toSpace) {
			Boolean planetFound = false;
			Boolean planetValid = false;
			int closestPlanetDistance = Integer.MAX_VALUE;
			Planet closestPlanet = null;
			for (int iPlane = 0; (!planetValid) && iPlane < WarpDriveConfig.PLANETS.length; iPlane++) {
				Planet planet = WarpDriveConfig.PLANETS[iPlane];
				if (worldObj.provider.dimensionId == planet.dimensionId) {
					planetFound = true;
					int planetDistance = planet.isValidToSpace(new VectorI(this));
					if (planetDistance == 0) {
						planetValid = true;
						moveX = planet.spaceCenterX - planet.dimensionCenterX;
						moveZ = planet.spaceCenterZ - planet.dimensionCenterZ;
						targetWorld = MinecraftServer.getServer().worldServerForDimension(WarpDriveConfig.G_SPACE_DIMENSION_ID);
						if (targetWorld == null) {
							LocalProfiler.stop();
							String msg = "Unable to load Space dimension " + WarpDriveConfig.G_SPACE_DIMENSION_ID + ", aborting jump.";
							messageToAllPlayersOnShip(msg);
							killEntity(msg);
							return;
						}
					} else if (closestPlanetDistance > planetDistance) {
						closestPlanetDistance = planetDistance;
						closestPlanet = planet;
					}
				}
			}
			if (!planetFound) {
				LocalProfiler.stop();
				String msg = "Unable to reach space!\nThere's no valid transition plane for current dimension " + worldObj.provider.getDimensionName() + " ("
						+ worldObj.provider.dimensionId + ")";
				messageToAllPlayersOnShip(msg);
				killEntity(msg);
				return;
			}
			if (!planetValid) {
				LocalProfiler.stop();
				assert(closestPlanet != null);
				@SuppressWarnings("null") // Eclipse derp, don't remove
				String msg = "Ship is outside border, unable to reach space!\nClosest transition plane is ~" + closestPlanetDistance + " m away ("
						+ (closestPlanet.dimensionCenterX - closestPlanet.borderSizeX) + ", 250,"
						+ (closestPlanet.dimensionCenterZ - closestPlanet.borderSizeZ) + ") to ("
						+ (closestPlanet.dimensionCenterX + closestPlanet.borderSizeX) + ", 255,"
						+ (closestPlanet.dimensionCenterZ + closestPlanet.borderSizeZ) + ")";
				messageToAllPlayersOnShip(msg);
				killEntity(msg);
				return;
			}
		} else if (!isHyperspaceJump && fromSpace) {
			Boolean planeFound = false;
			int closestPlaneDistance = Integer.MAX_VALUE;
			Planet closestTransitionPlane = null;
			for (int iPlanet = 0; (!planeFound) && iPlanet < WarpDriveConfig.PLANETS.length; iPlanet++) {
				Planet planet = WarpDriveConfig.PLANETS[iPlanet];
				int planeDistance = planet.isValidFromSpace(new VectorI(this));
				if (planeDistance == 0) {
					planeFound = true;
					moveX = planet.dimensionCenterX - planet.spaceCenterX;
					moveZ = planet.dimensionCenterZ - planet.spaceCenterZ;
					targetWorld = MinecraftServer.getServer().worldServerForDimension(planet.dimensionId);
					if (targetWorld == null) {
						LocalProfiler.stop();
						String msg = "Undefined dimension " + planet.dimensionId + ", aborting jump. Check your server configuration!";
						messageToAllPlayersOnShip(msg);
						killEntity(msg);
						return;
					}
				} else if (closestPlaneDistance > planeDistance) {
					closestPlaneDistance = planeDistance;
					closestTransitionPlane = planet;
				}
			}
			if (!planeFound) {
				LocalProfiler.stop();
				String msg = "";
				if (closestTransitionPlane == null) {
					msg = "No planet defined, unable to enter atmosphere!";
				} else {
					msg = "No planet in range, unable to enter atmosphere!\nClosest transition plane is " + closestPlaneDistance + " m away ("
							+ (closestTransitionPlane.spaceCenterX - closestTransitionPlane.borderSizeX) + ", 250,"
							+ (closestTransitionPlane.spaceCenterZ - closestTransitionPlane.borderSizeZ) + ") to ("
							+ (closestTransitionPlane.spaceCenterX + closestTransitionPlane.borderSizeX) + ", 255,"
							+ (closestTransitionPlane.spaceCenterZ + closestTransitionPlane.borderSizeZ) + ")";
				}
				messageToAllPlayersOnShip(msg);
				killEntity(msg);
				return;
			}
		} else if (isHyperspaceJump && isInHyperSpace) {
			targetWorld = MinecraftServer.getServer().worldServerForDimension(WarpDriveConfig.G_SPACE_DIMENSION_ID);
			if (targetWorld == null) {
				LocalProfiler.stop();
				String msg = "Unable to load Space dimension " + WarpDriveConfig.G_SPACE_DIMENSION_ID + ", aborting jump.";
				messageToAllPlayersOnShip(msg);
				killEntity(msg);
				return;
			}
		} else if (isHyperspaceJump && isInSpace) {
			targetWorld = MinecraftServer.getServer().worldServerForDimension(WarpDriveConfig.G_HYPERSPACE_DIMENSION_ID);
			if (targetWorld == null) {
				LocalProfiler.stop();
				String msg = "Unable to load Hyperspace dimension " + WarpDriveConfig.G_HYPERSPACE_DIMENSION_ID + ", aborting jump.";
				messageToAllPlayersOnShip(msg);
				killEntity(msg);
				return;
			}
		} else {
			targetWorld = worldObj;
		}
		
		// Calculate jump vector
		if (isCoordJump) {
			moveX = destX - xCoord;
			moveZ = destZ - zCoord;
			moveY = destY - yCoord;
			distance = 0; // FIXME: check collision in straight path, starting with getPossibleJumpDistance() ?
		} else if (isHyperspaceJump) {
			distance = 0;
			if (!isInSpace && !isInHyperSpace) {
				String msg = "Unable to reach hyperspace from a planet";
				messageToAllPlayersOnShip(msg);
				killEntity(msg);
				LocalProfiler.stop();
				return;
			}
		} else {
			if (toSpace) {
				// enter space at current altitude
				moveY = 0;
			} else if (fromSpace) {
				// re-enter atmosphere at max altitude
				moveY = 245 - maxY;
			} else {
				// Do not check in long jumps
				if (distance < 256) {
					distance = getPossibleJumpDistance();
				}
				
				int movementVector[] = getVector(direction);
				moveX = movementVector[0] * distance;
				moveY = movementVector[1] * distance;
				moveZ = movementVector[2] * distance;
				
				if ((maxY + moveY) > 255) {
					moveY = 255 - maxY;
				}
				
				if ((minY + moveY) < 5) {
					moveY = 5 - minY;
				}
			}
		}
		
		if (betweenWorlds && WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " From world " + worldObj.provider.getDimensionName() + " to " + targetWorld.provider.getDimensionName());
		}
		
		// Validate positions aren't overlapping
		if (!betweenWorlds) {
			if (Math.abs(moveX) <= (maxX - minX + 1) && Math.abs(moveY) <= (maxY - minY + 1) && Math.abs(moveZ) <= (maxZ - minZ + 1)) {
				// render fake explosions
				doCollisionDamage(false);
				
				// cancel jump
				String msg = "Not enough space for jump!";
				killEntity(msg);
				messageToAllPlayersOnShip(msg);
				LocalProfiler.stop();
				return;
			}
		}
		
		if (!forceChunks(reason)) {
			String msg = reason.toString();
			killEntity(msg);
			messageToAllPlayersOnShip(msg);
			LocalProfiler.stop();
			return;
		}
		
		{
			String msg = saveEntities();
			if (msg != null) {
				killEntity(msg);
				messageToAllPlayersOnShip(msg);
				LocalProfiler.stop();
				return;
			}
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info(this + " Saved " + entitiesOnShip.size() + " entities from ship");
			}
		}
		
		if (isHyperspaceJump && isInSpace) {
			messageToAllPlayersOnShip("Entering HYPERSPACE...");
		} else if (isHyperspaceJump && isInHyperSpace) {
			messageToAllPlayersOnShip("Leaving HYPERSPACE..");
		} else if (isCoordJump) {
			messageToAllPlayersOnShip("Jumping to coordinates (" + destX + "; " + yCoord + "; " + destZ + ")!");
		} else {
			messageToAllPlayersOnShip("Jumping " + getDirectionLabel(direction) + " by " + distance + " blocks");
		}
		
		// validate ship content
		int shipVolume = getRealShipVolume_checkBedrock(reason);
		if (shipVolume == -1) {
			String msg = reason.toString();
			killEntity(msg);
			messageToAllPlayersOnShip(msg);
			LocalProfiler.stop();
			return;
		}
		
		saveShip(shipVolume);
		this.currentIndexInShip = 0;
		msCounter = System.currentTimeMillis();
		LocalProfiler.stop();
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info("Removing TE duplicates: tileEntities in target world before jump: " + targetWorld.loadedTileEntityList.size());
		}
	}
	
	private int getRealShipVolume_checkBedrock(StringBuilder reason) {
		LocalProfiler.start("EntityJump.getRealShipVolume_checkBedrock");
		int shipVolume = 0;
		
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					Block block = worldObj.getBlock(x, y, z);
					
					// Skipping vanilla air & ignored blocks
					if (block == Blocks.air || Dictionary.BLOCKS_LEFTBEHIND.contains(block)) {
						continue;
					}
					
					shipVolume++;
					
					if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
						WarpDrive.logger.info("Block(" + x + ", " + y + ", " + z + ") is " + block.getUnlocalizedName() + "@" + worldObj.getBlockMetadata(x, y, z));
					}
					
					// Stop on non-movable blocks
					if (Dictionary.BLOCKS_ANCHOR.contains(block)) {
						reason.append(block.getUnlocalizedName() + " detected onboard at " + x + ", " + y + ", " + z + ". Aborting.");
						LocalProfiler.stop();
						return -1;
					}
				}
			}
		}
		
		// Abort jump if blocks with TE are connecting to the ship (avoid crash when splitting multi-blocks)
		for (int x = minX - 1; x <= maxX + 1; x++) {
			boolean xBorder = (x == minX - 1) || (x == maxX + 1);
			for (int z = minZ - 1; z <= maxZ + 1; z++) {
				boolean zBorder = (z == minZ - 1) || (z == maxZ + 1);
				for (int y = minY - 1; y <= maxY + 1; y++) {
					boolean yBorder = (y == minY - 1) || (y == maxY + 1);
					if ((y < 0) || (y > 255)) {
						continue;
					}
					if (!(xBorder || yBorder || zBorder)) {
						continue;
					}
					
					Block block = worldObj.getBlock(x, y, z);
					
					// Skipping any air block & ignored blocks
					if (worldObj.isAirBlock(x, y, z) || Dictionary.BLOCKS_LEFTBEHIND.contains(block)) {
						continue;
					}
					
					// Skipping non-movable blocks
					if (Dictionary.BLOCKS_ANCHOR.contains(block)) {
						continue;
					}
					
					// Skipping blocks without tile entities
					TileEntity tileEntity = worldObj.getTileEntity(x, y, z);
					if (tileEntity == null) {
						continue;
					}
					
					reason.append("Ship snagged by " + block.getLocalizedName() + " at " + x + ", " + y + ", " + z + ". Damage report pending...");
					worldObj.createExplosion((Entity) null, x, y, z, Math.min(4F * 30, 4F * (shipVolume / 50)), false);
					LocalProfiler.stop();
					return -1;
				}
			}
		}
		
		LocalProfiler.stop();
		return shipVolume;
	}
	
	/**
	 * Saving ship to memory
	 *
	 * @param shipVolume
	 */
	private void saveShip(int shipVolume) {
		LocalProfiler.start("EntityJump.saveShip");
		try {
			JumpBlock[][] placeTimeJumpBlocks = { new JumpBlock[shipVolume], new JumpBlock[shipVolume], new JumpBlock[shipVolume], new JumpBlock[shipVolume], new JumpBlock[shipVolume] };
			int[] placeTimeIndexes = { 0, 0, 0, 0, 0 }; 
			
			int xc1 = minX >> 4;
			int xc2 = maxX >> 4;
			int zc1 = minZ >> 4;
			int zc2 = maxZ >> 4;
			
			for (int xc = xc1; xc <= xc2; xc++) {
				int x1 = Math.max(minX, xc << 4);
				int x2 = Math.min(maxX, (xc << 4) + 15);
				
				for (int zc = zc1; zc <= zc2; zc++) {
					int z1 = Math.max(minZ, zc << 4);
					int z2 = Math.min(maxZ, (zc << 4) + 15);
					
					for (int y = minY; y <= maxY; y++) {
						for (int x = x1; x <= x2; x++) {
							for (int z = z1; z <= z2; z++) {
								Block block = worldObj.getBlock(x, y, z);
								
								// Skipping vanilla air & ignored blocks
								if (block == Blocks.air || Dictionary.BLOCKS_LEFTBEHIND.contains(block)) {
									continue;
								}
								
								int blockMeta = worldObj.getBlockMetadata(x, y, z);
								TileEntity tileEntity = worldObj.getTileEntity(x, y, z);
								JumpBlock jumpBlock = new JumpBlock(block, blockMeta, tileEntity, x, y, z);
								
								// save energy network
								if (WarpDriveConfig.isArsMagica2Loaded) {
									arsMagica2_energySave(tileEntity, jumpBlock);
								}
								if (WarpDriveConfig.isImmersiveEngineeringLoaded) {
									immersiveEngineering_energySave(tileEntity, jumpBlock);
								}
								
								// default priority is 2 for block, 3 for tile entities
								Integer placeTime = Dictionary.BLOCKS_PLACE.get(block);
								if (placeTime == null) {
									if (tileEntity == null) {
										placeTime = 2;
									} else {
										placeTime = 3;
									}
								}
								
								placeTimeJumpBlocks[placeTime][placeTimeIndexes[placeTime]] = jumpBlock;
								placeTimeIndexes[placeTime]++;
							}
						}
					}
				}
			}
			
			ship = new JumpBlock[shipVolume];
			int indexShip = 0;
			for (int placeTime = 0; placeTime < 5; placeTime++) {
				for (int placeTimeIndex = 0; placeTimeIndex < placeTimeIndexes[placeTime]; placeTimeIndex++) {
					ship[indexShip] = placeTimeJumpBlocks[placeTime][placeTimeIndex];
					indexShip++;
				}
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			killEntity("Exception during jump preparation (saveShip)!");
			LocalProfiler.stop();
			return;
		}
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Ship saved as " + ship.length + " blocks");
		}
		LocalProfiler.stop();
	}
	
	/**
	 * Ship moving
	 */
	private void moveShip() {
		LocalProfiler.start("EntityJump.moveShip");
		int blocksToMove = Math.min(WarpDriveConfig.G_BLOCKS_PER_TICK, ship.length - currentIndexInShip);
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Moving ship blocks " + currentIndexInShip + " to " + (currentIndexInShip + blocksToMove - 1) + " / " + (ship.length - 1));
		}
		
		for (int index = 0; index < blocksToMove; index++) {
			if (currentIndexInShip >= ship.length) {
				break;
			}
			
			JumpBlock jb = ship[currentIndexInShip];
			if (jb != null) {
				if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
					WarpDrive.logger.info("Deploying from " + jb.x + ", " + jb.y + ", " + jb.z + " of " + jb.block + "@" + jb.blockMeta);
				}
				jb.deploy(targetWorld, moveX, moveY, moveZ);
				if (jb.nbtArsMagica2 != null) {
					arsMagica2_energyRemove(jb);
				}
				if (jb.nbtImmersiveEngineering != null) {
					immersiveEngineering_energyRemove(jb);
				}
				worldObj.removeTileEntity(jb.x, jb.y, jb.z);
			}
			currentIndexInShip++;
		}
		
		LocalProfiler.stop();
	}
	
	/**
	 * Removing ship from world
	 */
	private void removeShip() {
		LocalProfiler.start("EntityJump.removeShip");
		int blocksToMove = Math.min(WarpDriveConfig.G_BLOCKS_PER_TICK, ship.length - currentIndexInShip);
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Removing ship blocks " + currentIndexInShip + " to " + (currentIndexInShip + blocksToMove - 1) + " / " + (ship.length - 1));
		}
		for (int index = 0; index < blocksToMove; index++) {
			if (currentIndexInShip >= ship.length) {
				break;
			}
			JumpBlock jb = ship[ship.length - currentIndexInShip - 1];
			if (jb == null) {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info(this + " Removing ship part: unexpected null found at ship[" + currentIndexInShip + "]");
				}
				currentIndexInShip++;
				continue;
			}
			if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
				WarpDrive.logger.info("Removing block " + jb.block + "@" + jb.blockMeta + " at " + jb.x + ", " + jb.y + ", " + jb.z);
			}
			
			if (jb.blockTileEntity != null) {
				if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
					WarpDrive.logger.info("Removing tile entity at " + jb.x + ", " + jb.y + ", " + jb.z);
				}
				if (jb.nbtArsMagica2 != null) {
					arsMagica2_energyPlace(jb);
				}
				if (jb.nbtImmersiveEngineering != null) {
					immersiveEngineering_energyPlace(jb);
				}
				worldObj.removeTileEntity(jb.x, jb.y, jb.z);
			}
			worldObj.setBlock(jb.x, jb.y, jb.z, Blocks.air, 0, 2);
			
			JumpBlock.refreshBlockStateOnClient(targetWorld, jb.x + moveX, jb.y + moveY, jb.z + moveZ);
			
			currentIndexInShip++;
		}
		LocalProfiler.stop();
	}
	
	@Optional.Method(modid = "arsmagica2")
	private void arsMagica2_energySave(TileEntity tileEntity, JumpBlock jumpBlock) {
		if (tileEntity instanceof IPowerNode) {
			jumpBlock.nbtArsMagica2 = PowerNodeRegistry.For(worldObj).getDataCompoundForNode((IPowerNode) tileEntity);
			if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
				WarpDrive.logger.info("Saved ArsMagica2 energy at " + jumpBlock.x + ", " + jumpBlock.y + ", " + jumpBlock.z + " " + jumpBlock.nbtArsMagica2);
			}
		}
	}
	
	@Optional.Method(modid = "arsmagica2")
	private void arsMagica2_energyRemove(JumpBlock jumpBlock) {
		PowerNodeRegistry.For(jumpBlock.blockTileEntity.getWorldObj()).removePowerNode((IPowerNode) jumpBlock.blockTileEntity);
	}
	
	@Optional.Method(modid = "arsmagica2")
	private void arsMagica2_energyPlace(JumpBlock jumpBlock) {
		if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
			WarpDrive.logger.info("Restoring ArsMagica2 energy at " + jumpBlock.x + ", " + jumpBlock.y + ", " + jumpBlock.z + " " + jumpBlock.nbtArsMagica2);
		}
		NBTTagCompound nbtTagCompound = (NBTTagCompound) jumpBlock.nbtArsMagica2.copy();
		
		// powerAmounts
		// (no changes)
		
		// powerPathList
		NBTTagList powerPathList = nbtTagCompound.getTagList("powerPathList", Constants.NBT.TAG_COMPOUND);
		if (powerPathList != null) {
			for (int powerPathIndex = 0; powerPathIndex < powerPathList.tagCount(); powerPathIndex++) {
				NBTTagCompound powerPathEntry = (NBTTagCompound) powerPathList.removeTag(0);
				
				// powerPathList[powerPathIndex].powerType
				// (no change)
				
				// powerPathList[powerPathIndex].nodePaths
				NBTTagList nodePaths = powerPathEntry.getTagList("nodePaths", Constants.NBT.TAG_LIST);
				if (nodePaths != null) {
					for (int nodePathIndex = 0; nodePathIndex < nodePaths.tagCount(); nodePathIndex++) {
						// we can't directly access it, hence removing then adding back later on
						NBTTagList nodeList = (NBTTagList) nodePaths.removeTag(0);
						if (nodeList != null) {
							for (int nodeIndex = 0; nodeIndex < nodeList.tagCount(); nodeIndex++) {
								NBTTagCompound node = (NBTTagCompound) nodeList.removeTag(0);
								// read coordinates
								node.setFloat("Vec3_x", node.getFloat("Vec3_x") + moveX);
								node.setFloat("Vec3_y", node.getFloat("Vec3_y") + moveY);
								node.setFloat("Vec3_z", node.getFloat("Vec3_z") + moveZ);
								//tack the node on to the power path
								nodeList.appendTag(node);
							}
							nodePaths.appendTag(nodeList);
						}
					}
					powerPathEntry.setTag("nodePaths", nodePaths);
				}
				powerPathList.appendTag(powerPathEntry);
			}
			nbtTagCompound.setTag("powerPathList", powerPathList);
		}
		
		PowerNodeRegistry.For(targetWorld).setDataCompoundForNode((IPowerNode) targetWorld.getTileEntity(jumpBlock.x + moveX, jumpBlock.y + moveY, jumpBlock.z + moveZ), nbtTagCompound);
	}
	
	@Optional.Method(modid = "ImmersiveEngineering")
	private void immersiveEngineering_energySave(TileEntity tileEntity, JumpBlock jumpBlock) {
		if (tileEntity instanceof IImmersiveConnectable) {
			ChunkCoordinates node = new ChunkCoordinates(jumpBlock.blockTileEntity.xCoord, jumpBlock.blockTileEntity.yCoord, jumpBlock.blockTileEntity.zCoord);
			Collection<Connection> connections = ImmersiveNetHandler.INSTANCE.getConnections(tileEntity.getWorldObj(), node);
			if (connections != null) {
				jumpBlock.nbtImmersiveEngineering = new NBTTagList();
				for (Connection connection : connections) {
					jumpBlock.nbtImmersiveEngineering.appendTag(connection.writeToNBT());
				}
				ImmersiveNetHandler.INSTANCE.clearConnectionsOriginatingFrom(node, worldObj);
			}
			if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
				WarpDrive.logger.info("Saved ImmersiveEngineering energy at " + jumpBlock.x + ", " + jumpBlock.y + ", " + jumpBlock.z + " " + jumpBlock.nbtImmersiveEngineering);
			}
		}
	}
	
	@Optional.Method(modid = "ImmersiveEngineering")
	private void immersiveEngineering_energyRemove(JumpBlock jumpBlock) {
		// needs to be done while saving
	}
	
	@Optional.Method(modid = "ImmersiveEngineering")
	private void immersiveEngineering_energyPlace(JumpBlock jumpBlock) {
		if (jumpBlock.nbtImmersiveEngineering == null) {
			return;
		}
		if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
			WarpDrive.logger.info("Restoring ImmersiveEngineering energy at " + jumpBlock.x + ", " + jumpBlock.y + ", " + jumpBlock.z + " " + jumpBlock.nbtImmersiveEngineering);
		}
		
		// powerPathList
		for (int connectionIndex = 0; connectionIndex < jumpBlock.nbtImmersiveEngineering.tagCount(); connectionIndex++) {
			Connection connection = Connection.readFromNBT(jumpBlock.nbtImmersiveEngineering.getCompoundTagAt(connectionIndex));
			
			connection.start.posX += moveX;
			connection.start.posY += moveY;
			connection.start.posZ += moveZ;
			connection.end.posX += moveX;
			connection.end.posY += moveY;
			connection.end.posZ += moveZ;
			
			ImmersiveNetHandler.INSTANCE.addConnection(targetWorld, new ChunkCoordinates(connection.start.posX, connection.start.posY, connection.start.posZ), connection);
		}
	}
	
	/**
	 * Finish jump: move entities, unlock worlds and delete self
	 */
	private void finishJump() {
		// FIXME TileEntity duplication workaround
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Jump done in " + ((System.currentTimeMillis() - msCounter) / 1000F) + " seconds and " + ticks + " ticks");
		}
		if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
			WarpDrive.logger.info("Removing TE duplicates: tileEntities in target world after jump, before cleanup: " + targetWorld.loadedTileEntityList.size());
		}
		LocalProfiler.start("EntityJump.removeDuplicates()");
		
		try {
			targetWorld.loadedTileEntityList = this.removeDuplicates(targetWorld.loadedTileEntityList);
		} catch (Exception exception) {
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info("TE Duplicates removing exception: " + exception.getMessage());
				exception.printStackTrace();
			}
		}
		
		doCollisionDamage(true);
		
		LocalProfiler.stop();
		if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
			WarpDrive.logger.info("Removing TE duplicates: tileEntities in target world after jump, after cleanup: " + targetWorld.loadedTileEntityList.size());
		}
		killEntity("Jump done");
	}
	
	/**
	 * Checking jump possibility
	 *
	 * @return possible jump distance or -1
	 */
	private int getPossibleJumpDistance() {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Calculating possible jump distance...");
		}
		int testDistance = this.distance;
		int blowPoints = 0;
		collisionDetected = false;
		
		CheckMovementResult result = null;
		while (testDistance >= 0) {
			// Is there enough space in destination point?
			result = checkMovement(testDistance, false);
			
			if (result == null) {
				break;
			}
			
			if (result.isCollision) {
				blowPoints++;
			}
			testDistance--;
		}
		
		if (distance != testDistance && WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Jump distance adjusted to " + testDistance + " after " + blowPoints + " collisions");
		}
		
		// Register explosion(s) at collision point
		if (blowPoints > WarpDriveConfig.SHIP_COLLISION_TOLERANCE_BLOCKS) {
			result = checkMovement(Math.max(1, testDistance + 1), true);
			if (result != null) {
				/*
				 * Strength scaling:
				 * Wither skull = 1
				 * Creeper = 3 or 6
				 * TNT = 4
				 * TNTcart = 4 to 11.5
				 * Wither boom = 5
				 * Endercrystal = 6
				 */
				float massCorrection = 0.5F
						+ (float) Math.sqrt(Math.min(1.0D, Math.max(0.0D, shipCore.shipMass - WarpDriveConfig.SHIP_VOLUME_MAX_ON_PLANET_SURFACE)
								/ WarpDriveConfig.SHIP_VOLUME_MIN_FOR_HYPERSPACE));
				collisionDetected = true;
				collisionStrength = (4.0F + blowPoints - WarpDriveConfig.SHIP_COLLISION_TOLERANCE_BLOCKS) * massCorrection;
				collisionAtSource = result.atSource;
				collisionAtTarget = result.atTarget;
				WarpDrive.logger.info(this + " Reporting " + collisionAtTarget.size() + " collisions coordinates " + blowPoints
							+ " blowPoints with massCorrection of " + String.format("%.2f", massCorrection) + " => strength "
							+ String.format("%.2f", collisionStrength));
			} else {
				WarpDrive.logger.error("WarpDrive error: unable to compute collision points, ignoring...");
			}
		}
		
		return testDistance;
	}
	
	private void doCollisionDamage(boolean atTarget) {
		if (!collisionDetected) {
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info(this + " doCollisionDamage No collision detected...");
			}
			return;
		}
		ArrayList<Vector3> collisionPoints = atTarget ? collisionAtTarget : collisionAtSource;
		Vector3 min = collisionPoints.get(0);
		Vector3 max = collisionPoints.get(0);
		for (Vector3 v : collisionPoints) {
			if (min.x > v.x) {
				min.x = v.x;
			} else if (max.x < v.x) {
				max.x = v.x;
			}
			if (min.y > v.y) {
				min.y = v.y;
			} else if (max.y < v.y) {
				max.y = v.y;
			}
			if (min.z > v.z) {
				min.z = v.z;
			} else if (max.z < v.z) {
				max.z = v.z;
			}
		}
		
		// inform players on board
		double rx = Math.round(min.x + worldObj.rand.nextInt(Math.max(1, (int) (max.x - min.x))));
		double ry = Math.round(min.y + worldObj.rand.nextInt(Math.max(1, (int) (max.y - min.y))));
		double rz = Math.round(min.z + worldObj.rand.nextInt(Math.max(1, (int) (max.z - min.z))));
		messageToAllPlayersOnShip("Ship collision detected around " + (int) rx + ", " + (int) ry + ", " + (int) rz + ". Damage report pending...");
		
		// randomize if too many collision points
		int nbExplosions = Math.min(5, collisionPoints.size());
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info("doCollisionDamage nbExplosions " + nbExplosions + "/" + collisionPoints.size());
		}
		for (int i = 0; i < nbExplosions; i++) {
			// get location
			Vector3 current;
			if (nbExplosions < collisionPoints.size()) {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info("doCollisionDamage random #" + i);
				}
				current = collisionPoints.get(worldObj.rand.nextInt(collisionPoints.size()));
			} else {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info("doCollisionDamage get " + i);
				}
				current = collisionPoints.get(i);
			}
			
			// compute explosion strength with a jitter, at least 1 TNT
			float strength = Math.max(4.0F, collisionStrength / nbExplosions - 2.0F + 2.0F * worldObj.rand.nextFloat());
			
			(atTarget ? targetWorld : worldObj).newExplosion((Entity) null, current.x, current.y, current.z, strength, atTarget, atTarget);
			WarpDrive.logger.info("Ship collision caused explosion at " + current.x + ", " + current.y + ", " + current.z + " with strength " + strength);
		}
	}
	
	private String saveEntities() {
		String result = null;
		entitiesOnShip = new ArrayList<MovingEntity>();
		
		AxisAlignedBB axisalignedbb = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX + 0.99D, maxY + 0.99D, maxZ + 0.99D);
		
		List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);
		
		for (Entity entity : list) {
			if (entity == null || (entity instanceof EntityJump)) {
				continue;
			}
			
			String id = EntityList.getEntityString(entity);
			if (Dictionary.ENTITIES_ANCHOR.contains(id)) {
				result = "Anchor entity " + id + " detected at " + Math.floor(entity.posX) + ", " + Math.floor(entity.posY) + ", " + Math.floor(entity.posZ) + ", aborting jump...";
				// we need to continue so players are added so they can see the message...
				continue;
			}
			if (Dictionary.ENTITIES_LEFTBEHIND.contains(id)) {
				if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
					WarpDrive.logger.info("Leaving entity " + id + " behind: " + entity);
				}
				continue;
			}
			if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
				if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
					WarpDrive.logger.info("Adding entity " + id + ": " + entity);
				}
			} 
			MovingEntity movingEntity = new MovingEntity(entity);
			entitiesOnShip.add(movingEntity);
		}
		return result;
	}
	
	private boolean moveEntities(boolean restorePositions) {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Moving entities");
		}
		LocalProfiler.start("EntityJump.moveEntities");
		
		if (entitiesOnShip != null) {
			for (MovingEntity me : entitiesOnShip) {
				Entity entity = me.entity;
				
				if (entity == null) {
					continue;
				}
				
				double oldEntityX = me.oldX;
				double oldEntityY = me.oldY;
				double oldEntityZ = me.oldZ;
				double newEntityX;
				double newEntityY;
				double newEntityZ;
				
				if (restorePositions) {
					newEntityX = oldEntityX;
					newEntityY = oldEntityY;
					newEntityZ = oldEntityZ;
				} else {
					newEntityX = oldEntityX + moveX;
					newEntityY = oldEntityY + moveY;
					newEntityZ = oldEntityZ + moveZ;
				}
				
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info("Entity moving: old (" + oldEntityX + " " + oldEntityY + " " + oldEntityZ + ") -> new (" + newEntityX + " " + newEntityY + " " + newEntityZ);
				}
				
				// Travel to another dimension if needed
				if (betweenWorlds && !restorePositions) {
					MinecraftServer server = MinecraftServer.getServer();
					WorldServer from = server.worldServerForDimension(worldObj.provider.dimensionId);
					WorldServer to = server.worldServerForDimension(targetWorld.provider.dimensionId);
					SpaceTeleporter teleporter = new SpaceTeleporter(to, 0,
							MathHelper.floor_double(newEntityX),
							MathHelper.floor_double(newEntityY),
							MathHelper.floor_double(newEntityZ));
					
					if (entity instanceof EntityPlayerMP) {
						EntityPlayerMP player = (EntityPlayerMP) entity;
						server.getConfigurationManager().transferPlayerToDimension(player, targetWorld.provider.dimensionId, teleporter);
						player.sendPlayerAbilities();
					} else {
						server.getConfigurationManager().transferEntityToWorld(entity, worldObj.provider.dimensionId, from, to, teleporter);
					}
				}
				
				// Update position
				if (entity instanceof EntityPlayerMP) {
					EntityPlayerMP player = (EntityPlayerMP) entity;
					
					ChunkCoordinates bedLocation = player.getBedLocation(player.worldObj.provider.dimensionId);
					
					if (bedLocation != null && minX <= bedLocation.posX && maxX >= bedLocation.posX && minY <= bedLocation.posY && maxY >= bedLocation.posY
							&& minZ <= bedLocation.posZ && maxZ >= bedLocation.posZ) {
						bedLocation.posX = bedLocation.posX + moveX;
						bedLocation.posY = bedLocation.posY + moveY;
						bedLocation.posZ = bedLocation.posZ + moveZ;
						player.setSpawnChunk(bedLocation, false);
					}
					
					player.setPositionAndUpdate(newEntityX, newEntityY, newEntityZ);
				} else {
					entity.setPosition(newEntityX, newEntityY, newEntityZ);
				}
			}
		}
		
		LocalProfiler.stop();
		return true;
	}
	
	public int[] getVector(int i) {
		int v[] = { 0, 0, 0 };
		
		switch (i) {
		case -1:
			v[1] = 1;
			break;
			
		case -2:
			v[1] = -1;
			break;
			
		case 0:
			v[0] = dx;
			v[2] = dz;
			break;
			
		case 180:
			v[0] = -dx;
			v[2] = -dz;
			break;
			
		case 90:
			v[0] = dz;
			v[2] = -dx;
			break;
			
		case 270:
			v[0] = -dz;
			v[2] = dx;
			break;
			
		default:
			WarpDrive.logger.error(this + "Invalid direction " + i);
			break;
		}
		
		return v;
	}
	
	class CheckMovementResult {
		public ArrayList<Vector3> atSource;
		public ArrayList<Vector3> atTarget;
		public boolean isCollision = false;
		public String reason = "";
		
		CheckMovementResult() {
			this.atSource = new ArrayList<Vector3>(1);
			this.atTarget = new ArrayList<Vector3>(1);
			this.isCollision = false;
			this.reason = "Unknown reason";
		}
		
		public void add(double sx, double sy, double sz, double tx, double ty, double tz, boolean pisCollision, String preason) {
			atSource.add(new Vector3(sx, sy, sz));
			atTarget.add(new Vector3(tx, ty, tz));
			isCollision = isCollision || pisCollision;
			reason = preason;
			if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
				WarpDrive.logger.info("CheckMovementResult " + sx + ", " + sy + ", " + sz + " -> " + tx + ", " + ty + ", " + tz + " " + isCollision + " '" + reason + "'");
			}
		}
	};
	
	private CheckMovementResult checkMovement(int testDistance, boolean fullCollisionDetails) {
		CheckMovementResult result = new CheckMovementResult();
		if ((direction == -1 && maxY + testDistance > 255) && !betweenWorlds) {
			result.add(xCoord, maxY + testDistance, zCoord, xCoord + 0.5D, maxY + testDistance + 1.0D, zCoord + 0.5D, false,
					"Reactor will blow due +high limit");
			return result;
		}
		
		if ((direction == -2 && minY - testDistance <= 8) && !betweenWorlds) {
			result.add(xCoord, minY - testDistance, zCoord, xCoord + 0.5D, maxY - testDistance, zCoord + 0.5D, false, "Reactor will blow due -low limit");
			return result;
		}
		
		int movementVector[] = getVector(direction);
		int lmoveX = movementVector[0] * testDistance;
		int lmoveY = movementVector[1] * testDistance;
		int lmoveZ = movementVector[2] * testDistance;
		
		int x, y, z, newX, newY, newZ;
		Block blockSource;
		Block blockTarget;
		for (y = minY; y <= maxY; y++) {
			newY = y + lmoveY;
			for (x = minX; x <= maxX; x++) {
				newX = x + lmoveX;
				for (z = minZ; z <= maxZ; z++) {
					newZ = z + lmoveZ;
					
					blockSource = worldObj.getBlock(x, y, z);
					blockTarget = worldObj.getBlock(newX, newY, newZ);
					if (Dictionary.BLOCKS_ANCHOR.contains(blockTarget)) {
						result.add(x, y, z,
							newX + 0.5D - movementVector[0] * 1.0D,
							newY + 0.5D - movementVector[1] * 1.0D,
							newZ + 0.5D - movementVector[2] * 1.0D,
							true, "Unpassable block " + blockTarget + " detected at destination (" + newX + ";" + newY + ";" + newZ + ")");
						if (!fullCollisionDetails) {
							return result;
						}
					}
					
					if ( blockSource != Blocks.air
					  && !Dictionary.BLOCKS_EXPANDABLE.contains(blockSource)
					  && blockTarget != Blocks.air
					  && !Dictionary.BLOCKS_EXPANDABLE.contains(blockTarget)) {
						result.add(x, y, z,
							newX + 0.5D + movementVector[0] * 0.1D,
							newY + 0.5D + movementVector[1] * 0.1D,
							newZ + 0.5D + movementVector[2]	* 0.1D,
							true, "Obstacle block #" + blockTarget + " detected at (" + newX + ", " + newY + ", " + newZ + ")");
						if (!fullCollisionDetails) {
							return result;
						}
					}
				}
			}
		}
		
		if (fullCollisionDetails && result.isCollision) {
			return result;
		} else {
			return null;
		}
	}
	
	private static ArrayList<Object> removeDuplicates(List<TileEntity> l) {
		Set<TileEntity> s = new TreeSet<TileEntity>(new Comparator<TileEntity>() {
			@Override
			public int compare(TileEntity o1, TileEntity o2) {
				if (o1.xCoord == o2.xCoord && o1.yCoord == o2.yCoord && o1.zCoord == o2.zCoord) {
					if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
						WarpDrive.logger.info("Removed duplicated TE: " + o1 + ", " + o2);
					}
					return 0;
				} else {
					return 1;
				}
			}
		});
		s.addAll(l);
		return new ArrayList<Object>(Arrays.asList(s.toArray()));
	}
	
	@Override
	protected void readEntityFromNBT(NBTTagCompound nbttagcompound) {
		WarpDrive.logger.error(this + " readEntityFromNBT()");
	}
	
	@Override
	protected void entityInit() {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.warn(this + " entityInit()");
		}
	}
	
	@Override
	protected void writeEntityToNBT(NBTTagCompound var1) {
		WarpDrive.logger.error(this + " writeEntityToNBT()");
	}
	
	public void setMinMaxes(int minXV, int maxXV, int minYV, int maxYV, int minZV, int maxZV) {
		minX = minXV;
		maxX = maxXV;
		minY = minYV;
		maxY = maxYV;
		minZ = minZV;
		maxZ = maxZV;
	}
	
	@Override
	public String toString() {
		return String.format("%s/%d \'%s\' @ \'%s\' %.2f, %.2f, %.2f #%d",
			getClass().getSimpleName(), Integer.valueOf(getEntityId()),
			shipCore == null ? "~NULL~" : (shipCore.uuid + ":" + shipCore.shipName),
			worldObj == null ? "~NULL~" : worldObj.getWorldInfo().getWorldName(),
			Double.valueOf(posX), Double.valueOf(posY), Double.valueOf(posZ),
			Integer.valueOf(ticks));
	}
}
