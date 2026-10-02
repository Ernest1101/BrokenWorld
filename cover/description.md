# Broken World

**Just survive. But one day you will notice the trees look wrong... and something is watching you.**

Broken World is a slow-burn horror mod. It starts as a completely normal survival world. Then, little by little, the world starts to break, and you realize you are not alone in it.

---

## What happens

**Day 1** – Everything is normal. Almost. Watch the first sunset.

**Day 2** – The world begins to break:
- **Textures go wrong.** Real Minecraft textures end up on the wrong blocks: an oak trunk drawn as diamond ore, leaves as cherry blossom or gold. First the trees, then everything.
- **Sounds break too.** Every sound in the game starts to sound like something else, stutters, drops out or slows down.
- **A black silhouette appears.** Far away on the horizon. Peeking out from behind a tree. Standing on the canopy. Right behind you. It never attacks at first; it only watches. When you look at it, it disappears.

**As the world falls apart:**
- It follows you in caves, crawling or running on all fours like a spider.
- It hangs from the ceiling of your house.
- It presses its face and hands against your window and taps on the glass.
- Mobs lose their faces. Some animals start acting like players.
- Holes open down to the bottom of the world. Pieces of ground float in the air.
- Torches go out, doors open by themselves, you hear footsteps behind you.
- Somebody joins your world and writes in chat. If you play with friends, it pretends to be one of them.
- Your house is bigger on the inside.
- While you are away, somebody visits your house: a sign by your bed, a diary written in your name, things moved around.
- Your game "loses connection"... and when you come back, things are not where you left them.

**On the third night, it ends.** We won't spoil how.

> ⚠️ **Warning:** by default, **the world is deleted at the very end** of the story. Play in a new world, or turn it off in the config (`deleteWorldAtEnd = false`).

---

## Multiplayer

Works in singleplayer, LAN and on servers. Everyone goes through the ending together. The mod must be installed on the server **and** on every client.

---

## Requirements

**Fabric** (1.20.1)
- Fabric Loader 0.15+
- [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
- If you use **Sodium**, also install **Indium** (otherwise the broken textures will not show; everything else works)

**Forge** (1.20.1)
- Forge 47+

---

## Config

`config/brokenworld-common.toml`:
- how many days until the world breaks, and how fast the stages go
- the finale on/off, deleting the world at the end on/off
- fake chat, the bigger house, jumpscares, world events, the fake disconnect, house visits, broken sounds: each on/off
- how often the silhouette and events appear

---

## Commands (for testing, need cheats / op)

- `/brokenworld info` – current stage and time to the next one
- `/brokenworld stage <0-4>` – jump to a stage
- `/brokenworld summon [mode]` – call the silhouette
- `/brokenworld pose <peek|crawl|hang|spider|window|scream|jerky>` – look at its poses up close
- `/brokenworld freeze` – posed silhouettes stop turning towards you
- `/brokenworld screamer`, `/brokenworld crash`, `/brokenworld visit`, `/brokenworld faceless on|off`
- `/brokenworld finale` / `/brokenworld finale stop`

---

## Credits

- Screamer sound made from **"Horror Hit Soundpack 1"** by **psychhead_** (CC0, OpenGameArt)
- Cover title font: **Rubik Glitch** (SIL Open Font License)
- The silhouette was modeled and animated in Blender.
