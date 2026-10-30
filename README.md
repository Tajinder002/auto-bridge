# Auto-Bridge

A Fabric client mod project for **Minecraft Java 1.21.11**. The initial version provides an in-game settings GUI, persistent configuration, keybinds, edge warnings, and a basic bridge-assist toggle.

## Requirements

- Minecraft Java Edition 1.21.11
- Fabric Loader 0.18.1 or newer
- Fabric API for 1.21.11
- Java 21 for development/building

## Build

Install Java 21 and Gradle, then run:

```bash
gradle build
```

The remapped mod JAR is generated in `build/libs/`. GitHub Actions builds the project on pushes, pull requests, and manual workflow runs.

## Controls

- **Right Shift** — open Auto-Bridge GUI
- **B** — toggle Bridge Assist
- **K** — panic switch (disable assistance)

You can change keybinds in Minecraft's Controls menu. Settings are saved to `config/auto-bridge.json`.

## Current implementation

- Dark in-game GUI with module toggles and sliders
- Persistent settings and reset button
- Edge warning while moving near unsupported ground
- Basic movement damping while Bridge Assist is enabled and the player is sneaking
- Panic switch
- Automated build workflow

Aim Assist is currently a settings placeholder; target selection and camera adjustment are not implemented yet. Automatic block placement is also not implemented in this starter version. These features should be developed and tested incrementally in a private server/world before relying on them.

## Server use

Use only where you own the server or have explicit permission to use client automation. Client-side assistance cannot override server-side block placement rules.

## License

MIT
