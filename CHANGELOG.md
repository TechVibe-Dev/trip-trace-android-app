# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Added Room local database and a foreground service that records GPS points during an active trip (`TripTrackingService`), plus a "Finalizar viaje" action. Fixed a crash on "Iniciar" caused by `GpsPointEntity`'s foreign key having no matching `TripEntity` row — the trip is now fetched and saved to Room before tracking starts. ([#26](https://github.com/TechVibe-Dev/trip-trace-android-app/pull/26))
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
