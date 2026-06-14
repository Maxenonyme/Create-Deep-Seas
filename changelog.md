# Changelog

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
