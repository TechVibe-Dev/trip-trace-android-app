# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Fixed the live map's current-position marker staying frozen at the trip's origin during a real drive — `rememberMarkerState` only sets position on first creation, later updates were silently ignored. Lowered the GPS recording interval 10s/5s → 3s/1.5s, improving both update lag and how closely the recorded route polyline follows the actual street. Found during the first real driving test. ([#75](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/75))
- Applied a dark map style (Google's official "Night Mode" JSON) to both Google Maps instances (History's route map, the live trip map) — previously the light default palette clashed with the rest of the (dark-themed) app. ([#70](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/70))
- Active trip screen now shows a real live map, full-screen with the stat cards floating on top (semi-transparent) instead of stacked above it — current position (a rotating navigation arrow matching recorded bearing, instead of a generic pin), route recorded so far, and origin/destination/stop markers, sourced from Room in real time (not tied to the 30s API sync), camera following like a navigation app. The stops card is now always shown, with the destination as a permanent last row (previously hidden entirely when a trip had no intermediate stops). Fixed a stop-creation failure in Create trip being completely silent — now logged, so it's diagnosable via Logcat instead of vanishing without a trace. Closes `android#7`. ([#67](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/67))
- Active trip screen now polls every 30s: syncs unsynced GPS points (Room → API), refreshes the live ETA (`POST /trips/{id}/recalculate-eta`) and stop progress (`GET /trips/{id}/stops`, `actual_arrival_at` detected server-side). Replaces the remaining mock data on this screen. Best-effort throughout — a failed tick just retries on the next one. ([#66](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/66))

## [0.2.0] - 23 Sep 2026

- Replaced the "Mapa de la ruta" placeholder in History with a real map (Google Maps SDK + `maps-compose`), showing each trip's actual recorded GPS path (`GET /trips/{id}/gps-points`), not the planned route. Loaded lazily, only when a card is expanded. ([#62](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/62))
- Debug builds now sign with a fixed, committed keystore instead of an auto-generated one, giving a stable SHA-1 across CI runs and local machines — needed to restrict Google API keys (e.g. Maps SDK, `android#57`) to this app. ([#61](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/61))
- Fixed stops being silently discarded on save — Create trip now geocodes each one and creates it via `POST /trips/{id}/stops` (`type: PLANNED`). Validates the destination and all stops up front; if any address can't be resolved, nothing is created. Departure time now defaults to the current time instead of a hardcoded 18:30; desired arrival defaults to empty instead of a hardcoded 19:15 (it's optional, an invented default didn't make sense). ([#60](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/60))
- Create trip now geocodes the typed destination (Android's built-in `Geocoder`, no API key) instead of always using placeholder coordinates. Shows an error and doesn't save if the address can't be resolved. ([#56](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/56))
- Sync: recorded GPS points now upload to the API (`POST /trips/{id}/gps-points`) when a trip finishes, before calling `/finalize` — `distance_km`/`max_speed`/`min_speed`/`avg_speed` are computed from real data instead of always coming back null. Best-effort: a failed upload leaves points unsynced in Room for a later retry, without blocking the trip from being marked `COMPLETED`. ([#55](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/55))
- Active trip screen now shows real current speed (live, via a Room `Flow`) and real departure time — replaces two of the mock stat cards. ETA and stop progress still mock, pending `api#7`. ([#51](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/51))
- Fixed History showing timestamps in UTC labeled as local time (could even show the wrong day for a trip that ended late at night). Same bug fixed in the still-open PR #50 (Viajes) and #51 (Viaje en tiempo real). ([#53](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/53))
- Create trip now calls `POST /trips/{id}/calculate-route` after saving (best-effort — a failure there doesn't block saving the trip). Wired the "Usar hora actual" button on stale planned trips (previously a no-op) to `PATCH planned_departure_at`. ([#50](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/50))
- Fixed CI: `android-actions/setup-android@v4` defaults to installing the deprecated `tools` SDK package, which Google stopped serving mid-September — broke every build. We don't need it (Gradle resolves its own SDK components), so `packages: ''` skips installing it.
- Connected History screen to real API data (`GET /trips?status_filter=COMPLETED`) — distance, max/avg speed, duration. ([#49](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/49))
- Added Room local database and a foreground service that records GPS points during an active trip (`TripTrackingService`), plus a "Finalizar viaje" action. Fixed a crash on "Iniciar" caused by `GpsPointEntity`'s foreign key having no matching `TripEntity` row — the trip is now fetched and saved to Room before tracking starts. Fixed duplicate/leaked GPS points across trips: the started Service gets reused by Android across `start()` calls, so `onStartCommand()` now removes any previous `LocationCallback` before registering a new one. ([#26](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/26))
- Configured Dependabot (gradle + github-actions), monthly. ([#28](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/28), [#47](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/47))
- Wired real GPS location into Create trip (runtime permission + `FusedLocationProviderClient`). ([#25](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/25))
- Connected Create trip and Trips screens to the real API (create, list planned, start). ([#24](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/24))
- Backport workflow now pushes a dedicated branch instead of using `main` directly as the PR head, so deleting the branch after merge can't delete `main`. ([#22](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/22))
- Login working end-to-end against the real API: Retrofit networking, DataStore-backed session, session-aware start destination, partial email/password autofill (keyboardType only — full ContentType support needs a Compose BOM upgrade, #21). ([#20](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/20))
- Implemented the Create trip screen. ([#15](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/15))
- Automated backport PR creation (`main` → `develop`) after a release/hotfix merge. ([#19](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/19))
- Build workflow now comments the APK download link on the PR after each build. ([#18](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/18))
- Implemented the Active trip screen. ([#16](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/16))
- Implemented the History screen with expandable trip cards. ([#14](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/14))
- Implemented the Trips screen (planned trips ready to start), and added placeholders + routes for Create trip and Active trip. ([#13](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/13))
- Renamed package from `com.joaquindev.triptrace` to `com.techvibedev.triptrace`. ([#10](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/10))
- Added navigation skeleton (NavHost, bottom nav bar) and the Login screen. ([#10](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/10))
- Added concurrency cancellation to the build workflow, so rapid consecutive pushes to the same branch only complete the latest build. ([#11](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/11))
- Applied dark navigation theme palette and stat-forward typography. ([#9](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/9))

## [0.1.0] - 8 Sep 2026

### Added

- Initial Android app skeleton (Kotlin + Jetpack Compose).
- GitHub Actions workflow to build and upload a debug APK. ([#1](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/1))
