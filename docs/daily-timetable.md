# Daily timetable (0.5.0)

Select a day in the week strip to view only that day's lessons. Week navigation and date picker remain available. The day strip counts active lesson entries and displays a dash where there are no cached entries; this is not proof that school is closed. The selected-day summary separately counts cancelled entries.

The portal parser reads the clock pair from each timetable row's TH header (the same header used by the public Mati365/librus-api timetable parser). The optional startTime/endTime fields are validated and stored in the existing v1 snapshot. Older snapshots without those fields remain readable. Unknown or invalid times are shown as unavailable, never estimated. No changes to preferences, package ID or signing configuration.

Automatic refresh still covers only the current week. Navigating other weeks shows cached records only. Real portal header layout and installation over the old build require testing on device.
