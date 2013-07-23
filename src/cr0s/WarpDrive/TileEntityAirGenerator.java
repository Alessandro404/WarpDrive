package cr0s.WarpDrive;

import cpw.mods.fml.common.FMLCommonHandler;
import ic2.api.Direction;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergySink;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;

public class TileEntityAirGenerator extends TileEntity implements IEnergySink {
    
    public boolean addedToEnergyNet;
    
    private final int EU_PER_AIRBLOCK = 300;
    private final int MAX_ENERGY_VALUE = 36 * EU_PER_AIRBLOCK;
    private int currentEnergyValue = 0;

    private int cooldownTicks = 0;
    private final float AIR_POLLUTION_INTERVAL = 10;
    
    private final int START_CONCENTRATION_VALUE = 15;

    @Override
    public void updateEntity() {
        if (!addedToEnergyNet && !worldObj.isRemote) {
            MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
            addedToEnergyNet = true;
        }
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            return;
        }
        
        // Air generator works only in spaces
        if (worldObj.provider.dimensionId != WarpDrive.instance.spaceDimID && worldObj.provider.dimensionId != WarpDrive.instance.hyperSpaceDimID) {
            return;
        }

        if (addedToEnergyNet && currentEnergyValue > EU_PER_AIRBLOCK) {
            if (cooldownTicks++ > AIR_POLLUTION_INTERVAL) {
                cooldownTicks = 0;
                worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 1, 2); // set enabled texture

                releaseAir();
            }
        } else
        {
            if (cooldownTicks++ > 20) {
                worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 0, 2); // set disabled texture
                cooldownTicks = 0;
            }
        }
    }
    
    private void releaseAir() {
        if (worldObj.isAirBlock(xCoord + 1, yCoord, zCoord) && (currentEnergyValue - EU_PER_AIRBLOCK >= 0)) {
            worldObj.setBlock(xCoord + 1, yCoord, zCoord, WarpDrive.AIR_BLOCKID, START_CONCENTRATION_VALUE, 2);
            currentEnergyValue -= EU_PER_AIRBLOCK;
        }
        
        if (worldObj.isAirBlock(xCoord - 1, yCoord, zCoord) && (currentEnergyValue - EU_PER_AIRBLOCK >= 0)) {
            worldObj.setBlock(xCoord - 1, yCoord, zCoord, WarpDrive.AIR_BLOCKID, START_CONCENTRATION_VALUE, 2);
            currentEnergyValue -= EU_PER_AIRBLOCK;
        }
        
        if (worldObj.isAirBlock(xCoord, yCoord + 1, zCoord) && (currentEnergyValue - EU_PER_AIRBLOCK >= 0)) {
            worldObj.setBlock(xCoord, yCoord + 1, zCoord, WarpDrive.AIR_BLOCKID, START_CONCENTRATION_VALUE, 2);
            currentEnergyValue -= EU_PER_AIRBLOCK;
        }
        
        if (worldObj.isAirBlock(xCoord, yCoord - 1, zCoord) && (currentEnergyValue - EU_PER_AIRBLOCK >= 0)) {
            worldObj.setBlock(xCoord, yCoord - 1, zCoord, WarpDrive.AIR_BLOCKID, START_CONCENTRATION_VALUE, 2);
            currentEnergyValue -= EU_PER_AIRBLOCK;
        }          
        
        if (worldObj.isAirBlock(xCoord, yCoord, zCoord + 1) && (currentEnergyValue - EU_PER_AIRBLOCK >= 0)) {
            worldObj.setBlock(xCoord, yCoord, zCoord + 1, WarpDrive.AIR_BLOCKID, START_CONCENTRATION_VALUE, 2);
            currentEnergyValue -= EU_PER_AIRBLOCK;
        }
        
        if (worldObj.isAirBlock(xCoord, yCoord, zCoord - 1) && (currentEnergyValue - EU_PER_AIRBLOCK >= 0)) {
            worldObj.setBlock(xCoord, yCoord, zCoord - 1, WarpDrive.AIR_BLOCKID, START_CONCENTRATION_VALUE, 2);
            currentEnergyValue -= EU_PER_AIRBLOCK;
        }      
    }
    
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        
        this.currentEnergyValue = tag.getInteger("energy");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        
        tag.setInteger("energy", this.getCurrentEnergyValue());
    }    
    
    // IEnergySink methods implementation
    @Override
    public int demandsEnergy() {
        return (MAX_ENERGY_VALUE - currentEnergyValue);
    }

    @Override
    public int injectEnergy(Direction directionFrom, int amount) {
        // Избыток энергии
        int leftover = 0;

        currentEnergyValue += amount;
        if (getCurrentEnergyValue() > MAX_ENERGY_VALUE) {
            leftover = (getCurrentEnergyValue() - MAX_ENERGY_VALUE);
            currentEnergyValue = MAX_ENERGY_VALUE;
        }

        return leftover;
    }

    @Override
    public int getMaxSafeInput() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean acceptsEnergyFrom(TileEntity emitter, Direction direction) {
        return true;
    }

    @Override
    public boolean isAddedToEnergyNet() {
        return addedToEnergyNet;
    }

    /**
     * @return the currentEnergyValue
     */
    public int getCurrentEnergyValue() {
        return currentEnergyValue;
    }

    @Override
    public void invalidate() {
        if (addedToEnergyNet) {
            MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
            addedToEnergyNet = false;
        }
        super.invalidate();
    }

}
