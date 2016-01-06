package cr0s.warpdrive.block.detection;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import cr0s.warpdrive.WarpDrive;

public class BlockCloakingCore extends BlockContainer {
	private IIcon[] iconBuffer;
	
	public BlockCloakingCore() {
		super(Material.rock);
		setHardness(0.5F);
		setStepSound(Block.soundTypeMetal);
		setCreativeTab(WarpDrive.creativeTabWarpDrive);
		this.setBlockName("warpdrive.detection.CloakingCore");
	}
	
	@Override
	public void registerBlockIcons(IIconRegister par1IconRegister) {
		iconBuffer = new IIcon[2];
		iconBuffer[0] = par1IconRegister.registerIcon("warpdrive:detection/cloakingCoreInactive");
		iconBuffer[1] = par1IconRegister.registerIcon("warpdrive:detection/cloakingCoreActive");
	}
	
	@Override
	public IIcon getIcon(int side, int metadata) {
		if (metadata < iconBuffer.length) {
			return iconBuffer[metadata];
		}
		
		return null;
	}
	
	@Override
	public TileEntity createNewTileEntity(World var1, int i) {
		return new TileEntityCloakingCore();
	}
	
	/**
	 * Returns the quantity of items to drop on block destruction.
	 */
	@Override
	public int quantityDropped(Random par1Random) {
		return 1;
	}
	
	/**
	 * Returns the item to drop on destruction.
	 */
	@Override
	public Item getItemDropped(int par1, Random par2Random, int par3) {
		return Item.getItemFromBlock(this);
	}
	
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer entityPlayer, int side, float hitX, float hitY, float hitZ) {
		if (world.isRemote) {
			return false;
		}
		
		TileEntity tileEntity = world.getTileEntity(x, y, z);
		if (tileEntity instanceof TileEntityCloakingCore) {
			TileEntityCloakingCore cloakingCore = (TileEntityCloakingCore)tileEntity;
			if (entityPlayer.getHeldItem() == null) {
				WarpDrive.addChatMessage(entityPlayer, cloakingCore.getStatus());
				// + " isInvalid? " + te.isInvalid() + " Valid? " + te.isValid + " Cloaking? " + te.isCloaking + " Enabled? " + te.isEnabled
				return true;
			} else if (entityPlayer.getHeldItem().getItem() == Item.getItemFromBlock(Blocks.redstone_torch)) {
				cloakingCore.isEnabled = !cloakingCore.isEnabled;
				WarpDrive.addChatMessage(entityPlayer, cloakingCore.getStatus());
				return true;
			// } else if (xxx) {// TODO if player has advanced tool
				// WarpDrive.addChatMessage(entityPlayer, cloakingCore.getStatus() + "\n" + cloakingCore.getEnergyStatus());
				// return true;
			}
		}
		
		return false;
	}
	
	@Override
	public void breakBlock(World par1World, int par2, int par3, int par4, Block par5, int par6) {
		TileEntity te = par1World.getTileEntity(par2, par3, par4);
		
		if (te != null && te instanceof TileEntityCloakingCore) {
			((TileEntityCloakingCore)te).isEnabled = false;
			((TileEntityCloakingCore)te).disableCloakingField();
		}
		
		super.breakBlock(par1World, par2, par3, par4, par5, par6);
	}
}
