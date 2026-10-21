# AR PC Builder

Android AR app that teaches students to build a PC from scratch on a real table, step by step, with an animated close-up of every install and live compatibility checks. When the build is complete, the power button spins the fans, cycles the ARGB lighting and shows a POST screen on a virtual monitor.

**Stack:** Kotlin, Jetpack Compose (Material 3), SceneView 4.52 (Filament + ARCore 1.56), Hilt, MVVM with Clean Architecture.

## Running it
1. Open `D:\AR\PC` in a current Android Studio. Let it sync, and accept its offer to add the Gradle wrapper.
2. Run on an [ARCore-supported device](https://developers.google.com/ar/devices). The emulator can't track planes.
3. Camera permission and the ARCore install prompt are handled by `ARSceneView`'s built-in overlays.

Command-line build (Gradle 9.5 wrapper, JDK 21 or newer, Android SDK platform 37):
```bash
./gradlew assembleDebug testDebugUnitTest
```
If antivirus HTTPS scanning (e.g. Avast) breaks downloads, run with `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` and a JDK that can read the Windows certificate store. Android Studio's bundled JBR can't.

## How to use it
1. Point the phone at an open space and press **Create AR table**. A virtual workbench appears about 75 cm in front of you; no real table needs to be detected.
2. Press the big button for each step. Where a part is needed, a sheet opens to choose it, with a live compatibility hint on every card.
3. Each install plays a short animated scene: the build glides up close to the camera and turns so the slot faces you, the part goes in the way a technician does it, then everything glides back to the table. A caption explains each motion.

| Step | What the animation shows |
|---|---|
| Motherboard | Laid on its anti-static bag on the bench |
| Processor | Retention lever lifts, CPU lowers straight in, lever closes |
| Case | The case pops in beside the bench |
| Into the case | Board lifts, tilts upright, lowers onto the standoffs; four screws spin in |
| Thermal paste | Syringe presses a pea-sized dot onto the CPU |
| CPU cooler | Cooler lowers onto the CPU, fan facing the front |
| Memory | Latches on A2 and B2 open; each stick presses in and its latch clicks shut |
| NVMe SSD | Drive slides in at an angle, is pressed flat and screwed down |
| Graphics card | Slot cover comes off, card pushes into the x16 slot, bracket screw spins in |
| Power supply | PSU slides in from the back, fan down; two screws |
| Cables | CPU fan, 24-pin, 8-pin CPU and GPU power cables run along their routes |
| Power on | Fans spin, ARGB cycles, a POST screen appears on a virtual monitor |

**Guided mode** blocks incompatible parts and explains why. **Sandbox mode** lets them in, so students can see the red compatibility badge and the "No POST" result for themselves. The toolbar has an exploded view, power, and start over.

## Layout
```
app/src/main/java/com/arpcbuilder/
├── domain/                  pure Kotlin, no Android imports
│   ├── model/               PCComponent, AssemblyState (build order), SocketLayout (positions, cable routes)
│   ├── engine/              CompatibilityEngine (sockets, form factor, RAM, clearance, cooling, wattage)
│   └── repository/          ComponentRepository interface
├── data/                    InMemoryComponentRepository + SampleCatalog (22 real-world parts)
├── di/                      Hilt bindings
└── ui/
    ├── ar/                  ARBuilderScreen, BuilderViewModel, InstallDirector (animations), Motion (easing, camera focus), PartNodes
    ├── components/          HUD, parts bottom sheet, BIOS screen
    └── theme/
```

## 3D models
Every part currently renders as a correctly sized placeholder box, with fans and ARGB strips. To use real models, add `app/src/main/assets/models/<name>.glb`, where `<name>` matches the part's `modelUrl` in `SampleCatalog`. Models should:
- be centred on their origin and use metres (they are scaled to the part's largest dimension);
- name any ARGB materials with `rgb` in the name, so the boot simulation drives their emissive colour.

Sketchfab and CGTrader have free PC-part models; check each licence.

## Tests
`./gradlew testDebugUnitTest` runs `CompatibilityEngineTest`: socket, form factor, memory, clearance and PSU rules, the build order, and dual-channel RAM slots.
