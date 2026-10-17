<div align="center">

# Download Mod

**Search Modrinth and install mods, resource packs, shaders and data packs without leaving Minecraft.**

[![Available for Fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg)](https://fabricmc.net/)
[![Chat with us on Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/ZFfxJevHrg)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)
![Loader](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square)
![Side](https://img.shields.io/badge/Side-Client-blue?style=flat-square)

</div>

---

Download Mod adds a menu to Minecraft where you can search Modrinth and install mods, resource packs, shaders and data packs without leaving the game. It also lists the mods you already have installed, and has a short list of popular servers you can add to your server list.

It is a client-side mod for Fabric on Minecraft 1.21.11. You need **Fabric Loader** and **Fabric API**.

## Table of Contents

- [Using it](#using-it)
- [Features](#features)
- [Safety and network use](#safety-and-network-use)
- [Requirements and limits](#requirements-and-limits)
- [FAQ](#faq)
- [Support](#support)
- [Credits](#credits)

## Using it

There is a **Download Mod** button in the top-left corner of the title screen and of the pause menu. It opens a screen with these tabs:

| Tab | What it does |
| --- | --- |
| **Installed** | The mods you have now, with their icons, descriptions and links. |
| **Mods**, **Resource Packs**, **Data Packs** | Search Modrinth for compatible projects. |
| **Shaders** | Only shown when Iris is installed. |
| **Servers** | A short list of well-known servers. |

Type in the search box and the results update by themselves a moment after you stop typing. Click a result to see its icon, description, screenshots and links. Press **Download**, look over the list of files that will be downloaded, and confirm.

## Features

### Search

Results are limited to projects that support your game version. For mods that also means Fabric. Required dependencies are added to the download automatically, optional ones are not.

### Installed projects and updates

Projects you already have are marked **Installed** in the results, and you cannot download them a second time. If a newer version exists, the button changes to **Update to** followed by the version number. The mod recognises your files by their hash, so it also works for files you downloaded by hand or renamed.

### Versions

The **Versions** button lists every version of a project that fits your Minecraft version, with its type (release, beta, alpha) and date. Pick an older one if the newest does not suit you. If the project is installed, the chosen version replaces it.

### Review before installing

Before anything is downloaded you see every file with its size. Each file has an **x** button, so you can leave out a dependency you do not want. Removing a required dependency shows a warning, because the mod may not start without it.

### Screenshots and links

If the author uploaded images, you can flip through them under the description. Clicking the image (or the **Gallery** button) opens a full-screen viewer with next, previous and close buttons. Below the description there are buttons for the project's Modrinth page and, when the author provided them, its source code, issue tracker, wiki and Discord. Images are drawn at the real resolution of your screen, so they stay sharp at any GUI scale.

### Multi Download

Turn on **Multi Download** and the Download button becomes **Add to Download All list**. Pick as many projects as you like, across tabs, then press **Download All** to fetch everything in one go. Turn Multi Download off again to hide the Download All button. The list is kept until you download it or remove items.

### Data packs

If you are inside a world, data packs go into that world. If you are not, the mod shows your worlds with their icons and names and you pick one. Data packs do not need a restart: type `/reload` in the world (this needs cheats or operator rights), or just open the world later.

### Resource packs and shaders

They are placed in the `resourcepacks` and `shaderpacks` folders. No restart is needed; enable them from the usual screens in the game.

### Mods and restarting

Mods need a restart to load. After installing one, a **Restart Minecraft** button appears. It only does something when you press it and confirm. Nothing restarts by itself.

The restart works by starting the game again with the same launch command, so it comes back with the same profile. On Windows the mod reads the real launch command from the system, because Java cannot do that on its own. It was written for **Modrinth App** and works there on Windows in both restart modes.

Launchers that do not allow this (Prism and MultiMC pass their launch data in a way that cannot be repeated) will show **Close Minecraft** instead.

In Settings you can switch the restart mode between:

- **Attached** (the default): keeps the launcher connected to the game.
- **Detached**: starts the new game on its own. Try it if Attached does not work.

> [!NOTE]
> With Modrinth App the mod leaves out the launcher's own tracking agent when it starts the new game, so the launcher does not follow the restarted game and may show the instance as stopped while the new game is running.

Each restart writes two log files:

- `logs/downloadmod-restart.log` (account tokens hidden)
- `logs/downloadmod-child-output.log` (what the new game printed when it started)

### Replacing files

When you update a mod or pick another version, the new file is downloaded first and the old one is removed afterwards. Windows keeps a running mod locked, so in that case the swap is finished by a small helper program when you close or restart the game. Until then the old version stays active and the new file waits under a name Fabric ignores, so two versions of the same mod are never loaded together.

### Installed tab

Shows every mod with its own icon, description and links. There is a button that opens the mods folder, and a **Mod settings** button for mods that provide a settings screen through the Mod Menu API (Mod Menu has to be installed for that button to work).

### Servers

Fifteen well-known servers with a short description each and a button to add one to your server list. The list was put together from published best-of lists and is not an endorsement. Not every server supports 1.21.11, so check before you join. Server icons are read from the servers themselves, the same way the game's own server list does it. A server that does not send an icon shows its first letter instead.

### Settings

The **Settings** button in the top-right corner lets you:

- turn the Discord and website buttons on the title screen on or off
- turn the update check on or off
- choose the restart mode

Settings are stored in `config/downloadmod.json`.

## Safety and network use

Nothing is downloaded until you confirm. Files are only downloaded from `cdn.modrinth.com`, and each file is checked against the SHA-512 hash that Modrinth's API gives for it. If the hash is missing or does not match, the file is deleted and the install stops.

The mod talks to two Modrinth hosts:

| Host | Used for |
| --- | --- |
| `api.modrinth.com` | Search, project information, versions, and recognising your installed files |
| `cdn.modrinth.com` | The files, and icons and screenshots |

When you open the Servers tab it also connects to the listed game servers directly to read their icons. This is the same request the game's own server list sends, and those servers can see your IP address, as with any server list.

To mark what you already have installed, the mod calculates the SHA-512 hash of each file in your `mods`, `resourcepacks` and `shaderpacks` folders and sends those hashes to `api.modrinth.com`, the same way launchers do. File names, file contents and paths are not sent.

It has **no accounts, no analytics and no telemetry**. Apart from the hashes above, the only things that leave your computer are what you typed in the search box and the usual details of an HTTP request.

On start it also asks Modrinth whether a newer version of Download Mod exists. If there is one, it asks whether you want to open the download page, and it does not install anything itself. This check can be switched off in the settings.

> [!IMPORTANT]
> Download Mod uses Modrinth's public API. It is not made by, and is not affiliated with, Modrinth.

## Requirements and limits

- Minecraft 1.21.11 and Fabric only. There is no Forge, NeoForge or Quilt build.
- Java 21.
- English only.
- It installs from Modrinth. It does not search CurseForge.
- The screenshot preview under a description only appears when the window is tall enough. The Gallery button is always there.
- Installed detection covers mods, resource packs and shaders. Data packs are not checked.

## FAQ

<details>
<summary><b>Does it install things by itself?</b></summary>

No. You always see the list of files and confirm first.

</details>

<details>
<summary><b>Will it restart my game without asking?</b></summary>

No.

</details>

<details>
<summary><b>A restart did not work for me.</b></summary>

Open Settings and switch the restart mode to **Detached**. If it still fails, send the files `logs/downloadmod-restart.log` and `logs/downloadmod-child-output.log` to us on Discord and tell us which launcher you use.

</details>

<details>
<summary><b>Why is my update not active yet?</b></summary>

The old file may still be in use by the running game. Close or restart Minecraft and it is swapped automatically.

</details>

<details>
<summary><b>Where are the settings?</b></summary>

The Settings button inside the Download Mod screen, and the file `config/downloadmod.json`.

</details>

## Support

Questions, bugs and ideas are welcome on our Discord: https://discord.gg/ZFfxJevHrg

[![Chat with us on Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/ZFfxJevHrg)

## Credits

Made by [MishariM12752](https://github.com/MishariM12752).

WebP images are decoded with [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) (BSD 3-Clause), which is bundled inside the mod.
