---
name: Project Setup
description: Core project identity, structure, and the FD version mismatch that needs resolving before compilation
type: project
---

mod_id: `cfb_delight`, group: `net.inkyquill`, package: `net.inkyquill.cfbdelight`
NeoForge 1.21.10 (neo_version=21.10.64), Java 21.

**Why:** Bridges Cooking for Blockheads (CfB, multi-loader, Balm framework) with Farmer's Delight (FD). Adds three kitchen blocks: Pot Stand (CookingPotRecipe), Auto Cutter (CuttingBoardRecipe), Grill (CampfireCookingRecipe).

**Critical blocker:** The local FarmersDelight source at `../FarmersDelight` targets MC 1.20.1 Forge. The compat mod targets NeoForge 1.21.10. The FD dependency in `build.gradle` is commented out with two options (local build artifact or CurseForge Maven) — must be filled in before the project compiles.

**How to apply:** When editing build files or discussing compilation errors, check whether the FD dependency has been resolved first. The CfB artifact (`net.blay09.mods:cookingforblockheads-neoforge:21.11.5` from maven.twelveiterations.com) should resolve fine.
