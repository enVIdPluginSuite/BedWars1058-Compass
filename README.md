# BedWars1058-Compass

A Hypixel-style compass for [BedWars1058](https://github.com/andrei1058/BedWars1058), written in Kotlin (2.2.21) under `dev.etran`.

The compass sits in the **9th hotbar slot** and has two jobs:

| Your team's bed | The compass is | Right-click does |
| --- | --- | --- |
| alive | **Quick Communication** | opens a menu of quick messages that go to your team only |
| destroyed | **Tracker Shop** | opens the shop where a **player tracker costs 2 emeralds** |

A tracker makes the compass point at one enemy player and shows their distance in the action bar. It is lost when you die, or when your target leaves or is eliminated.

While a match is running this plugin is the **only** thing that decides what a compass does (see [Compass takeover](#compass-takeover)).

## Requirements

* BedWars1058 (any version with the `bedwars-api`), Spigot / Paper 1.8 - latest, Java 8+ (Java 21 on 1.21).
* The Kotlin standard library is bundled and relocated, so it never clashes with another plugin.

## Install

Drop `BedWars1058-Compass-<version>.jar` into `plugins/` and restart. `plugins/BedWars1058-Compass/config.yml` is created on the first start.

Upgrading from 1.x: options that did not exist before are added to your `config.yml` automatically (`compass.name` / `compass.lore` are replaced by `compass.communication.*` and `compass.tracker.*`).

## In-game administration

Everything can be changed without the console. Permission `bwcompass.admin` covers all of it; `bwcompass.reload` and `bwcompass.config` allow just those parts.

| Command | What it does |
| --- | --- |
| `/bwcompass` | help |
| `/bwcompass status` | version, arenas, trackers, locator bar state |
| `/bwcompass reload` | **full reload**: unregisters every listener, cancels every task, rebuilds from `config.yml` (active trackers survive) |
| `/bwcompass reload config` | re-reads and applies `config.yml` only |
| `/bwcompass config list [group]` | browse the options |
| `/bwcompass config get <option>` | show one option |
| `/bwcompass config set <option> <value>` | change an option; it is saved and applied immediately |
| `/bwcompass config reset <option>` | back to the default |
| `/bwcompass config remove <id>` | delete a quick message button |

Tab completion works for every part. Values are type checked (`true`/`false`, numbers, materials, slot ranges) and a wrong value is refused with a message. List options take entries separated by `||`.

Examples:

```
/bwcompass config set tracker.cost.amount 3
/bwcompass config set locator-bar.disable false
/bwcompass config set quick-communication.messages.rush.material GOLD_INGOT
/bwcompass config set quick-communication.messages.rush.message Rush the enemy bed!
/bwcompass config set quick-communication.messages.rush.name &6Rush
/bwcompass config set quick-communication.messages.rush.slot 9
```

Saving rewrites `config.yml`. Servers on 1.18.2+ keep its comments, older servers drop them.

## Quick communication

Until your bed is destroyed, right-clicking the compass opens a menu. Clicking a button sends its message to every member of your team (nobody else sees it). Buttons live under `quick-communication.messages`; each has a `slot`, `material`, `name` and `message`. Message placeholders: `{player}`, `{team}`, `{x}`, `{y}`, `{z}`. The chat line is built from `quick-communication.format`, and `quick-communication.cooldown-seconds` limits spam. Set `quick-communication.enabled: false` and the compass does nothing before bed destruction.

## Player locator bar

Minecraft 1.21.6+ has a player locator bar that replaces the XP bar and shows where players are, which would make the tracker pointless. With `locator-bar.disable: true` (the default) the `locatorBar` game rule is switched off in every arena world and re-checked every second. The original value is restored if you turn the toggle off or the plugin is disabled. On servers without the game rule nothing happens.

## Compass takeover

While an arena is `playing`, every compass in it belongs to this plugin:

1. **First word** - a LOWEST priority listener cancels every compass use (left/right click, air or block, on entities) before other plugins see it, and opens our menu.
2. **Last word** - HIGHEST and MONITOR listeners cancel it again if a plugin un-cancelled it.
3. **Plugins that ignore cancellation** - for `override.suppress-window-ms` (250 ms) after a compass click, swing or punch, any menu, command or plugin teleport aimed at that player is cancelled. A punch with a compass still deals damage.
4. **The item and the target** - a repeating task re-sets the compass target and repairs the item, so nothing another plugin does to it lasts. The compass cannot be dropped or moved into chests and other menus.

Spectators are left alone by default (`override.include-spectators`) because locking them would break BedWars1058's own spectator teleporter.

Limit: a plugin that reacts after the window, or in a way other than a menu, command or teleport, cannot be stopped from outside.

## Build

```
./gradlew build
```

The jar is `build/libs/BedWars1058-Compass-<version>.jar`. It is compiled against the 1.8.8 API with Java 8 bytecode, so one jar loads on every supported version.

## Source map

| File | Job |
| --- | --- |
| `BedWars1058Compass.kt` | startup, shutdown, reloads |
| `Settings.kt` | typed `config.yml`, default merging |
| `MatchService.kt` | BedWars1058 questions (arena, team, bed) |
| `Compasses.kt` | the item and its two modes |
| `QuickCommunication.kt` | the pre-bed team message menu |
| `TrackerMenus.kt`, `MenuListener.kt` | tracker shop, target list, menu clicks |
| `TrackerManager.kt` | active trackers and the repeating task |
| `CompassOverrideListener.kt` | the compass takeover |
| `GameListener.kt` | BedWars1058 lifecycle events |
| `LocatorBar.kt` | locator bar game rule |
| `ConfigEditor.kt`, `PluginCommand.kt` | in-game config editing, `/bwcompass` |
