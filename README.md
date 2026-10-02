# Broken World

![Broken World](https://raw.githubusercontent.com/Ernest1101/BrokenWorld/main/cover/cover.png)

**Just survive. But one day you will notice the trees look wrong... and something is watching you.**

A slow-burn horror mod for Minecraft **1.20.1**. It starts as a normal survival world; then the world slowly
breaks, a black silhouette starts watching you, and on the third night it all ends.

- Branch **`main`** – Fabric
- Branch **`forge`** – the same mod for Forge (this branch)

The "wrong" textures are not new art: they are Minecraft's own textures, just on the wrong blocks. The blocks
themselves do not change (an oak log stays an oak log and drops as one) – only the way they are drawn.

## Installation (Forge)
1. **Forge 47+** for Minecraft **1.20.1**.
2. Put `brokenworld-1.0.0-forge.jar` into the `mods` folder.

Multiplayer: the mod is needed on the server **and** on every client.

> ⚠️ By default **the world is deleted at the very end** of the story (singleplayer / the LAN host).
> Play in a new world, or set `deleteWorldAtEnd = false` in the config.

## Stages
| Stage | When | What happens |
|---|---|---|
| 0 | day 1 | a normal game – until the first sunset |
| 1 | day 2 | tree textures get mixed up (in patches, more and more); sounds start to break; the silhouette stands far away, peeks out from behind trees, stands on the canopy |
| 2 | +~0.4 days | every block is mixed up, every sound is broken; it follows you, stands by your house at night, hangs from ceilings, looks in through your window; torches go out, doors open, footsteps, digging sounds |
| 3 | +~0.4 days | different textures on different faces of one block; it stands right behind you; sometimes it rushes at you |
| 4 | day 3 | **the finale** (see below) |

Days follow the in-game clock: sleeping through the night counts too. (1 day = 20 minutes.)

## The first sign (day 1)
At the first sunset, when the sun touches the horizon, **day and night start flickering** – morning, night,
morning, night, at uneven intervals – for **10 seconds**, and then it stays **night**. Once per world, no sound,
no message. (`/brokenworld sunset` shows it now.)

## The world breaks, little by little
The further the stages go, the more often and the worse:
- **Shuffled textures** – real Minecraft textures on the wrong blocks: trees first, then everything.
- **Broken sounds** – every sound plays as another one (always the same swap in a world: a cow may always "moo"
  like a door); now and then a sound stutters, drops out or comes out slow and deep. From stage 2 on, every sound.
- **Faceless mobs** – now and then every mob around loses its face (a smooth, skin-coloured head). The faces come
  back after a minute or two.
- **The world falls apart** (from stage 2): far away, where you are not looking, **holes down to the bottom of the
  world** and **pieces of ground floating in the air** appear. Only natural ground, never near your bed or your builds.
- **Jumpscares in normal play** (from stage 2, at night or in the dark).
- **Animals that act like players** – some cows, pigs, sheep and chickens look normal but behave like players:
  they stare at you, **freeze when you look**, sneak up with jumps when you turn away, sometimes **dig blocks** or
  **pillar up** with dirt.

## Somebody else in the chat
Now and then (from stage 1) another player "joins" your world: the yellow "… joined the game", their name in the
TAB list, and they write in chat – "hi", "do you see it too?", later "i'm at your house", "it's standing behind
you"... If you answer, they repeat your message word for word. Only the player they came for sees them.

**If you play with friends**, it pretends to be one of them instead: it whispers to you **in your friend's name**,
**in their writing style**, sometimes repeating their real old messages. Your friend never wrote any of it.

## Your house is bigger on the inside
Once the world is broken: go more than 48 blocks away from your house (your bed) and come back – inside, it is
**three times wider and twice as tall**: the same walls, windows and floor, your furniture alone in huge rooms.
From outside it looks normal. Walk out through the door and you are outside your door; try to break the walls from
inside and you are suddenly back in your small house.

## It was here
- **Fake disconnect** (from stage 2, once per stage, at night or indoors): the game's real "Connection Lost"
  screen, then "Loading terrain..." – and while you were "gone", torches moved, doors opened, a chest was gone
  through, there is a sign behind you saying "turn around", and you are facing it. Nothing can hurt you meanwhile.
- **Somebody visits your house** (from stage 2, while you are far from your bed): a sign by your bed, a diary in a
  chest written in your name, a shuffled chest, moved torches, open doors, a trail of footprints through the grass
  to your door. Nothing is broken or lost.

## The silhouette
It never attacks you directly and cannot be killed: notice it and look away, stare too long, come close or hit it
– and it is gone.
- **far** – stands far away on the horizon, staring
- **tree_side** – peeks out from behind a tree trunk
- **tree_top** – stands on a treetop, looking down
- **behind** – right behind you; turn around and it vanishes
- **follow** – follows you while you are not looking, freezes when you look (in caves: crawling, or running on all fours like a spider)
- **cave** – stands in a dark cave passage
- **house** – at night, outside by your house
- **ceiling** – hangs upside down from a ceiling (caves, your house)
- **window** – presses its face and hands to the window of the room you are in and taps on the glass
- **rush** – (stage 3) once noticed, it runs at you with its mouth open

Its poses are animated in Blender: peeking around a tree, crawling, the spider walk, hanging upside down, the face
at the window, the jaw falling open. Its head can turn much further than it should, and when it follows you it
moves in jerks, like a lagging player.

## The finale (day 3)
1. **The fall.** The silhouette stops appearing. As you walk, you **sink through the ground** – the camera passes
   through the blocks, then black. The others fall after you.
2. **The tunnel.** Everyone wakes up together at the start of an endless tunnel (dimension `brokenworld:tunnel`).
   It stands far ahead. After **200 blocks** it appears far behind you and follows – slowly at first, then faster
   than you can run.
3. **The maze.** Whoever it catches gets a jumpscare and wakes up in a dark maze. **5 notes** lie in dead ends;
   they must be **thrown into the pit** in the middle (Q).
4. **The hall.** When all 5 notes are in the pit, everyone is moved into a **very long hall** and **cannot move**.
   Far away it stands at a **lectern, reading a book** for **30 seconds**. Then the lectern bursts into particles,
   it **grows twice as big** and **walks straight at you** – slowly, then faster and faster.
5. **The end.** A jumpscare, creaking in the dark, the game leaves the world – and **the world is deleted**
   (config `deleteWorldAtEnd`). On a server, players are just disconnected.

## Commands (for testing; need op / cheats)
- `/brokenworld info` – current stage and days to the next one
- `/brokenworld stage <0-4>` – jump to a stage
- `/brokenworld summon <mode>` – call the silhouette (`far`, `tree_side`, `tree_top`, `behind`, `follow`, `cave`, `house`, `ceiling`, `window`, `rush`)
- `/brokenworld inspect` – a silhouette 3 blocks in front of you that does not vanish; `/brokenworld clear` removes them
- `/brokenworld pose <stand|peek|crawl|hang|spider|window|scream|jerky>` – look at its poses up close
- `/brokenworld freeze [on|off]` – posed silhouettes stop turning towards you
- `/brokenworld screamer` – a jumpscare now
- `/brokenworld crash` – the fake disconnect now
- `/brokenworld visit` – somebody visits your house now (lists what was done)
- `/brokenworld faceless on|off` – faceless mobs
- `/brokenworld glitch hole|floating` – a hole / floating ground nearby
- `/brokenworld playerlike` – the nearest animal starts acting like a player
- `/brokenworld chat` – the stranger joins the chat now
- `/brokenworld sunset` – the first-sunset flicker now
- `/brokenworld corruption <0-1>|reset` – set how broken the textures and sounds are
- `/brokenworld event footsteps|torch_out|door|mining|cave_sound`
- `/brokenworld finale` / `/brokenworld finale stop` – start / stop the finale (⚠️ the end deletes the world)

## Config
`config/brokenworld-common.toml`: days until the world breaks, days between stages and to the finale, the finale
on/off, deleting the world at the end (`deleteWorldAtEnd`), the bigger house (`biggerHouse`), how often it appears
(`appearanceFrequency`), jumpscares, world events, fake chat (`fakeChat`), the fake disconnect (`fakeCrash`), house
visits (`homeVisits`), broken sounds (`brokenSounds`).

## Building
- `gradlew build` – the mod jar in `build/libs`
- `gradlew runClient` – a test client
- `gradlew runGameTestServer` – the GameTests (`FinaleGameTest`): the whole finale, the bigger house, the world falling
  apart, player-like animals, the fake chat, the face at the window, house visits, the fake disconnect, broken sounds.
  (Delete `run/world` first for a clean run.)

## The model (Blender)
The silhouette is a smooth mesh made in **Blender**: `blender/silhouette.blend` (collection `Silhouette`). The body,
arms and legs are skeletons with Skin + Subdivision modifiers (move the points, change the thickness); elbows and
knees are joints. A hunched 2.5-block creature: arms reaching below the knees with three claw fingers, a ridge of
vertebrae, a long skull with deep black eye sockets, a hollow nose, sunken cheeks and a mouth full of needle teeth.

- `tools/blender_body.py` – body, arms, legs; `tools/blender_face.py` – head, jaw, eyes, teeth
- `tools/blender_poses.py` – the rig and the poses (keyframed actions `<pose>__<part>`)
- `tools/blender_export.py` – exports the parts (and simplified far-away copies) as OBJ + `pivots.json` + `poses.json`
- `tools/blender_screamer.py` – renders the jumpscare picture

Change it: open `blender/silhouette.blend`, edit (keep the object names), run `tools/blender_export.py` in Blender
(Scripting → Run Script), then F3+T in game or `gradlew build`.

## Cover and icon
Made in branch `main` (`cover/`, `tools/render_cover.py`, `tools/cover_text.py`).

## Credits
- Jumpscare sound made from [Horror Hit Soundpack 1](https://opengameart.org/content/horror-hit-soundpack-1)
  by **psychhead_** (CC0) – `tools/make_scream.py`.
- Cover font: **Rubik Glitch** (SIL Open Font License).

## License
All Rights Reserved – see [LICENSE](LICENSE).
