package me.formax.mod;

import me.formax.mod.utils.FullbrightToggle;
import me.formax.mod.utils.HeadTextureListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;


import java.io.IOException;

@Mod(modid = Main.MODID, version = Main.VERSION, acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class Main {
    public static final String MODID = "formax";
    public static final String VERSION = "1.0";
    @Mod.Instance public static Main INSTANCE;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event){
        MinecraftForge.EVENT_BUS.register(new HeadTextureListener());
        MinecraftForge.EVENT_BUS.register(new FullbrightToggle());


    }



}
