# 👻 GhostHunt Mod

<div align="center">

[![Release](https://img.shields.io/github/v/release/iForMax/LabyMod-GhostHunt?color=brightgreen&label=Download)](https://github.com/iForMax/LabyMod-GhostHunt/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8.9-blue)](https://files.minecraftforge.net/)
[![Forge](https://img.shields.io/badge/Forge-Required-orange)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**A powerful Forge mod for tracking and collecting ghosts on Laby.net**

[Features](#-features) • [Installation](#-installation) • [Usage](#-usage) • [Commands](#-commands) • [Contributing](#-contributing)

</div>

---

## 📖 Overview

**GhostHunt** is a sophisticated client-side modification for Minecraft 1.8.9 (Forge) designed specifically for the **Laby.net** server. The mod enhances the ghost hunting experience by providing real-time visual indicators and intelligent tracking systems to help players efficiently locate and collect ghosts throughout the map.

### Key Highlights

- 🎯 **Smart Detection System** - Automatically scans and identifies ghost heads within a 32-block radius
- 🔍 **Texture-Based Verification** - Uses advanced pixel comparison to accurately identify genuine ghost heads
- 🌈 **Color-Coded Status** - Visual overlay system for instant ghost status recognition
- 🔥 **Particle Detection** - Network packet interception to detect unclaimed ghosts via flame particles
- 📊 **Progress Tracking** - Comprehensive statistics for monitoring your ghost collection progress

---

## ✨ Features

### Visual Indicators

The mod renders semi-transparent colored overlays on detected ghost heads:

| Color | Status | Description |
|-------|--------|-------------|
| 🟡 **Yellow** | Unknown | Ghost detected but status not yet determined (requires proximity check) |
| 🔴 **Red** | Unclaimed | Ghost has not been collected (flame particles detected) |
| 🟢 **Green** | Claimed | Ghost has been successfully collected (no flame particles) |

### Intelligent Status Detection

- **Proximity-Based Checking**: Approach within **8 blocks** to automatically determine ghost status
- **Flame Particle Monitoring**: Real-time detection of flame particles to identify unclaimed ghosts
- **Persistent State Tracking**: Once a ghost is marked as collected, it remains green unless flames reappear
- **Auto-Claiming**: Automatically marks ghosts as collected when no flame particles are detected nearby

### Performance Optimization

- **Texture Caching**: Loaded ghost textures are cached to minimize disk I/O operations
- **Delayed Processing**: Skull blocks are queued with a 1-second delay before texture verification
- **Efficient Scanning**: Area scanning occurs once per second to reduce CPU overhead
- **Smart Cleanup**: Automatic removal of stale data and non-existent ghost heads

---

## 🚀 Installation

### Prerequisites

- **Minecraft**: Version 1.8.9
- **Forge**: [Download Minecraft Forge 1.8.9](https://files.minecraftforge.net/)
- **Java**: JDK 8 or higher

### Installation Steps

1. **Install Minecraft Forge 1.8.9**
   ```
   Download the installer from https://files.minecraftforge.net/
   Run the installer and select "Install client"
   Launch Minecraft once to create the mods folder
   ```

2. **Download GhostHunt Mod**
   - Navigate to the [Releases page](https://github.com/iForMax/LabyMod-GhostHunt/releases)
   - Download the latest `GhostHunt-x.x.x.jar` file

3. **Install the Mod**

   Place the downloaded `.jar` file in your Minecraft mods directory:

   - **Windows**: `%appdata%\.minecraft\mods`
   - **macOS**: `~/Library/Application Support/minecraft/mods`
   - **Linux**: `~/.minecraft/mods`

4. **Launch Minecraft**
   - Open the Minecraft Launcher
   - Select the **Forge 1.8.9** profile
   - Click "Play"

5. **Verify Installation**
   - Join the **Laby.net** server
   - Ghost heads should automatically be detected and highlighted

---

## 🎮 Usage

### Getting Started

Once installed, the mod works automatically in the background. Simply explore the map, and ghost heads will be detected and marked with colored overlays.

### Keybinds

| Key | Function | Description |
|-----|----------|-------------|
| `G` | Toggle Fullbright | Enables/disables fullbright mode for improved visibility in dark areas |

### Understanding Ghost Status

1. **Initial Detection (Yellow)**
   - When you first enter an area, detected ghosts appear yellow
   - Status is unknown until you get close

2. **Proximity Check (8 blocks)**
   - Approach within 8 blocks of a ghost to trigger automatic status detection
   - The mod checks for flame particles inside the ghost's head

3. **Status Update**
   - **No flames detected** → Ghost turns green (claimed)
   - **Flames detected** → Ghost turns red (unclaimed)

4. **Manual Claiming**
   - Right-click on a ghost head to manually mark it as claimed
   - Useful for correcting status or claiming ghosts immediately after collection

### Technical Notes

> ⚠️ **Network Stability**: If you experience unstable internet connection, the mod may incorrectly identify ghost status due to packet loss affecting flame particle detection. In such cases, use manual claiming by right-clicking ghost heads.

> 💡 **Texture Requirements**: The mod requires ghost texture files to be present in `assets/skins/[XX]/[texture_id]` format. Missing textures will prevent ghost detection.

---

## 💻 Commands

### `/ghoststatus`

Displays comprehensive statistics about your ghost hunting progress.

**Output Information:**
- **Total Ghosts Found (All Time)**: Cumulative count of all unique ghosts discovered during the session
- **Current Ghosts Tracked**: Number of ghost heads currently being monitored in the loaded area
- **Claimed Ghosts**: Count of ghosts you have successfully collected (green)
- **Unclaimed Ghosts**: Count of ghosts still available for collection (red)

**Example Output:**
```
========== GHOST STATUS ==========
Total Ghosts Found (All Time): 47
Current Ghosts Tracked: 23
Claimed Ghosts: 15
Unclaimed Ghosts (With Flames): 8
==================================
```

### `/ghostdebug`

Toggles debug mode for advanced troubleshooting and development purposes.

**Debug Features:**
- Detailed texture loading information
- Texture similarity comparison percentages
- Block detection notifications
- Flame particle detection logs
- State change notifications

**Usage:**
```
/ghostdebug     → Toggles debug mode ON/OFF
```

---

## 🛠️ Configuration

### Adjustable Parameters

The following constants can be modified in the source code for customization:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `SCAN_RADIUS` | 32 blocks | Maximum distance for ghost detection |
| `FLAME_TIMEOUT` | 2000ms | Duration to remember flame particle detection |
| `COMPARE_WIDTH` | 16 pixels | Width of texture region to compare |
| `COMPARE_HEIGHT` | 8 pixels | Height of texture region to compare |
| `SIMILARITY_THRESHOLD` | 95% | Minimum pixel match percentage for ghost identification |

---

## 🔧 Technical Details

### Architecture

The mod consists of several key components:

1. **Event Listeners**
   - `onClientTick`: Handles periodic scanning and state updates
   - `onPlayerInteract`: Processes manual ghost claiming via right-click
   - `onRenderWorld`: Renders colored overlays on ghost heads

2. **Network Monitoring**
   - Custom channel handler for intercepting particle packets
   - Real-time flame particle position tracking
   - Automatic timeout cleanup for stale particle data

3. **Texture Analysis**
   - BufferedImage-based pixel comparison
   - Regional texture matching (top portion of head)
   - Cached texture loading for performance

4. **State Management**
   - Set-based tracking for ghost positions
   - Map-based queuing for delayed processing
   - Persistent state tracking across session

### Ghost Detection Algorithm

```
1. Scan 32-block radius every second
2. Identify skull blocks (Blocks.skull)
3. Queue skulls for delayed checking (1-second delay)
4. Extract GameProfile and texture data
5. Compare texture against known ghost texture IDs
6. Verify 95%+ pixel similarity in comparison region
7. Add to tracked ghost positions if match found
8. Monitor for flame particles when player is nearby
9. Update status based on flame detection
10. Render colored overlay based on final status
```

---

## 🐛 Known Issues

- **Lag on High Ghost Density**: Areas with 50+ ghost heads may experience minor FPS drops
- **Packet Loss Sensitivity**: Unstable connections may cause incorrect status detection
- **Texture Cache Memory**: Large numbers of detected ghosts increase memory usage

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### Reporting Issues

1. Check if the issue already exists in the [Issues](https://github.com/iForMax/LabyMod-GhostHunt/issues) section
2. Provide detailed information:
   - Minecraft version
   - Forge version
   - Mod version
   - Steps to reproduce
   - Screenshots/logs if applicable

### Submitting Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Development Setup

```bash
git clone https://github.com/iForMax/LabyMod-GhostHunt.git
cd LabyMod-GhostHunt
./gradlew setupDecompWorkspace
./gradlew idea  # or ./gradlew eclipse
```

---

## 📊 Version History

See [CHANGELOG.md](CHANGELOG.md) for detailed version history and release notes.

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

### MIT License Summary

✅ Commercial use
✅ Modification
✅ Distribution
✅ Private use

⚠️ License and copyright notice required
❌ Liability
❌ Warranty

---

## 🙏 Acknowledgments

- **Laby.net Community** for testing and feedback
- **Minecraft Forge Team** for the modding framework
- **Mojang Studios** for Minecraft

---

## 📧 Contact & Support

- **Developer**: [@iForMax](https://github.com/iForMax)
- **Issues**: [GitHub Issues](https://github.com/iForMax/LabyMod-GhostHunt/issues)
- **Discussions**: [GitHub Discussions](https://github.com/iForMax/LabyMod-GhostHunt/discussions)

---

<div align="center">

**Made with ❤️ by iForMax**

If you find this mod helpful, consider giving it a ⭐ on GitHub!

[⬆ Back to Top](#-ghosthunt-mod)

</div>