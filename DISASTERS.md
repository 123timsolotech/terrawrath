# TERRAWRATH — Blockbuster Natural Disasters

> *"The world was never safe. It was just waiting."*

**Mod id:** `terrawrath` · **Target:** Minecraft 26.3 · Forge 26.3-66.0.13 (also loads on Fabric Loader 0.19.5, the loader of the "Claude Disasters" CurseForge instance) · Java 25 · single jar, no dependencies.

## Look & mood

TerraWrath is played like a disaster-movie trailer. Each event has three acts. First the dread: the sky shifts colour, a sub-bass rumble starts, sirens sound and huge warning letters fill the screen. Then the spectacle: something enormous, glowing and loud. Last comes the aftermath: smoke drifting over a wrecked landscape that stays wrecked.

| Token | Colour | Used for |
|---|---|---|
| Ember | `#FF5A1F` | lava, embers, meteors, UI accent |
| Molten Gold | `#FFC23D` | glowing cores, lava bombs, flash |
| Ash Charcoal | `#1B1A1F` | UI panels, ash, smoke |
| Storm Violet | `#7B3FE4` | superstorm, Event Horizon disk edge |
| Glacier Cyan | `#7FE8FF` | blizzard, lightning cores, jets |
| Bone White | `#F4EEE6` | foam, snow, text |

The UI is a dark glass slab with ember-orange hairlines and a hazard-stripe header. Warning titles are huge red/orange letters with a stamped subtitle in the style of an emergency broadcast. Bright things (embers, sparks, lava, lightning, the disk) render **full-bright and additive**, so they glow at night and still read in daylight.

## The disasters — and their money shot

### 1. Tornado — "The Finger of God"
A 160+ block funnel made of rotating, translucent cone shells, dark at the cloud base and dusty at the ground, twisting like a living thing. It crawls across the land, tearing up the ground.
**Money shot:** the funnel crossing a forest as entire trees and real tumbling blocks spiral up its side. A boiling dust skirt sits at its base, and blocks get flung hundreds of blocks away.

### 2. Volcano — "Mountain of Fire"
The ground shakes and an 80+ block cone pushes up out of the earth. Then it erupts: lava fountains, flaming bombs with smoke trails, lava rivers down the flanks, and an ash column that blots out the sun with lightning crackling inside it.
**Money shot:** night-time eruption seen from a distance. Glowing orange fountain, a dozen lava bombs arcing across a black-orange sky, and lightning forking through the ash plume.

### 3. Tsunami — "The Wall"
The sea drains away from the beach, exposing the seabed. Then a 40+ block wall of water with a white foaming crest rolls in over hundreds of blocks, carrying mobs and debris.
**Money shot:** standing on the exposed seabed looking up at a skyscraper of water, foam spraying off its curling lip, seconds before impact.

### 4. Earthquake — "Faultline"
Violent shake, then glowing magma-red cracks race across the ground and tear open into chasms dozens of blocks deep. Dust geysers burst out of them, structures crumble, and aftershocks follow.
**Money shot:** a glowing crack racing straight at the camera and splitting the ground open right under the player's feet.

### 5. Meteor Shower — "Skyfall"
The sky turns an eerie green-teal. Burning meteors streak in at an angle with long fire-and-smoke trails, punching smoking craters full of glowing magma. At the end comes one colossal meteor: a growing shadow on the ground, a whistle, a white-out flash and a shockwave ring.
**Money shot:** the giant final meteor filling the sky with its trail, just before impact, its shadow swallowing the player.

### 6. Lightning Superstorm — "Black Sky"
The sky goes black-purple and a vast rotating cloud deck churns overhead. Lightning never stops: branching bolts, double strikes and fires. Sideways rain and gale-force wind push everything around.
**Money shot:** a ring of five branching bolts striking a hilltop at once, lighting the whole black-purple sky violet-white.

### 7. Blizzard — "Whiteout"
A visible wall of snow rolls across the land. Behind it, water freezes, snow piles up deep and giant ice spikes erupt from the ground. Frost creeps over the screen edges and anyone caught outside freezes.
**Money shot:** the white wall swallowing a village while blue ice spires explode out of the ground in front of it.

### 8. Sinkhole — "The Swallow"
Concentric cracks circle the ground, then it collapses ring by ring into a pit dozens of blocks deep, with dust plumes and debris raining into the dark.
**Money shot:** looking straight down into the freshly opened pit while the last ring falls away and dust rolls up out of the abyss.

### 9. SIGNATURE — "Event Horizon"
The sky darkens to violet and stars come out at noon while gravity starts to hum. Then a black hole opens above the land: a perfectly black sphere wrapped in a blinding, rotating accretion disk (white-hot core fading to orange and violet), with twin cyan relativistic jets firing hundreds of blocks into the sky. It feeds by ripping the terrain upward: real blocks, trees and mobs spiral into the disk and vanish. It ends by collapsing to a point, then a supernova flash and a shockwave that flings everything it was holding outwards and leaves a scarred crater.
**Money shot:** the black hole hanging over the horizon, disk blazing, jets lancing the sky, with a river of real blocks spiralling up off the ground into it.

## Controls
- **Disaster Control Panel** (given on first join, creative tab): one button per disaster with icon and description, intensity slider, Random, Stop All, "Spawn in front of me" toggle, and a Cinematic Camera that hides the HUD and orbits the active disaster.
- **Commands:** `/disaster start <type> [intensity]`, `/disaster stop`, `/disaster random`.
- **Survival:** natural disasters on a timer (config toggle `config/terrawrath.json`). The **Disaster Detector** beeps faster as a disaster approaches and shows a countdown.
