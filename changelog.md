# Changelog

## [June 18, 2026] - Modded Fluids and Sodium Compatibility Fixes

- **Modded Fluids Compatibility:** Completely rewrote the fluid detection logic across the entire mod (Decompression Chambers, Ballasts, Electrolyzers, etc.) to use standard NeoForge Fluid Tags (`#minecraft:water`) instead of hardcoded vanilla blocks. The mod is now natively compatible with modded liquids like Terrafirmacraft's saltwater out of the box!
- **Mod Compatibility (Veil/Spotlights):** Fixed a shader compilation crash that occurred when using the "Spotlights or Something" mod (Veil rendering engine) alongside Sodium. Rewrote the Sodium shader injection using MixinExtras `@ModifyReturnValue` to gracefully allow Veil's mixins to apply their shader uniforms first, preventing `undefined variable "lodBias"` OpenGL errors.

## [June 17, 2026] - Performance & Dedicated Server Fixes

### Bug Fixes & Refactoring
- **Dedicated Server Crashes:** 
  - Fixed a critical issue preventing dedicated servers from starting due to `FlowingFluidMixin` and client-side code stripping.
  - Fixed a severe crash during server startup where a client-only rendering class (`SubLevelCrackRenderer`) was referenced within a common network packet, causing the Mixin pre-processor to crash and trigger a cascade failure of dependent mods (like Copycats+ and Create Aeronautics).
- **Decompression Chamber Performance:** Massively improved the decompression chamber's TPS performance during filling and draining by optimizing the compartment BFS (Breadth-First Search) to only run once per tick instead of for every block filled.
- **Fluid Duplication Fix:** Fixed a bug in the decompression chamber's fluid handlers that would incorrectly duplicate or void fluids by scaling transfer rates artificially. The block now correctly respects the 1:1 fluid mechanics.
- **Waterlogged Block Protection:** The decompression chamber will no longer accidentally destroy and replace waterlogged blocks (like slabs and stairs) when attempting to manage water levels inside the compartment.
- **Coordinate Mapping Accuracy:** Fixed a severe bug in `EntityWaterPhysicsMixin` where player coordinates were incorrectly calculated in global world space instead of local sublevel space, breaking airtight submarine suffocation/swimming checks.
- **Memory Leak Prevention:** Patched a static memory leak in the decompression chamber that prevented unloaded or destroyed chamber water blocks from being garbage collected.
## [June 15, 2026] - Barometer, Decompression Chambers & Implosion Mechanics

### New Blocks & Features
- **Create Aeronautics Compatibility:** Officially updated compatibility to fully support the new **Create: Aeronautics 1.3.0** update.
- **Barometer:** Added a new Barometer block and item. It displays the current pressure state relative to your submarine's weakest hull block (Acceptable, Warning, Critical) using a visual pufferfish and detailed tooltips. Mining it correctly requires an Iron Pickaxe.
- **Barometer Display Link:** The Barometer now updates Display Links at an incredibly fast 10 times per second (every 2 ticks), making connected displays instantly responsive to depth changes.
- **Commands:** Added the `/submarine findhole` command to help locate leaks and breached blocks in your submarine hull.
- **Decompression Chambers (WIP):** Enhanced the Ballast Vent to support a "CHAMBER" mode. You can now use ballast tanks to gradually fill or drain sealed airlocks layer by layer. *(Note: The decompression chamber system is currently in development and is currently unusable).*
- **Boat Support (WIP):** Continued groundwork for boat mechanics. *(Note: The boat system is currently in development and is currently unusable).*

### Physics & Implosion Mechanics
- **Smart Hull Breaches:** Breaking a block inside the submarine while under extreme pressure no longer instantly implodes the entire sub. The system now strictly verifies that the broken block is part of the *exterior hull* before triggering a catastrophic failure.
- **Copycat Support:** Added official support for Create and Copycats+ copycat blocks. The pressure system now dynamically reads the copied material to determine maximum depth and cracking behavior.
- **Creative Mode Safety:** Breaking the exterior hull while in Creative Mode no longer triggers an implosion, allowing you to safely build or modify your submarine at any depth.
- **Dynamic Airlock Implosions:** IN WIP; Don't USE Decompression chambers no longer implode at a hardcoded depth of 80 blocks if opened to the ocean without being filled with water first. They now dynamically check your submarine's hull strength and will only implode if you are in a "Warning" or "Critical" pressure state.
- **Visual Hull Cracks:** As your hull approaches its pressure limits, visible cracks will form and water will begin dripping into the submarine.
- **Wrench Repairs:** You can now repair these cracks before the hull gives way by right-clicking on them with the Create Wrench, which will reinforce the block and prevent implosion.
- **Adjusted Pressure Thresholds:** The "Warning" threshold on the Barometer and for the pressure system has been raised to 80% of your weakest hull block's maximum depth (previously 75%), giving you a larger safe margin before things become critical.
- **Config Auto-Reset:** Because the depth calculations and global caps have been entirely overhauled to allow blocks to go much deeper, the `submarine_hull.json` config file will be automatically regenerated (and the old one backed up) the first time you launch this version.

### Bug Fixes & Refactoring
- **Accurate Hull Detection:** Fixed a major bug where unsealed exterior areas (like the surrounding ocean) were evaluated as submarine compartments. This previously caused non-structural exterior blocks, corners, and decorations to incorrectly lower the submarine's total hull strength.
- **Copycat Wrench Priority:** Fixed a conflict where trying to repair a cracked Copycat block with a Wrench would unintentionally strip its applied material. The wrench will now strictly prioritize repairing cracks before allowing the block to be undisguised.
- **Login Desynchronization & Fog Fix:** Fixed an issue where logging into a world inside a submarine temporarily affected players with water physics and thick fog. The compartment scan penalty delay was removed, allowing immediate airtight verification upon chunk loading.
- **Dedicated Server Crash Fixes:** Fixed severe startup crashes on dedicated servers by ensuring optional dependencies (`lithostitched`, `fusion`) are strictly marked as optional in the `mods.toml`, and abstracting client-side rendering elements (`SubLevelCrackRenderer`) from common server Mixins.
- **Pulley Renaming:** Renamed internal references, blocks, and items from "Poulis" to "Pulley" for better clarity.
- **Rendering Fixes:** Implemented rendering fixes for water occlusion and resolved issues causing invisible blocks in production environments when using Veil/Flywheel shaders.

## [June 13, 2026] - Mod Splitting, Sable Physics & Connecting Glass

### Mod Architecture & Splitting
- **The Great Mod Split:** Separated the monolithic codebase into three distinct modular projects to streamline development and structure future content:
  - **Create: Deep Seas:** The main core mod (formerly *Create Submarine*), containing all submarines, buoyancy controllers, depth pressure mechanics, and core underwater tools.
  - **Create: Abyss:** A dedicated mod containing the Abyss dimension, custom deep-sea biomes, bioluminescent plants/fauna, physical lianas, and the PDA overlay namespace.
  - **Create: High Seas:** Initial groundwork added for an upcoming mod focused on boat support, custom sails, and wind dynamics.

### New Blocks & Features
- **Arresting Hook:** Added the Arresting Hook block, block entity, custom item rendering, and creative tab placement for slowing down or docking vessels.
- **Pressurizer Connected Glass:** Added optional support for the Fusion mod, introducing connected glass textures for all pressurized glass variants.

### Physics & Integration
- **Sable Force Queuing:** Fully integrated ballast tanks and floaters with Sable's physical force-queuing system. Buoyancy forces are now calculated block-by-block and submitted as aggregated clusters for smoother physics updates.
- **Sable UI Force Clustering:** Merged multiple ballast and floater indicators into single aggregated points in the submarine diagram UI (displaying total force and count) to avoid screen clutter.
- **Waterwheel Propulsion:** Added support for waterwheels in sublevels. Large waterwheels now dynamically apply thrust and impulses to the sublevel's physical body.

### Performance & Optimizations
- **Airtight Check Caching:** Added per-tick caching to `EntityWaterPhysicsMixin` for airtight compartment checks, preventing redundant compartment lookups when multiple entities check their suffocation status in the same tick.
- **Level-Aware Compartment Lookup:** Optimized `CompartmentTracker` to ignore sublevel entries belonging to different dimensions during containment scans.
- **Efficient Vegetation Clearing:** Reworked the submarine placement clearing code to sweep foliage/kelp by chunk section rather than block-by-block, respecting world height bounds.

### Bug Fixes
- **Pressurizer Glass Translucency:** Changed pressurized glass blocks to inherit from `TransparentBlock`, fixing rendering glitches and allowing other blocks to be properly visible through them.
- **Pocket Fog & Water Culling:** Resolved rendering glitches where fog and water culling wouldn't update properly inside sealed air pockets within sublevels.
- **Sodium Rendering Integration:** Fixed a Sodium water occlusion issue by switching to `RenderSystem` texture binding inside `SodiumWaterOcclusionBridge`.
- **Pressure Crack Repair Syncing:** Reworked wrench repairs to decrement crack stages properly and broadcast block updates with correct block IDs to prevent desyncs.

### Under the Hood
- **Cleaned Bundled Assets:** Removed outdated, temporary bundled files (`temp_aero`) and unused assets to optimize the mod footprint.
- **Project Structure Documentation:** Added a detailed explanation of the project structure to the repository's `README.md`.

### Localization
- **Translations:** Synced and updated keys for English (`en_us`), French (`fr_fr`), Russian (`ru_ru`), and Simplified Chinese (`zh_cn`).

## [June 7, 2026] - Submarine Occlusion & Suffocation Fixes

### Bug Fixes
- **Airtight Submarine Suffocation:** Fixed a critical bug where players would suffocate and lose bubbles while safely inside an airtight submarine. NeoForge's custom fluid type checks (`getEyeInFluidType`) now correctly recognize the submarine's interior as empty, eliminating server/client desyncs and ghost bubbles.
- **Corner & Walking Occlusion Glitches:** Replaced the grid-based position checking with exact decimal (`Vec3`) local-space physics. The water occlusion tolerance has been expanded to include the physical boundaries of the hull itself (`VISUAL_UNION`). Players will no longer start drowning when walking on the floor, getting pushed into walls, or standing in tight corners of a heavily pitched submarine.
- **Swimming Prevention:** The game will now forcefully prevent players from entering swimming mode while inside the submarine's occluded area.

## [June 6, 2026] - Cookiecutter Sharks, Naval Mines, Steel Cable Power & Propellers

### New Blocks & Features
- **Cookiecutter Shark:** Added a new deep-sea predator with its own model, swim/idle animations and a latch-and-struggle attack behavior.
- **Underwater Mines:** Added deployable naval mines that float to hold their depth, arm when a ship or creature gets close, and detonate with a large underwater shockwave — camera shake, launched debris, and area damage.
- **Submarine Propeller:** Added a submarine propeller block with configurable power, client-side bubble/particle wake, and dedicated rendering.
- **Steel Cables & Electrification:** Added steel cable visuals (connector and winch variants, ponder scenes) and a cable electrification system that carries energy along cables, throws sparks, and shocks entities touching a live line.
- **Amphistium:** Added the Amphistium, a glowing schooling fish that spawns in the Abyss biome.

### Configuration & User Interface
- **Update Notifications:** Added an in-game update checker that queries Modrinth and shows a screen when a newer Deep Seas version is available, with the changelog and an "ignore this version" option.
- **Lithostitched Reminder:** Added a startup screen that prompts you to install the recommended Lithostitched dependency when it is missing.
- **Item Tooltips:** Mines, propellers and floaters now show descriptive tooltips.

### Bug Fixes
- **Mines no longer detonate on their own wreckage:** Fixed mines randomly exploding while you broke blocks off them to make them resurface. Each detached chunk becomes a brand-new sublevel spawned right next to the mine, which the proximity trigger mistook for an approaching ship. Debris now inherits the mine's owner at the exact moment of the split and is ignored by the trigger, while real ships still set the mine off.
- **Mines no longer punch holes in the ocean:** Fixed mine explosions spawning their flying-block debris in a way that silently replaced the block at the target world position, carving air pockets into the surrounding water and terrain. Debris is now thrown without ever touching the parent world.
- **Mine depth-keeping no longer lags the server:** Replaced the once-per-second full-volume scan each mine ran (to share buoyancy between mines) with live O(1) tracking, preventing TPS drops on large hulls.
- **Cable energy network spam:** Throttled the block-update packets sent while energy flows through cables (roughly twice a second instead of every tick) to stop network lag.
- **Update checker visibility:** Marked the update-check result fields `volatile` so the render thread reliably sees the values fetched on the background network thread.
- **Corrupt client state file:** Loading an empty or corrupt `create_submarine_client_state.json` no longer throws — it now falls back to defaults.
- **Sturdier cable collisions:** Hardened cable-versus-player collision (sublevel and parent-level handling, null rope-manager guards) and made the winch energy store thread-safe.
- **Pulley concurrency & speed:** Clamped pulley slide speed to a configurable maximum and deferred block destruction/particles to the server tick to avoid concurrency crashes.
- **Fog submersion detection:** Reworked the underwater fog check to skip occluded positions and confirm actual water, fixing incorrect fog states.
- **Reduced particle spam:** Lowered ballast-vent and water-thruster particle counts and frequency.
- **Double-unregister guard:** Submarine state is now only cleared once the driver claim is actually released, preventing double-unregister glitches when several control blocks are present.

### Under the Hood
- **Submarine Driver Registry:** Added a registry that grants a single exclusive "driver" block per submarine (hull controller / oxygen diffuser) using priorities and stale-claim eviction.
- **Mine float guard:** A mine bolted onto a contraption larger than 5 blocks no longer acts as a ballast/floater.
- **Pressure membership helper:** Centralized the "is this position part of the ship" check (`isWithinShip`) used by the pressure system.
- **Hull scan budgeting:** Added dynamic scan delay/budget logic to the hull controller to spread out leak scanning.
- **Access transformer:** Added an access transformer so falling-block debris can be spawned without destructive world side effects.

### Localization
- **Translations:** Updated Simplified Chinese (`zh_cn`) and Russian (`ru_ru`) translations.

## [May 30, 2026] - Persistent Lianas, Fruit Reattachment & Configurable Oceans

### Bug Fixes
- **Lianas Surviving Reload:** Fixed creepvines breaking apart and floating to the surface after leaving and rejoining a world. Their topology (which segment anchors to the seabed, parent/child links, attached fruits) was never actually persisted — the spawn-time block-entity lookup silently failed, so nothing survived a reload. The full chain layout is now saved to a dedicated registry and every physics joint is rebuilt deterministically on load.
- **No More Self-Fighting Lianas:** Fixed lianas jittering, folding and collapsing when bumped into after a reload. Each stacked liana block was running its own physics and stacking buoyancy/player forces several times over; only the segment's centre block now drives the simulation.
- **Fruits Staying Attached:** Fixed creepvine fruits randomly dropping off on reload depending on chunk load order. Each fruit's rest position is now remembered and the fruit is snapped back into place before its joint is rebuilt, so it always reattaches where it belongs.

### Configuration & User Interface
- **Configurable Ocean Depth:** The Deeper Oceans feature is no longer locked to a fixed 10 blocks — a new `deeperOceansDepth` option (default 10, up to 256) lets you choose how far below vanilla the sea floor sits, with an in-config warning that large values can badly hurt world-generation and rendering performance.
- **Deep Seas Welcome Screen:** Added a one-time welcome screen shown in front of the main menu, recommending you set up your Deep Seas preferences before diving in. It offers a button straight to the mod configuration and a "maybe later" button; either choice is remembered in the config TOML so it never shows again.

### Under the Hood
- **Reusable Plant Persistence:** Generalized the liana save system into a plant-agnostic `PlantPhysicsRegistry` that will back future physical plants, made it the single source of truth for segment topology, and dropped the redundant (and unreliable) block-entity NBT copy.

## [May 29, 2026] - Abyss Dimension, Physical Lianas & Big Optimizations

### New Blocks & Features
- **Abyss Dimension:** Reintroduced the custom deep-ocean Abyss dimension with dedicated biome configs and custom worldgen.
- **Physical Lianas & Seeds:** Added creepvines (submarine lianas) and creepvine seeds that grow, flow with water currents, and simulate realistic physics inside sublevels.
- **PDA & Sound Effects:** Added a custom PDA menu overlay and registered new ambient/warning audio cues for Leviathans (roars and class detection warnings).
- **New Diagnostics Command:** Added the `/submarine info` command to inspect current hull integrity, depth, crack counts, and whether your sub is hermetically sealed or breached.
- **Spawning Command:** Added `/submarineliana spawnradius` to spawn creepvines inside a radius with custom density/probabilities.
- **Client RAM Boost:** Allocated 20 GB of RAM for the game client config to handle large sublevel structures smoothly.

### Performance & Optimizations
- **Global LOD System:** Added a new LOD Optimizer (`LianaLODOptimizer`) that automatically pauses/freezes physics and ticking on distant creepvines to save CPU/FPS.
- **Spawning Queue:** Spawning multiple lianas in a radius is now staggered (closest to the player first) and freezes other lianas during creation to prevent lag spikes.

### Bug Fixes
- **Liana Sublevel Lighting:** Fixed an issue where lianas in Sable sublevels rendered pitch black. They now dynamically fetch and reflect the real-world block light values (torches, glowstones, shaders) around them.
- **Precise Pressure Calculations:** Completely reworked pressure depth logic to measure depth block-by-block relative to the actual water surface rather than checking the center of the sub globally.
- **Sinking & Crash Handling:** Added a limit to how many blocks can implode at once to prevent audio/particle lag spikes, and ensured oxygen/life-support blocks are immediately destroyed when a sub sinks.
- **Stability Fixes:** Resolved rare `NullPointerException` and chunk-loading issues in the leak/compartment scanner, and fixed a concurrency deadlock in the Sable snapshot queue.

## [May 27, 2026] - Experimental Branch Updates & Enhancements

### Configuration & User Interface
- **In-Game Mod Configuration UI:** Implemented a new custom split-pane configuration screen (`HullStrengthConfigScreen`) to allow editing block-by-block depth limits and implosion chances directly in-game.
- **Config Persistence:** Changes made in-game are automatically saved to `config/submarine_hull.json` and applied at runtime.

### Pressure Physics & Visual Glitches
- **Refactored Stress Cues:** Restructured pressure calculations to only play metal creaking stress sounds and spawn water dripping particles when a block actually sustains crack or implosion damage. Blocks set to 0% implosion chance are now completely silent and dry.
- **Configurable Floater Limits:** Updated the Floater block to respect customized depth limits set in the configuration instead of using a hardcoded threshold.

### New Items & Create Integration
- **Survival Friendly:** All custom blocks and items (including Phycological Membranes, Pressurized Glasses, and Floater variants) now have proper survival recipes, making the mod fully survival-friendly.
- **Phycological Membrane:** Registered and added the Phycological Membrane item, which can only be crafted by pressing a Kelp block under a Create Mechanical Press.
- **Iron & Copper Pressurized Glasses:** Reworked the generic glass pressurizer block into distinct Iron and Copper variants with new recipes, models, and textures.
- **Colored Floater Variants:** Added crafting recipes for Floater blocks in all vanilla wool colors.

### Abyss Biome & Custom World Generation
- **Abyss Biome:** Introduced a deep ocean Abyss biome ALPHA
- **Biome Modifiers:** Implemented world generation modifiers for amplified, large biomes, and default world types.

### Renders, Compatibility & Performance
- **Early Startup Config Access Fix:** Resolved an initialization crash (`IllegalStateException: Cannot get config value before config is loaded`) in `PermanentWaterCullingTest` that occurred when early-ticking client mods (such as Xaero's Train Map) triggered ticks before NeoForge configurations loaded.
- **Sodium & Veil Shaders:** Integrated critical rendering compatibility mixins for Sodium and Veil pipelines.
- **Sable Network Synchronizations:** Resolved package de-synchronization issues under active tracking states.
- **General Asset Cleanup:** Removed outdated model textures and unused assets to optimize mod footprint.
- **Sable Dependency Upgrade:** Upgraded Sable to version 1.2.2 for improved sub-level physics and performance stability.


## For developers (Modrinth Maven)

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter { includeGroup "maven.modrinth" }
    }
}

dependencies {
    implementation "maven.modrinth:create-deep-seas:2.0.0"
}
```
