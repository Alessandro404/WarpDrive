package cr0s.warpdrive.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import cr0s.warpdrive.WarpDrive;

public class BlockIridium extends Block
{
    public BlockIridium(int par1)
    {
        super(par1, Material.rock);
        setHardness(0.8F);
		setResistance(150 * 4);
		setStepSound(Block.soundMetalFootstep);
		setCreativeTab(WarpDrive.warpdriveTab);
		setUnlocalizedName("warpdrive.blocks.IridiumBlock");
    }

    @Override
    public void registerIcons(IconRegister par1IconRegister)
    {
        this.blockIcon = par1IconRegister.registerIcon("warpdrive:iridiumSide");
    }

    @Override
    public int idDropped(int var1, Random var2, int var3)
    {
        return this.blockID;
    }

    @Override
    public int quantityDropped(Random par1Random)
    {
        return 1;
    }
}