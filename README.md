# Xaero Waypoint ETAs
### Estimated time of arrival to your destination

Renders an ETA label below your looked-at Xaero waypoint while you're moving toward it.

**Requires Xaero's Minimap, Xaero's World Map, and XaeroPlus**

---

## Features

**ETA display:** shows time remaining to reach a waypoint based on your current speed. The label fades as you get close, and can optionally show the waypoint name and distance alongside the ETA.

**Speed tracking:** averages your XZ movement speed over a configurable number of frames. If you're moving too slowly it shows a placeholder text instead of a misleading estimate. You can also lock it to a fixed speed if you know exactly how fast you're travelling.

**Nether highway detection:** if you're flying elytra on the nether roof (Y 115-125), the module automatically uses 40.79 b/s instead of measuring your speed.

**Label positioning:** by default the label floats below the waypoint marker. You can offset it, pin it to the center of the screen, or have its Y position follow your camera pitch.

**Presets:** save and load named configurations from the Presets group in the module settings. Saved presets are stored as JSON files in `rusherhack/config/WaypointETA/presets/` and persist across restarts.  
**Please note that if you want to add a new config, you will need to run the `reload` command in your console or chat to see it in the list**
---

## Settings overview

| Group | What it controls |
|---|---|
| Filters | Which waypoints show a label (temp-only, max range, look cone width) |
| Display | What's shown in the label (name, distance, unknown ETA placeholder) |
| Speed | How speed is measured or overridden |
| Formatting | Font, shadow, color, rainbow gradient, background opacity |
| Position | Label offset, screen anchor, pitch-follow |
| Presets | Save / load / delete named configurations |
