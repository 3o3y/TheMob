🧟 TheMob – Advanced Custom Mobs & Boss System

TheMob is a high-performance, YAML-driven custom mob system for modern Paper / Spigot servers.

It focuses on performance, predictability, and server-owner control.

Designed for:


RPG servers

Survival with depth


Arenas & events


Boss encounters without lag

⚠️ Core philosophy: Performance > Features

✨ Core Features

🧬 Custom Mobs


Fully YAML-based mob definitions

No recompiling required

Attribute scaling, equipment, effects

Clean separation between data & logic


👑 Boss System


Multi-phase boss mobs

Phase logic based on HP percentage

Phase enter / leave effects

BossBars, titles, particles & sounds

Visual elements (crowns, floating heads, scale)


⚡ Auto-Spawn System


Interval-based spawning

Hard caps per spawn point

Chunk-aware hot / cold logic

AFK-farm prevention

Safe cleanup when arenas become inactive


🧭 Navigation HUD (Optional)


Direction HUD via BossBar

Integrated mob radar

Fully configurable

Can be disabled entirely (zero overhead)


🎁 Drops & Items


Advanced drop tables

Legendary / OP drops (optional)

YAML-driven item stats

Designed for RPG progression

🛠️ Performance Design


TheMob is built for long-running servers:


No per-tick heavy logic

Smart throttling

Cached attributes

Chunk-aware behavior

Clean reload handling


➡️ Suitable for production servers, not just test worlds.


📦 Installation


Download TheMob.jar

Place it in your /plugins folder

Start the server once

Edit configs & YAML mobs


📘 Commands


/mob spawn <mob-id>

/mob autospawn <mob-id> <interval-seconds> <max-spawns>

/mob list autospawn

/mob del autospawn <mob-id>

/mob reload

/mob killall

/mob reload


🔐 Permissions


themob.use

themob.spawn

themob.spawn.set

themob.killall

themob.reload

themob.stats


🧩 Addons & Extensions


TheMob is designed to be extended.


✔ Addons supported

✔ Separate API (org.plugin.theMob.api)

✔ Safe to update core without breaking addons


You can create:


Paid addons

Custom boss logic

Extra systems (pets, companions, mechanics)


📄 License


Core Plugin

TheMob core is free to use on any server.


Commercial use is allowed for:


Addons

Configuration packs (YAML)

Services (setup, balancing, support)

❌ Redistribution or resale of the core plugin JAR is not permitted.


See: LICENSE.md



🧠 Design Philosophy


Performance > Features

Predictability > Magic


Server owners first

Players second

Developers respected


No feature bloat.

No forced dependencies.

No client mods.


🧭 Roadmap (Short Overview)


✅v1.1 – Stability & Performance✅

✅v1.2 – Performance Optimization✅

✅v1.2.1 – Stability & Configuration Hotfix✅

✅v1.3 – Boss Phase Depth ✅

v1.4 – Player Feedback & HUD

v1.5 – Combat Extensions

v1.6 – Minions & Summons

v1.7 – World Interaction

v1.8 – AI & Behavior Enhancements

v1.9 – Automation & Scaling

v1.10 – Monitoring & Debugging

v1.11 – Advanced Items & Progression (Optional)

v1.12 – Polishing & Long-Term Support


Core stays lightweight.

Advanced features stay optional.


❤️ Credits


Created by 3o3y

Built with ❤️ for serious server owners.
