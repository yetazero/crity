Crity — Dynamic Combat Damage & Target HUD

Crity brings an immersive, modern combat experience to Hytale! It replaces static damage indicators with dynamic tiered floating numbers, critical hit popups, and introduces a clean, layered top-screen Target Health Bar with smooth phantom health drain.

DISCLAIMER & NOTE
I am not a professional developer! I create mods purely as a hobby and for fun. Because this mod hooks into internal damage events, custom UI pipelines, and game reflection systems, you might encounter occasional visual quirks or minor glitches. Feel free to experiment, test it out, and leave feedback in the comments!

FEATURES

Dynamic Damage Tiers & Critical Popups
Damage numbers dynamically scale in color, size, and animation depending on how hard you hit relative to your weapon's damage range:
• Cyan (< 25% weapon range): Light glancing blow.
• Green (25% – 55% weapon range): Standard solid strike.
• Yellow (55% – 80% weapon range): Heavy hit.
• Red + CRIT! (≥ 80% weapon range): Massive critical hit with an additional explosive "CRIT!" popup!

Layered Target Health Bar
• Phantom Damage Trail: Instant red health bar drop with a smooth orange phantom trail that gracefully drains down.
• Exact HP Numbers: Clear and precise numerical health readout (Current HP / Max HP).
• Smart Auto-Hide: Automatically fades away after 5 seconds of combat inactivity.
• Keeps entity heads clean by moving target health to an elegant top-centered HUD.

COMMANDS & CUSTOMIZATION

Every player can independently configure their preferences. All settings are automatically saved and persist across server restarts!

• /crity
View your current display settings.

• /crity damage [on | default | off]
Switch between Custom Tiers, Vanilla White, or Disabled.

• /crity health [on | default | off]
Switch between HUD, Vanilla 3D Bar, or Disabled.
