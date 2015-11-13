package cr0s.warpdrive.block.detection;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import cpw.mods.fml.common.FMLCommonHandler;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.data.CameraRegistryItem;
import cr0s.warpdrive.render.ClientCameraHandler;

public class BlockMonitor extends BlockContainer {
	private IIcon iconFront;
	private IIcon iconSide;
	
	public BlockMonitor() {
		super(Material.iron);
		setHardness(0.5F);
		setStepSound(Block.soundTypeMetal);
		setCreativeTab(WarpDrive.creativeTabWarpDrive);
		setBlockName("warpdrive.detection.Monitor");
	}
	
	@Override
	public IIcon getIcon(int side, int parMetadata) {
		int meta = parMetadata & 3;
		return side == 2 ? (meta == 0 ? iconFront : iconSide) : (side == 3 ? (meta == 2 ? iconFront : iconSide) : (side == 4 ? (meta == 3 ? iconFront : iconSide) : (side == 5 ? (meta == 1 ? iconFront : iconSide) : iconSide)));
	}
	
	/**
	 * When this method is called, your block should register all the icons it needs with the given IconRegister. This
	 * is the only chance you get to register icons.
	 */
	@Override
	public void registerBlockIcons(IIconRegister reg) {
		iconFront = reg.registerIcon("warpdrive:detection/monitorFront");
		iconSide = reg.registerIcon("warpdrive:detection/monitorSide");
	}
	
	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entityliving, ItemStack itemstack) {
		int dir = Math.round(entityliving.rotationYaw / 90.0F) & 3;
		world.setBlockMetadataWithNotify(x, y, z, dir, 3);
	}
	
	@Override
	public boolean onBlockActivated(World par1World, int x, int y, int z, EntityPlayer entityPlayer, int par6, float par7, float par8, float par9) {
		// Monitor is only reacting client side
		if (!FMLCommonHandler.instance().getEffectiveSide().isClient()) {
			return false;
		}
		
		// Get camera frequency
		TileEntity tileEntity = par1World.getTileEntity(x, y, z);
		
		if (tileEntity != null && tileEntity instanceof TileEntityMonitor && (entityPlayer.getHeldItem() == null)) {
			int frequency = ((TileEntityMonitor)tileEntity).getVideoChannel();
			CameraRegistryItem cam = WarpDrive.instance.cameras.getCameraByFrequency(par1World, frequency);
			if (cam == null) {
				WarpDrive.addChatMessage(entityPlayer, getLocalizedName() + " frequency '" + frequency + "' is invalid or camera is too far!");
				return false;
			} else {
				WarpDrive.addChatMessage(entityPlayer, "Viewing camera at " + cam.position.chunkPosX + ", " + cam.position.chunkPosY + ", " + cam.position.chunkPosZ + " on frequency " + frequency);
				ClientCameraHandler.setupViewpoint(
						cam.type, entityPlayer, entityPlayer.rotationYaw, entityPlayer.rotationPitch,
						x, y, z, this,
						cam.position.chunkPosX, cam.position.chunkPosY, cam.position.chunkPosZ, par1World.getBlock(cam.position.chunkPosX, cam.position.chunkPosY, cam.position.chunkPosZ));
			}
		}
		
		return false;
	}
	
	@Override
	public TileEntity createNewTileEntity(World world, int i) {
		return new TileEntityMonitor();
	}
}