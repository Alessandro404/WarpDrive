package blusunrize.immersiveengineering.api;


import blusunrize.immersiveengineering.ImmersiveEngineering;

public class Lib
{
	public static final String[] METALS_IE = {"Copper","Aluminum","Lead","Silver","Nickel","Uranium","Constantan","Electrum","Steel"};
	public static final String[] METALS_ALL = {"Copper","Aluminum","Lead","Silver","Nickel","Uranium","Constantan","Electrum","Steel","Iron","Gold"};
	
	public static final String TOOL_HAMMER = "IE_HAMMER";
	public static final String TOOL_WIRECUTTER = "IE_WIRECUTTER";
	
	public static final String CHAT = "chat."+ImmersiveEngineering.MODID+".";
	public static final String CHAT_WARN = CHAT+"warning.";
	public static final String CHAT_INFO = CHAT+"info.";
	public static final String CHAT_COMMAND = CHAT+"command.";
	
	public static final String DESC = "desc."+ImmersiveEngineering.MODID+".";
	public static final String DESC_INFO = DESC+"info.";
	public static final String DESC_FLAVOUR = DESC+"flavour.";
	
	public static final String GUI = "gui."+ImmersiveEngineering.MODID+".";
	public static final String GUI_CONFIG = "gui."+ImmersiveEngineering.MODID+".config.";

	/**Gui IDs*/
	//Tiles
	public static final int GUIID_Base_Tile = 0;
	public static final int GUIID_CokeOven = GUIID_Base_Tile +0;
	public static final int GUIID_BlastFurnace = GUIID_Base_Tile +1;
	public static final int GUIID_WoodenCrate = GUIID_Base_Tile +2;
	public static final int GUIID_Workbench = GUIID_Base_Tile +3;
	public static final int GUIID_Assembler = GUIID_Base_Tile +4;
	public static final int GUIID_Sorter = GUIID_Base_Tile +5;
	public static final int GUIID_Squeezer = GUIID_Base_Tile +6;
	public static final int GUIID_Fermenter = GUIID_Base_Tile +7;
	public static final int GUIID_Refinery = GUIID_Base_Tile +8;
	public static final int GUIID_ArcFurnace = GUIID_Base_Tile +9;
	//Items
	public static final int GUIID_Base_Item = 64;
	public static final int GUIID_Manual = GUIID_Base_Item +0;
	public static final int GUIID_Revolver = GUIID_Base_Item +1;
	public static final int GUIID_Toolbox = GUIID_Base_Item +2;
	
	public static final int colour_nixieTubeText = 0xff9900;
	
	public static String DMG_RevolverCasull="ieRevolver_casull";
	public static String DMG_RevolverAP="ieRevolver_armorPiercing";
	public static String DMG_RevolverBuck="ieRevolver_buckshot";
	public static String DMG_RevolverDragon="ieRevolver_dragonsbreath";
	public static String DMG_RevolverHoming="ieRevolver_homing";
	public static String DMG_RevolverWolfpack="ieRevolver_wolfpack";
	public static String DMG_RevolverSilver = "ieRevolver_silver";
	public static String DMG_RevolverPotion = "ieRevolver_potion";
	public static String DMG_Crusher="ieCrushed";
	public static String DMG_Tesla="ieTesla";
	public static String DMG_Acid="ieAcid";
	public static String DMG_Railgun = "ieRailgun";
	public static String DMG_Tesla_prim = "ieTeslaPrimary";

	public static boolean BAUBLES = false;
	public static boolean IC2 = false;
	public static boolean GREG = false;
}