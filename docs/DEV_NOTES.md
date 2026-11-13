# Dev notes

Facts found while setting up and testing on the Pleos emulator (2026-10-27).
Things here are **not** in the kickoff brief or contradict it.

## Machine setup (Apple Silicon Mac)

| Item | Value |
| --- | --- |
| Android Studio | 2026.2.1 (Homebrew cask `android-studio`) |
| JDK for command-line builds | 17 (Homebrew `openjdk@17`) |
| SDK platforms | 34, 35 (37 came with the Studio wizard) |
| Emulator | 37.2 |
| System image | `system-images;android-34;pleos_car_emulator;arm64-v8a` (Pleos Connect v2.1.0, 4.3 GB download, ~19 GB unpacked) |
| AVD | `Pleos_Connect_v2.1.0`: cold boot, 4 GB RAM, host GPU, 2560×1440 |

Command-line builds need JDK 17 on the path. Without editing your shell profile:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew installDebug
```

Start the emulator without opening Android Studio:

```bash
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0
```

**Disk:** the image install needs ~18 GB free *at that moment*. macOS "purgeable" space (local
Time Machine snapshots) does not count. The first attempt failed with "No space left on device"
during unzip.

## SDK facts (verified from the SDK binaries and on the emulator)

- **Vehicle SDK: only 2.3.2 (and 2.0.3) exist on the Pleos Maven repo.** The official
  vehicle sample still asks for 2.3.1 and fails to build until bumped to 2.3.2.
- **Pleos `Gear` API is deprecated:** "use the Google GEAR_SELECTION property". We read gear via
  AAOS `VehiclePropertyIds.GEAR_SELECTION` (permission `android.car.permission.CAR_POWERTRAIN`, normal).
- **No vehicle-speed API in the Pleos Vehicle SDK.** Speed comes from AAOS
  `PERF_VEHICLE_SPEED` and needs `android.car.permission.CAR_SPEED`, a **dangerous** (runtime)
  permission. The app asks once, while parked.
- Permissions per domain (from `@RequiresPermission` in the SDK):
  - Door → `pleos.car.permission.CAR_DOORS`
  - Brake, Odometer, DrivingMode, (Gear) → `pleos.car.permission.CAR_DRIVE`
  - NaviHelper get bookmarks/location/route state → `pleos.car.permission.NAVI_ROUTE`
  - NaviHelper requestRoute/cancelRoute → `pleos.car.permission.NAVI_ROUTE_SEARCH`
- Listener nullability: `GearSelectionListener.onSelected(VehicleGear?)`,
  `DistanceDrivenListener.onDistanceDrivenUpdated(Float?)`, `BrakePedalStatusListener.onBrakePedalStatusUpdated(Boolean?)`,
  `DoorPositionListener.onPositionUpdated(Int?)`. All listeners also have `onFailed(Exception)`.
- NaviHelper `onRouteEnded()` is deprecated → use `onRoutingCompleted()`.
- **NaviHelper always returns HOME and OFFICE bookmark slots, even when empty.** An empty slot has
  lat/lon `0.0, 0.0` and a blank name. Routing to it fails in the nav app with
  "경로 탐색 오류 – Invalid Parameter" (`NaviErrorCode.CAUSE_SEARCH_ROUTE`). Check `BookmarkInfo.isSet`.
- `NaviHelperEventListener.onDrivingInfo` carries destination/waypoint state, not speed.

## Found while building HandOver (2026-10-27)

- **Android 14 freezes background apps.** While navigation is in front, car-signal callbacks to
  a background app are queued and delivered in one burst later. Trip recording measured 69 ms of
  driving for a 20 s drive. Fix: `RecordingService` (foreground service, type `specialUse`) runs
  while a HandOver is active. Verified: recorded 0.409 km vs 0.410 km computed from the console.
- Hard-brake detection compares speed samples ≥ 300 ms apart, so a burst of queued events
  can't fake a hard brake (unit-tested).
- After re-subscribing, `GEAR_SELECTION` on the emulator only reports on the next change.
  `CarSource` now also reads the current value once on subscribe.
- `run-as` needs `--user 10` on the Pleos emulator (apps run as user 10), e.g.
  `adb shell "run-as app.daerimanjok --user 10 cat files/datastore/handover.preferences_pb"`.
  Same for `pm grant`; the runtime permission dialog is easier.
- **The emulator can lose internet** after running for hours or across Mac sleep. Navigation
  then shows "경로 탐색 오류" and a grey map. Check with `adb shell ping -c1 8.8.8.8` and restart the
  emulator. Start it as a long-lived process, not from a short shell command, or it gets killed.
  "detected a hanging thread" lines during cold boot are only warnings.

## Virtual Console tips

- `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/
- **Drive:** select **D**, then hold **ACCEL**. Release, hold **BRAKE** to stop, then **P**.
  While moving, `CarUxRestrictions` reports "requires distraction optimization" = yes.
- **Odometer does not advance while driving.** Set it with the slider under
  **Emulation Controls** (the `›` tab on the left edge) → search "Odometer".
- **Emulation Controls** lists every signal the console can publish (door position/lock,
  telltales, brake pedal %, ETCS toll scenarios, …). The search box finds them quickly.
- Mock GPS: `adb emu geo fix <lon> <lat>` (longitude first) moves **Android's** location only.
  **The Pleos navigation app ignores it** (see below), so the car never moves on the nav map.

## Why the nav map never moves on the emulator (checked 2026-10-27)

- The navigation app reads **raw NMEA sentences** (`GpsLocationSource` / `NmeaParser`), not Android's location.
- The emulator re-broadcasts its last fix every second with a **wrong checksum** (always `*47`;
  e.g. `$GPRMC,122913,A,3510.7760,N,…*47` should be `*61`). Navigation rejects every sentence:
  `NMEA checksum mismatch` in logcat. `adb emu geo nmea <valid sentence>` is ignored by this emulator.
- No location control in the Virtual Console, and no simulated-drive mode in navigation settings.
- So navigation stays at its default spot (수정구 시흥동), and **`onDestinationArrived` cannot be triggered
  on the emulator**. Driving in the console (speed) does not move the nav position either.
- `tools/drive_gps.py` builds correct NMEA and would drive a straight-line route if a future emulator
  image accepts `geo nmea`. Keep it for re-testing after image updates.
- App impact: the report falls back to "Parked hh:mm" (last shift into P) when no arrival event came.
  Worth asking the organizers whether the pitch hardware/emulator can simulate a route.

## Verified on the emulator

| Signal / action | Result |
| --- | --- |
| Vehicle SDK init, NaviHelper init | ready |
| Driver door open/close | updates live |
| Gear (AAOS) P ↔ D | updates live |
| Speed (AAOS) | 167 km/h while driving, matches cluster |
| UX restrictions | `yes` while moving → app shows DRIVING, parked-only button disabled |
| Odometer | updates live from console slider |
| Brake pedal | reads `released`; pressed state not yet checked |
| Bookmarks | 2 empty slots (no Home saved on a fresh emulator) |
| requestRoute | Home (test address) → navigation starts guidance; empty Home slot correctly blocked |

## 대리만족 emulator check (2026-11-11)

Debug builds log every NaviHelper callback (`NaviSource.trace`, tag `NaviSource`); release builds don't.

**Navigation (NaviHelper 3.0.5) on the emulator**

| Callback | What arrives |
| --- | --- |
| `onBookmarkInfo` | HOME with lat/lon, name, poiId, `poiSubId = null`, address, `category = HOME`; OFFICE slot empty (0,0) |
| `onCurrentLocationInfo` | lat/lon, `heading = 0`, `address = 수정구 시흥동` (district only), `routeId`, `currentRouteIndex`. Never moves |
| `onCurrentRoadSpeedLimit` | fires often, always **0** (unknown). The app maps ≤ 0 to "no limit" |
| `onRouteStarted` | `duration = 60` s, `distance = 50` m, name |
| `onDrivingInfo` | `destination.distance` / `.duration` = distance and time left; only sent on change, so after an app restart nothing arrives until the car moves |
| `onTBTInfo` | `[TBTInfo(type = TURN_ARRIVE, distance = 50.0, description = 도착)]`; on a real road the description may carry road names (could fill the report's road strip) |
| `onNavigationStatus` | `ROUTE_DRIVE` while guiding |
| `onRouteStateInfo` | mode, route option, EV charge; **no** distance left |
| `onDestinationArrived` | still never fires (GPS doesn't move) |

**Car signals**

| Signal | Emulator |
| --- | --- |
| Speed, gear, UX restrictions (AAOS) | ✅ live from the console |
| Parking brake (AAOS `PARKING_BRAKE_ON`) | ✅ reads; the console has a "Parking Brake" control (not tried yet) |
| Brake pedal (Vehicle SDK) | ✅ pressed while holding the console's BRAKE; 급제동 detected with it |
| EV battery % | ✅ 100 % |
| Fuel % | ❌ `ERROR_NOT_SUPPORTED_PROPERTY` (EV); briefing hides the row |
| Drive mode | ❌ `ERROR_NOT_SUPPORTED_PROPERTY`; briefing hides the row |
| VIN | returns a placeholder ("inva…"), so the car-model lookup falls back to the Grandeur guide |
| Odometer | 500 km after boot, then 0; distance comes from speed × time |

**Live recording from the console** (D, hold ACCEL ~9 s to ~160 km/h, hard BRAKE, P): 과속 (161 km/h,
no limit → 110 rule), 급가속 and 급제동 all detected and shown on the timeline. A long flat-out
acceleration first counted three times; fixed so one continuous hard acceleration/braking counts once.

**Layout**
- Pleos' launcher puts the driving view in one task area (0–853) and every app in the other
  (853–2535). No way found to open navigation and 대리만족 side by side on the emulator: no console
  control, the right-edge handle and a dock long-press don't split (the long-press opens dock editing),
  and FormFactorConsole shows a blank page.
- Narrow column checked by doubling the density (`adb shell wm density 320`, then `wm density reset`):
  S3 switches to the one-column layout. That halves the height too, so the bottom card is cut off;
  the real split column is 840 × 1184 and should fit, but is unverified.
- Dark and light themes checked on every screen.

**Other**
- A trip ended by the driver leaves navigation guiding (cancelling it would bring navigation over the
  "운행 완료" screen). On a real arrival, navigation ends guidance itself.
- A stationary trip records points that all sit in one place; the sketch now shows its placeholder.

## Not done yet

- Arrival event (`onDestinationArrived`): **cannot be tested on this emulator** (nav rejects the
  emulator's GPS; see above). The code path is wired; it needs a real car or a fixed emulator image.
- Narrow (split) layout on a real Pleos screen; see the layout notes above.
- Forgot-PIN recovery: none yet (clearing app data resets it).
- Release keystore (see README → Release APK). Must be created by a team member; never committed.
- Neutral GitHub account + `git init` with neutral `user.name`/`user.email` (blind review).
