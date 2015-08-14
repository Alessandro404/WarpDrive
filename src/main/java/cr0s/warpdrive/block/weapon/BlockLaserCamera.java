package cr0s.warpdrive.block.weapon;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import cpw.mods.fml.common.FMLCommonHandler;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.TileEntityLaser;
import cr0s.warpdrive.conf.WarpDriveConfig;
import cr0s.warpdrive.data.CameraRegistryItem;

public class BlockLaserCamera extends BlockContainer {
	private IIcon[] iconBuffer;

	private final int ICON_SIDE = 0;

	public BlockLaserCamera(int texture, Material material) {
		super(material);
		setHardness(0.5F);
		setStepSound(Block.soundTypeMetal);
		setCreativeTab(WarpDrive.creativeTabWarpDrive);
		this.setBlockName("warpdrive.weapon.LaserCamera");
	}

	@Override
	public void registerBlockIcons(IIconRegister par1IconRegister) {
		iconBuffer = new IIcon[1];
		// Solid textures
		iconBuffer[ICON_SIDE] = par1IconRegister.registerIcon("warpdrive:weapon/laserCameraSide");
	}

	@Override
	public IIcon getIcon(int side, int metadata) {
		return iconBuffer[ICON_SIDE];
	}

	@Override
	public TileEntity createNewTileEntity(World parWorld, int i) {
		return new TileEntityLaser();
	}

	/**
	 * Returns the quantity of items to drop on block destruction.
	 */
	@Override
	public int quantityDropped(Random par1Random) {
		return 1;
	}

	/**
	 * Returns the ID of the items to drop on destruction.
	 */
	@Override
	public Item getItemDropped(int par1, Random par2Random, int par3) {
		return Item.getItemFromBlock(this);
	}

	/**
	 * Called upon block activation (right click on the block.)
	 */
	@Override
	public boolean onBlockActivated(World par1World, int x, int y, int z, EntityPlayer par5EntityPlayer, int par6, float par7, float par8, float par9) {
		if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
			return false;
		}

		// Get camera frequency
		TileEntity te = par1World.getTileEntity(x, y, z);
		if (!WarpDrive.instance.isOverlayEnabled && te != null && te instanceof TileEntityLaser && (par5EntityPlayer.getHeldItem() == null)) {
			int beamFrequency = ((TileEntityLaser)te).getBeamFrequency();
			int cameraFrequency = ((TileEntityLaser)te).getCameraFrequency();

			CameraRegistryItem cam = WarpDrive.instance.cameras.getCamByFrequency(par1World, cameraFrequency);
			if (WarpDriveConfig.LOGGING_WEAPON) {
				WarpDrive.logger.info("Camera detected as " + cam);
			}
			WarpDrive.addChatMessage(par5EntityPlayer, getLocalizedName()
					+ ": Beam frequency '" + beamFrequency + "' is " + ((beamFrequency < 0) ? "invalid!" : "valid.")
					+ " Camera frequency '" + cameraFrequency + "' is " + ((cam == null) ? "invalid!" : "valid for laser-camera at " + cam.position.chunkPosX + ", " + cam.position.chunkPosY + ", " + cam.position.chunkPosZ));
			return true;
		}

		return false;
	}
}