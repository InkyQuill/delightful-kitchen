## Current Direction

The canonical roadmap lives in `docs/ROADMAP.md`. This file is historical scratch notes.

The approved standalone design lives in `docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md`.

Implementation is split into milestones. The first milestone builds Kitchen Floor scanning, Chef's Counter shell, and Cabinet of Holding as a merged kitchen inventory terminal. Full recipe planning and the Curios pouch are separate future milestones.

Блоки:

Auto Cutter - крафтится из Kitchen Counter (CfB), Cutting Board (FD), Diamond Knife (FD) - самостоятельно нарезает ингредиенты. необходимо читать все Cutting-рецепты и переделывать их в AutoCutter Recipes. Каждый занимает некоторое время, блок имеет очередь, выдаёт результат в соседние хранилища или в специализированный Cabinet Of Holding, если он есть

B CB B
B KC B
B D B, где B - Brick, D - Diamond Knife, KC - Kitchen Counter, CB - Cutting Board

Cabinet Of Holding - имеет хранилище в размере двух сундуков - собирает в себя результаты из блоков мода. Предлагаю рецепт как Kitchen Counter + Ender Chest + что-то ещё.

Например:
B K B
B E B
B B B , где B - Brick, E - Ender Chest, K - Kitchen Counter

Grill - замена сковородки FD - требует топлива. Делается из Kitchen Counter, блока печки из FD, и сковородки FD - самостоятельно обжаривает ингредиенты. Необходимо читать все рецепты сковородки и переделывать их в Grill Recipes. Используется время, топливо, есть очередь. Выдача в Cabinet of Holding, если есть, или в соседние инвентари.

B S B
B K B
B O B , где B - Brick, S - сковородка из FD, K - Kitchen Counter, O - печка из FD

Pot Stand - замена Pot из FD - требует топлива. Делается из Kitchen Counter, блока печки из FD, и Cooking Pot из FD - самостоятельно варит. Используем рецепты Cooking Pot. Используется время, топливо, есть очередь. Если рецепту требуется контейнер, пытается искать Container Storage, и брать оттуда, или же из других инвентарей Kitchen. Выдача так же, в Cabinet of Holding или соседние инвентари

B P B
B K B
B O B , где B - Brick, P - Cooking Pot, K - Kitchen Counter, O - печка из FD

Container Storage - хранилище для контейнеров - бутылок, мисок. Делается из Kitchen Counter + Chest + Glass Bottle + Bowl.

B K B
G C G
B W B , где B - Brick, K - Kitchen Counter, W - Wooden Bowl, G - Glass Bottle, C - Chest

Fuel Provider - автоматически передаёт топливо в любые блоки, которым нужно топливо - в том числе и в стандартную плиту CfB, если она требует топливо (то есть, там нет адаптера для электричества)

## Next Milestones

- Chef's Counter recipe book UI and details panel.
- Confirmation and station queue execution.
- Capacity-aware station planning for busy queues and blocked outputs.
- Standalone station interfaces and removal of Cooking for Blockheads APIs.
- Persistent Kitchen Floor camouflage.
- Curios pouch.
