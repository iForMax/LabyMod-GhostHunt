package me.formax.mod.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class FullbrightToggle {

  private static boolean fullbrightEnabled = false;
  private static float originalGamma = 1.0F;

  // Set the keybind here (default: G key)
  private static final int TOGGLE_KEY = Keyboard.KEY_G;

  @SubscribeEvent
  public void onKeyInput(InputEvent.KeyInputEvent event) {
    // Check if the toggle key was pressed
    if (Keyboard.isKeyDown(TOGGLE_KEY)) {
      toggleFullbright();
    }
  }

  /**
   * Toggles fullbright on/off
   */
  private void toggleFullbright() {
    Minecraft mc = Minecraft.getMinecraft();
    GameSettings settings = mc.gameSettings;

    if (fullbrightEnabled) {
      // Disable fullbright - restore original gamma
      settings.gammaSetting = originalGamma;
      fullbrightEnabled = false;
      sendMessage("§c§lFullbright: §cOFF");
    } else {
      // Enable fullbright - save current gamma and set to max
      originalGamma = settings.gammaSetting;
      settings.gammaSetting = 100.0F; // Maximum brightness
      fullbrightEnabled = true;
      sendMessage("§a§lFullbright: §aON");
    }
  }

  /**
   * Gets the current fullbright state
   */
  public static boolean isEnabled() {
    return fullbrightEnabled;
  }

  /**
   * Manually enable fullbright
   */
  public static void enable() {
    if (!fullbrightEnabled) {
      Minecraft mc = Minecraft.getMinecraft();
      GameSettings settings = mc.gameSettings;
      originalGamma = settings.gammaSetting;
      settings.gammaSetting = 100.0F;
      fullbrightEnabled = true;
    }
  }

  /**
   * Manually disable fullbright
   */
  public static void disable() {
    if (fullbrightEnabled) {
      Minecraft mc = Minecraft.getMinecraft();
      GameSettings settings = mc.gameSettings;
      settings.gammaSetting = originalGamma;
      fullbrightEnabled = false;
    }
  }

  private void sendMessage(String message) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(new ChatComponentText(message));
    }
  }
}