# Be My Eyes

[English](README.md) | [简体中文](README.zh-CN.md)

**Two players. One body. You move, your partner sees. Then switch.**

Be My Eyes is a two-player co-op mod for Minecraft Java Edition. The **driver** controls the shared body but cannot see the game world or shared container screens. The **eyes** player sees the body's first-person view and supported container screens but cannot control the body. Roles swap on a timer.

Play in a normal Survival world. No custom map is required.

**Author:** aojiangQAQ（鳌江）

## Requirements

| Component | Version |
| --- | --- |
| Minecraft Java Edition | 1.21.1 |
| Minecraft Forge | Tested with 52.1.16 |
| Java | 21 |
| Be My Eyes | 0.1.4 |

Install the same mod version on **both clients and the dedicated server**, if you use one. For LAN play, both the host and the guest need the mod.

## Installation

1. Install Forge for Minecraft 1.21.1.
2. Download `be-my-eyes-1.21.1-0.1.4.jar` from [Releases](https://github.com/aojiangQAQ/Be-My-Eyes/releases).
3. Place the JAR in each client's `mods` folder and the dedicated server's `mods` folder, if applicable.
4. Remove older versions of the mod before upgrading. Do not install two versions at the same time.

The internal mod ID, config filename and save-data identifiers remain `duosight` for compatibility with earlier versions. The displayed mod name and primary command are now Be My Eyes and `/bemyeyes`.

## Start Playing

Both players must be alive, unpaired, on foot, in Survival mode, in the same dimension and within 32 blocks of each other.

The player whose body you want to share sends an invitation:

```text
/bemyeyes invite YourFriendsName
```

The other player accepts:

```text
/bemyeyes accept
```

The inviter's body, inventory and experience become the shared body. The guest's original items are not merged into it. Ending the session restores the guest's position, inventory, experience and normal controls.

The session ends if the shared body dies or either player disconnects.

## Commands And Controls

| Command or key | Action |
| --- | --- |
| `/bemyeyes invite <player>` | Invite a nearby player to share your body. |
| `/bemyeyes accept` | Accept an active invitation. |
| `/bemyeyes interval <seconds>` | Set the current session's swap interval, from 15 to 3600 seconds, and restart the countdown. |
| `/bemyeyes swap` | Swap roles immediately and restart the countdown. Unavailable during dimension loading. |
| `/bemyeyes status` | Show the current interval and remaining time. |
| `/bemyeyes stop` | End the session. |
| `F8` | End the session immediately. |
| `T` or `/` | Open native chat or command entry. Custom chat key bindings are supported. |
| `ESC` | Close the shared container when driving, or open your personal pause menu. |

The default swap interval is **120 seconds**. Both players see the countdown at the top of their screen. `/duosight` remains a compatibility alias for `/bemyeyes`.

### Chat And Menus

Both roles can use native chat, message history and command completion. Messages and commands are sent as the player who entered them, using that player's normal server permissions.

Personal pause menus, settings and chat do not end the session. Opening them pauses shared-body input and the swap countdown. Closing them resumes play and restores the shared container screen.

The driver can see personal menus and chat, but the world remains blacked out. The eyes player keeps the body's first-person view while chatting.

When an anvil name field or recipe-book search field is focused, the driver's typing goes into that field instead of opening chat. Shared inventory and container interactions remain the driver's responsibility.

## Configuration

After the world has been opened once, edit its `serverconfig/duosight-server.toml`:

```toml
swapSeconds = 120
inviteSeconds = 60
```

| Setting | Meaning | Range |
| --- | --- | --- |
| `swapSeconds` | Default role-swap interval in seconds. | 15–3600 |
| `inviteSeconds` | Invitation lifetime in seconds. | 10–300 |

Restart the world or server after editing the file. `/bemyeyes interval` changes only the current session.

## Languages

Mod messages follow each player's own Minecraft language setting:

- Simplified Chinese, Traditional Chinese (Taiwan) and Traditional Chinese (Hong Kong): **Simplified Chinese**.
- All other languages: **English**.

No server language setting is required.

## Supported Screens And Limitations

The inventory and these 20 vanilla containers have been tested with both roles:

Enchanting table, anvil, grindstone, smithing table, stonecutter, loom, cartography table, beacon, furnace, blast furnace, smoker, brewing stand, chest, barrel, shulker box, hopper, dispenser, dropper, crafter and crafting table.

- **Sign editing, book editing and other non-container screens are not synchronized.**
- Compatibility with modded screens, shaders and camera/input-altering mods is not guaranteed.
- First-person view is enforced and debug views are disabled during a session.
- Network latency still affects control and camera synchronization.
- Back up your world before trying the mod.

## Verification

Version 0.1.4 passed **25 unit tests** and a two-client Forge multiplayer regression run covering:

- Both-role interactions and in-container role swaps across the 20 containers listed above.
- Simultaneous chat, normal messages and commands, history, completion, custom chat keys, container restoration and the driver's blacked-out chat background.
- Eight narrow/wide recipe-book layouts, 24 cursor positions, map previews, anvil rename state and swaps while carrying items.
- Overworld, Nether, End and End return transitions with both roles, personal menus and restoration of inventory, experience and controls after stopping.
- Chinese language variants and English fallback under English, Japanese, French and German settings.

## Build From Source

Install **JDK 21**, then use the included Gradle wrapper.

Windows:

```powershell
.\gradlew.bat build
```

Linux or macOS:

```bash
./gradlew build
```

The mod JAR is written to `build/libs`. Run `gradlew test` to run the unit tests.

`src/smoke` contains development-only verification code. It is not included in the release JAR. Runtime folders, test worlds, logs and local build files are not part of this repository.

## License

**All Rights Reserved.** See [LICENSE.txt](LICENSE.txt). Third-party components retain their original licenses; the Forge notice is in [licenses/Forge-LICENSE.txt](licenses/Forge-LICENSE.txt).
