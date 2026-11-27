# Moonlight

An independent Android school planner and clearly labeled simulation of a school timetable and grades. Not affiliated with Librus.

## Planned features

- Kotlin and Jetpack Compose interface, dark moonlight theme.
- Local timetable and personal grade calculator with weighted grades.
- Explicitly labeled demo mode: simulate canceled lessons and sample grades without modifying school records.
- Local storage and configurable notifications.
- Real school data only through an authorized, supported integration; no account credentials in the repository.
- GitHub Actions debug APK builds.

No root, device security changes, or modifications to the official Librus app are required.

## Status

Initial project setup. A live Librus integration is not implemented.

## Librus connection

Browser portal and read-only local JSON import are available in the Librus tab. Automatic local snapshot refresh is available with WorkManager; automatic Librus API synchronization remains unimplemented. See [integration details](docs/librus-bridge.md).

## UI 0.2

Polish dashboard and drawer, schedule filters, imported grades, private lists, light/dark themes. See [UI details](docs/ui-v0.2.md).

## APK updates

See [stable signing setup](docs/signing.md). Build artifacts now distinguish temporary and persistent signing and have unique filenames.

## Experimental portal 0.3

See [in-app portal and user-requested local imports](docs/portal.md). No background server sync.

## Experimental portal refresh 0.4

See [local-session automatic refresh](docs/portal-refresh.md). This is not an official API connection.

## Local cache 0.4.1

See [local sync state](docs/local-sync.md).

## Persistent configuration

See [upgrade and storage contract](docs/persistence.md). Keep the same signing key and package ID.
