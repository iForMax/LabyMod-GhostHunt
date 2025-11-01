package me.formax.mod.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * Main listener class for detecting and tracking ghost heads in Minecraft.
 * Handles texture comparison, particle detection, rendering overlays, and status tracking.
 */
public class HeadTextureListener {

  // Debounce timer to prevent multiple rapid clicks
  private long lastClickTime = 0;

  // Ghost texture IDs to check against (known ghost head texture hashes)
  private static final List<String> GHOST_TEXTURE_IDS = Arrays.asList(
          "9c2a977b735e1685a2b75760664315fbaa7e3bbae215889bf767b53035435800",
          "426ebbe5769ae1524a3d3091984a534da04956c089d146ecab6f2d9304fb617"
  );

  // Enable/disable debug messages
  private static boolean debugMode = false;

  // Tracks ghost heads with flame particles (timestamp of last flame detected)
  private static final Map<BlockPos, Long> flameParticlePositions = new HashMap<>();

  // Set of ghost heads that have been claimed (either clicked or no flames detected when nearby)
  private static final Set<BlockPos> claimedGhostHeads = new HashSet<>();

  // Set of ghost heads that are unclaimed (have flames, not clicked yet)
  private static final Set<BlockPos> unclaimedGhostHeads = new HashSet<>();

  // Timeout duration for flame particle tracking (2 seconds)
  private static final long FLAME_TIMEOUT = 2000;

  // Flag to ensure particle listener is only registered once
  private static boolean particleListenerRegistered = false;

  // Cache for loaded ghost textures to avoid reloading from disk
  private static final Map<String, BufferedImage> ghostTextureCache = new HashMap<>();

  // Set to store positions of all detected ghost heads
  private static final Set<BlockPos> ghostHeadPositions = new HashSet<>();

  // Queue for heads that need to be checked (with timestamp for delayed checking)
  private static final Map<BlockPos, Long> headsToCheck = new HashMap<>();

  // Set to track heads we've already processed to avoid duplicate checks
  private static final Set<BlockPos> processedHeads = new HashSet<>();

  // Statistics tracking - total ghost heads found across all time
  private static int totalGhostsFound = 0;

  // Region to compare (top portion of the head texture)
  // Minecraft head textures are 64x64, the top of the head is typically in the upper portion
  private static final int COMPARE_START_X = 8;
  private static final int COMPARE_START_Y = 0;
  private static final int COMPARE_WIDTH = 16;
  private static final int COMPARE_HEIGHT = 8;

  // Scan radius around player (in blocks)
  private static final int SCAN_RADIUS = 32;

  /**
   * Registers the particle listener to detect flame particles near ghost heads.
   * This listener intercepts network packets to detect when flame particles spawn.
   * Called once during mod initialization or first tick.
   */
  private void registerParticleListener() {
    // Check if already registered to prevent duplicate listeners
    if (particleListenerRegistered) {
      return;
    }

    Minecraft mc = Minecraft.getMinecraft();

    // Ensure network handler is available
    if (mc.getNetHandler() == null || mc.getNetHandler().getNetworkManager() == null) {
      return;
    }

    try {
      // Add custom handler to the network pipeline
      mc.getNetHandler().getNetworkManager().channel()
              .pipeline()
              .addBefore("packet_handler", "particle_listener", new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                  // Check if the packet is a particle packet
                  if (msg instanceof S2APacketParticles) {
                    S2APacketParticles packet = (S2APacketParticles) msg;

                    // Check if it's a flame particle (unclaimed ghost heads spawn flames)
                    String particleName = packet.getParticleType().getParticleName();
                    if (particleName.equals("flame") || particleName.equals("lava")) {
                      double x = packet.getXCoordinate();
                      double y = packet.getYCoordinate();
                      double z = packet.getZCoordinate();

                      // Check if this flame is near any known ghost head
                      BlockPos particlePos = new BlockPos(x, y, z);

                      for (BlockPos ghostPos : ghostHeadPositions) {
                        // Calculate 3D distance between flame and ghost head
                        double distance = Math.sqrt(
                                Math.pow(ghostPos.getX() + 0.5 - x, 2) +
                                        Math.pow(ghostPos.getY() + 0.5 - y, 2) +
                                        Math.pow(ghostPos.getZ() + 0.5 - z, 2)
                        );

                        // If flame is within 0.5 blocks of ghost head, mark it
                        if (distance <= 0.5) {
                          flameParticlePositions.put(ghostPos, System.currentTimeMillis());
                          if (debugMode) {
                            sendMessage("§7[DEBUG] Flame detected at ghost head: " + ghostPos);
                          }
                        }
                      }
                    }
                  }
                  super.channelRead(ctx, msg);
                }
              });

      particleListenerRegistered = true;
      sendMessage("§aParticle listener registered successfully!");
    } catch (Exception e) {
      sendMessage("§cFailed to register particle listener: " + e.getMessage());
    }
  }

  /**
   * Checks if a ghost head position has flame particles nearby (within timeout period).
   * @param pos The block position to check
   * @return true if flames were detected recently, false otherwise
   */
  private boolean hasFlameParticlesNearby(BlockPos pos) {
    if (flameParticlePositions.containsKey(pos)) {
      long lastFlameTime = flameParticlePositions.get(pos);
      return System.currentTimeMillis() - lastFlameTime < FLAME_TIMEOUT;
    }
    return false;
  }

  /**
   * Handles player interaction events (right-click on blocks).
   * Used to detect when player clicks on a ghost head to mark it as claimed.
   */
  @SubscribeEvent
  public void onPlayerInteract(PlayerInteractEvent event) {
    // Only handle right-click on blocks
    if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    // Debounce clicks (prevent multiple triggers within 200ms)
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastClickTime < 200) {
      return;
    }
    lastClickTime = currentTime;

    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null) {
      return;
    }

    BlockPos blockPos = event.pos;
    Block block = mc.theWorld.getBlockState(blockPos).getBlock();

    // Only show debug info if debug mode is enabled
    if (debugMode) {
      sendMessage("§6§l=== Block Information ===");
      sendMessage("§eBlock: §f" + Block.blockRegistry.getNameForObject(block));
      sendMessage("§ePosition: §f" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ());
    }

    // Check if it's a skull/head block
    if (block == Blocks.skull) {
      if (debugMode) {
        sendMessage("§a§lThis is a HEAD/SKULL block!");
      }
      checkHeadTexture(blockPos, debugMode);

      // Mark as claimed when player right-clicks a ghost head
      if (ghostHeadPositions.contains(blockPos)) {
        claimedGhostHeads.add(blockPos);
        unclaimedGhostHeads.remove(blockPos); // Remove from unclaimed if it was there
        sendMessage("§a§lGhost head marked as CLAIMED!");
      }
    }

    if (debugMode) {
      sendMessage("§6§l=====================");
    }
  }

  /**
   * Scans for skull blocks around the player periodically.
   * Runs every tick but only performs scanning every 20 ticks (1 second).
   * Also cleans up old flame particle data and registers the particle listener.
   */
  @SubscribeEvent
  public void onClientTick(TickEvent.ClientTickEvent event) {
    // Only run at the end of the tick phase
    if (event.phase != TickEvent.Phase.END) {
      return;
    }

    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null) {
      return;
    }

    // Register particle listener on first tick
    if (!particleListenerRegistered) {
      registerParticleListener();
    }

    // Clean up old flame positions (remove entries older than timeout)
    flameParticlePositions.entrySet().removeIf(entry ->
            System.currentTimeMillis() - entry.getValue() > FLAME_TIMEOUT
    );

    // Only scan every 20 ticks (1 second) to reduce performance impact
    if (mc.thePlayer.ticksExisted % 20 != 0) {
      return;
    }

    // Get player position for scanning
    BlockPos playerPos = mc.thePlayer.getPosition();

    // Scan for skull blocks in a cube around the player
    for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
      for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
        for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
          BlockPos checkPos = playerPos.add(x, y, z);

          // Skip if already processed to avoid duplicate checks
          if (processedHeads.contains(checkPos)) {
            continue;
          }

          Block block = mc.theWorld.getBlockState(checkPos).getBlock();
          if (block == Blocks.skull) {
            // Add to check queue with timestamp (check after 1 second delay)
            if (!headsToCheck.containsKey(checkPos)) {
              headsToCheck.put(checkPos, System.currentTimeMillis() + 1000);
              if (debugMode) {
                sendMessage("§7[DEBUG] Found skull at " + checkPos + ", queued for checking");
              }
            }
          }
        }
      }
    }

    // Process heads that are ready to be checked
    Iterator<Map.Entry<BlockPos, Long>> iterator = headsToCheck.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<BlockPos, Long> entry = iterator.next();
      BlockPos pos = entry.getKey();
      long readyTime = entry.getValue();

      // Check if enough time has passed (1 second delay)
      if (System.currentTimeMillis() >= readyTime) {
        // Verify block still exists before checking
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == Blocks.skull) {
          checkHeadTexture(pos, false); // Don't show verbose output for automatic scans
          processedHeads.add(pos);
        }
        iterator.remove();
      }
    }
  }

  /**
   * Checks if a head at the given position is a ghost head by analyzing its texture.
   * @param blockPos The position of the skull block to check
   * @param verbose Whether to output detailed debug information
   */
  private void checkHeadTexture(BlockPos blockPos, boolean verbose) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null) {
      return;
    }

    // Get tile entity at the position
    TileEntity te = mc.theWorld.getTileEntity(blockPos);

    if (te instanceof TileEntitySkull) {
      TileEntitySkull skull = (TileEntitySkull) te;

      if (verbose) {
        sendMessage("§eTile Entity: §fTileEntitySkull");
        sendMessage("§eSkull Type: §f" + skull.getSkullType());
      }

      // Get GameProfile (contains texture data for player heads)
      GameProfile profile = skull.getPlayerProfile();

      if (profile != null) {
        if (verbose) {
          sendMessage("§a§lGame Profile Found!");
          sendMessage("§ePlayer Name: §f" + (profile.getName() != null ? profile.getName() : "Unknown"));
          sendMessage("§eUUID: §f" + (profile.getId() != null ? profile.getId().toString() : "None"));
        }

        // Get texture properties from the profile
        Collection<Property> textures = profile.getProperties().get("textures");

        if (textures != null && !textures.isEmpty()) {
          if (verbose) {
            sendMessage("§6§l=== TEXTURE DATA FOUND ===");
          }

          boolean isGhost = false;

          // Iterate through texture properties
          for (Property property : textures) {
            if (verbose) {
              sendMessage("§eProperty Name: §f" + property.getName());
            }
            String base64Value = property.getValue();
            if (verbose) {
              sendMessage("§eBase64 Value: §f" + base64Value);
            }

            // Decode base64 to get texture URL
            try {
              byte[] decoded = Base64.getDecoder().decode(base64Value);
              String decodedJson = new String(decoded);
              if (verbose) {
                sendMessage("§6Decoded JSON: §f" + decodedJson);
              }

              // Extract URL from JSON
              if (decodedJson.contains("\"url\"")) {
                int urlStart = decodedJson.indexOf("\"url\"") + 7;
                int urlEnd = decodedJson.indexOf("\"", urlStart);
                if (urlEnd > urlStart) {
                  String textureUrl = decodedJson.substring(urlStart, urlEnd);
                  if (verbose) {
                    sendMessage("§a§lTexture URL: §f" + textureUrl);
                  }

                  // Extract texture ID from URL (the hash at the end)
                  String textureId = extractTextureId(textureUrl);
                  if (textureId != null) {
                    if (verbose) {
                      sendMessage("§eTexture ID: §f" + textureId);
                      sendMessage("§eChecking texture similarity...");
                    }

                    // Compare texture against known ghost textures
                    if (isGhostTextureByImage(textureId, verbose)) {
                      isGhost = true;
                    }
                  }
                }
              }
            } catch (Exception e) {
              if (verbose) {
                sendMessage("§cCouldn't decode base64: " + e.getMessage());
              }
            }

            // Display signature if present (for debugging)
            if (verbose && property.hasSignature()) {
              sendMessage("§eSignature: §f" + property.getSignature());
            }
          }

          // Display ghost detection result and update tracking sets
          if (isGhost) {
            if (verbose) {
              sendMessage("§d§l  ❂ GHOST DETECTED! ❂");
            }

            // Add to ghost head positions for rendering and tracking
            if (!ghostHeadPositions.contains(blockPos)) {
              ghostHeadPositions.add(blockPos);
              totalGhostsFound++; // Increment total count
              sendMessage("§d§lNew ghost head detected at " + blockPos + " (Total: " + totalGhostsFound + ")");
            }
          } else {
            if (verbose) {
              sendMessage("§7This is not a ghost head.");
            }

            // Remove from ghost head positions if it was there
            ghostHeadPositions.remove(blockPos);
          }

        } else {
          if (verbose) {
            sendMessage("§cNo texture properties found in GameProfile");
          }
        }
      } else {
        if (verbose) {
          sendMessage("§cNo GameProfile found for this skull");
        }
      }
    } else {
      if (verbose) {
        sendMessage("§cNo TileEntitySkull found (te: " + (te != null ? te.getClass().getSimpleName() : "null") + ")");
      }
    }
  }

  /**
   * Extracts the texture ID from a Minecraft texture URL.
   * URL format: http://textures.minecraft.net/texture/[ID]
   * @param textureUrl The full texture URL
   * @return The texture ID (hash), or null if extraction fails
   */
  private String extractTextureId(String textureUrl) {
    if (textureUrl == null) {
      return null;
    }

    // URL format: http://textures.minecraft.net/texture/[ID]
    // Extract the part after the last slash
    int lastSlash = textureUrl.lastIndexOf('/');
    if (lastSlash != -1 && lastSlash < textureUrl.length() - 1) {
      return textureUrl.substring(lastSlash + 1);
    }

    return null;
  }

  /**
   * Checks if the given texture ID matches any ghost texture by comparing image pixels.
   * Loads textures from local storage and compares a specific region.
   * @param textureId The texture ID to check
   * @param verbose Whether to output detailed debug information
   * @return true if texture matches a known ghost texture, false otherwise
   */
  private boolean isGhostTextureByImage(String textureId, boolean verbose) {
    if (textureId == null) {
      return false;
    }

    try {
      // Load the texture to check from local storage
      BufferedImage checkTexture = loadLocalTexture(textureId, verbose);
      if (checkTexture == null) {
        if (verbose) {
          sendMessage("§cFailed to load texture for comparison");
        }
        return false;
      }

      // Compare against each known ghost texture
      for (String ghostId : GHOST_TEXTURE_IDS) {
        BufferedImage ghostTexture = getGhostTexture(ghostId, verbose);
        if (ghostTexture == null) {
          continue;
        }

        // Compare the top portion of the textures (where ghost features are)
        if (compareTextureRegion(checkTexture, ghostTexture, verbose)) {
          if (verbose) {
            sendMessage("§aTexture region matches ghost texture!");
          }
          return true;
        }
      }

      if (verbose) {
        sendMessage("§7Texture region does not match any ghost textures.");
      }
      return false;

    } catch (Exception e) {
      if (verbose) {
        sendMessage("§cError during texture comparison: " + e.getMessage());
      }
      return false;
    }
  }

  /**
   * Gets or loads a ghost texture from cache.
   * Caches textures to avoid repeated disk I/O.
   * @param textureId The texture ID to load
   * @param verbose Whether to output detailed debug information
   * @return The loaded BufferedImage, or null if loading fails
   */
  private BufferedImage getGhostTexture(String textureId, boolean verbose) {
    if (ghostTextureCache.containsKey(textureId)) {
      return ghostTextureCache.get(textureId);
    }

    BufferedImage image = loadLocalTexture(textureId, verbose);
    if (image != null) {
      ghostTextureCache.put(textureId, image);
    }
    return image;
  }

  /**
   * Loads a texture from the local assets folder.
   * Path format: assets/skins/[first 2 chars]/[texture_id]
   * @param textureId The texture ID to load
   * @param verbose Whether to output detailed debug information
   * @return The loaded BufferedImage, or null if loading fails
   */
  private BufferedImage loadLocalTexture(String textureId, boolean verbose) {
    if (textureId == null || textureId.length() < 2) {
      return null;
    }

    try {
      // Get Minecraft data directory
      File mcDir = Minecraft.getMinecraft().mcDataDir;

      // Build path: assets/skins/[first 2 chars]/[texture_id]
      // The first 2 chars are used as a subdirectory for organization
      String firstTwoChars = textureId.substring(0, 2);
      File textureFile = new File(mcDir, "assets/skins/" + firstTwoChars + "/" + textureId);

      if (verbose) {
        sendMessage("§eAttempting to load: §f" + textureFile.getAbsolutePath());
      }

      // Check if file exists
      if (!textureFile.exists()) {
        if (verbose) {
          sendMessage("§cTexture file not found: " + textureFile.getName());
        }
        return null;
      }

      // Load the image using ImageIO
      BufferedImage image = ImageIO.read(textureFile);

      if (image != null && verbose) {
        sendMessage("§aSuccessfully loaded texture: §f" + textureFile.getName());
      } else if (verbose) {
        sendMessage("§cFailed to read image file");
      }

      return image;

    } catch (Exception e) {
      if (verbose) {
        sendMessage("§cFailed to load texture: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Compares a specific region of two textures for similarity.
   * Uses pixel-by-pixel comparison of the defined region (top portion of head).
   * @param img1 First image to compare
   * @param img2 Second image to compare
   * @param verbose Whether to output detailed debug information
   * @return true if textures match (95%+ similarity), false otherwise
   */
  private boolean compareTextureRegion(BufferedImage img1, BufferedImage img2, boolean verbose) {
    // Check if images are valid
    if (img1 == null || img2 == null) {
      return false;
    }

    // Check if the region is within bounds for both images
    if (img1.getWidth() < COMPARE_START_X + COMPARE_WIDTH ||
            img1.getHeight() < COMPARE_START_Y + COMPARE_HEIGHT ||
            img2.getWidth() < COMPARE_START_X + COMPARE_WIDTH ||
            img2.getHeight() < COMPARE_START_Y + COMPARE_HEIGHT) {
      if (verbose) {
        sendMessage("§cTexture too small for region comparison");
      }
      return false;
    }

    // Compare pixels in the specified region (pixel by pixel)
    int matchingPixels = 0;
    int totalPixels = COMPARE_WIDTH * COMPARE_HEIGHT;

    for (int y = 0; y < COMPARE_HEIGHT; y++) {
      for (int x = 0; x < COMPARE_WIDTH; x++) {
        // Get RGB values for both images at the same position
        int rgb1 = img1.getRGB(COMPARE_START_X + x, COMPARE_START_Y + y);
        int rgb2 = img2.getRGB(COMPARE_START_X + x, COMPARE_START_Y + y);

        // Count matching pixels
        if (rgb1 == rgb2) {
          matchingPixels++;
        }
      }
    }

    // Calculate similarity percentage
    double similarity = (double) matchingPixels / totalPixels * 100.0;
    if (verbose) {
      sendMessage("§eSimilarity: §f" + String.format("%.2f", similarity) + "%");
    }

    // Consider it a match if 95% or more pixels match
    return similarity >= 95.0;
  }

  /**
   * Sends a message to the player's chat.
   * @param message The message to send (supports Minecraft color codes)
   */
  private void sendMessage(String message) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(new ChatComponentText(message));
    }
  }
  @SubscribeEvent
  public void onChatSent(ClientChatReceivedEvent event) {
    IChatComponent message = event.message;

    if (message.getFormattedText().startsWith("//ghost")) {
      handleCommand(message.getFormattedText());
      event.setCanceled(true); // Prevent command from being sent to server
    }
  }
  /**
   * Handles custom commands for ghost tracking.
   * Commands:
   * - //ghoststatus: Displays statistics about detected ghost heads
   * - //ghostdebug: Toggles debug mode on/off
   * @param command The full command string
   */
  public void handleCommand(String command) {
    if (command.equalsIgnoreCase("//ghoststatus")) {
      sendMessage("§6§l========== GHOST STATUS ==========");
      sendMessage("§e§lTotal Ghosts Found (All Time): §f" + totalGhostsFound);
      sendMessage("§e§lCurrent Ghosts Tracked: §f" + ghostHeadPositions.size());
      sendMessage("§a§lClaimed Ghosts: §f" + claimedGhostHeads.size());
      sendMessage("§c§lUnclaimed Ghosts (With Flames): §f" + unclaimedGhostHeads.size());
      sendMessage("§6§l==================================");

      if (debugMode) {
        sendMessage("§7[DEBUG MODE: ON]");
      }
    } else if (command.equalsIgnoreCase("//ghostdebug")) {
      // Toggle debug mode
      debugMode = !debugMode;
      sendMessage("§eDebug mode: " + (debugMode ? "§aON" : "§cOFF"));
    }
  }

  /**
   * Renders colored overlays on ghost heads in the world.
   * Colors indicate status:
   * - GREEN: Claimed (either clicked or no flames when nearby)
   * - RED: Unclaimed (flames detected, not clicked yet)
   * - YELLOW: Unknown state (not checked yet)
   */
  @SubscribeEvent
  public void onRenderWorld(RenderWorldLastEvent event) {
    // Don't render if no ghost heads are tracked
    if (ghostHeadPositions.isEmpty()) {
      return;
    }

    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null) {
      return;
    }

    // Calculate player's interpolated position for smooth rendering
    double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
    double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
    double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;

    // Set up OpenGL state for rendering
    GlStateManager.pushMatrix();
    GlStateManager.translate(-playerX, -playerY, -playerZ);

    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableDepth(); // Render through walls
    GlStateManager.disableLighting();

    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldRenderer = tessellator.getWorldRenderer();

    // Remove ghost heads that no longer exist in the world
    Iterator<BlockPos> iterator = ghostHeadPositions.iterator();
    while (iterator.hasNext()) {
      BlockPos pos = iterator.next();

      // Check if block still exists and is still a skull
      Block block = mc.theWorld.getBlockState(pos).getBlock();
      if (block != Blocks.skull) {
        iterator.remove();
        processedHeads.remove(pos);
        claimedGhostHeads.remove(pos);
        unclaimedGhostHeads.remove(pos);
        continue;
      }

      // Create bounding box for the head (1x1x1 block)
      AxisAlignedBB bb = new AxisAlignedBB(
              pos.getX(), pos.getY(), pos.getZ(),
              pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
      );

      // Start drawing quads for this ghost head
      worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

      // Calculate distance to player for proximity checks
      double distanceToPlayer = Math.sqrt(
              Math.pow(pos.getX() + 0.5 - mc.thePlayer.posX, 2) +
                      Math.pow(pos.getY() + 0.5 - mc.thePlayer.posY, 2) +
                      Math.pow(pos.getZ() + 0.5 - mc.thePlayer.posZ, 2)
      );

      // Check current state
      boolean isNearby = distanceToPlayer <= 8.0;
      boolean hasFlames = hasFlameParticlesNearby(pos);
      boolean isClaimed = claimedGhostHeads.contains(pos);
      boolean isUnclaimed = unclaimedGhostHeads.contains(pos);

      // Update state when nearby (automatic state detection)
      if (isNearby) {
        if (hasFlames && !isClaimed) {
          // Mark as unclaimed (RED) if flames detected and not clicked yet
          unclaimedGhostHeads.add(pos);
          if (debugMode) {
            sendMessage("§7[DEBUG] Ghost at " + pos + " marked as UNCLAIMED (flames detected)");
          }
        } else if (!hasFlames && !isClaimed && !isUnclaimed) {
          // Auto-claim (GREEN) if no flames and not marked as anything yet
          claimedGhostHeads.add(pos);
          if (debugMode) {
            sendMessage("§7[DEBUG] Ghost at " + pos + " auto-claimed (no flames)");
          }
        }
      }

      // Set color based on persistent state
      float red, green, blue;

      if (isClaimed) {
        // GREEN: Claimed (no flames when nearby OR manually clicked)
        red = 0.0F;
        green = 1.0F;
        blue = 0.0F;
      } else if (isUnclaimed) {
        // RED: Unclaimed (flames detected, not clicked yet)
        red = 1.0F;
        green = 0.0F;
        blue = 0.0F;
      } else {
        // YELLOW: Unknown state (not checked yet)
        red = 1.0F;
        green = 1.0F;
        blue = 0.0F;
      }
      float alpha = 0.3F; // Semi-transparent

      // Render all 6 faces of the cube with the determined color

      // Bottom face
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();

      // Top face
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();

      // North face
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();

      // South face
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();

      // West face
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();

      // East face
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();

      tessellator.draw();
    }

    // Restore OpenGL state
    GlStateManager.enableDepth();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.popMatrix();
  }
}