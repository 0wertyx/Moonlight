# Local sync state 0.4.1

The existing app-private SharedPreferences snapshot is retained, with separate grade and lesson last-success timestamps. Both the auto-refresh toggle and the snapshot survive normal process restarts. Portal cookies remain in WebView's own private store. No cloud or GitHub data uploads, new endpoints, cookie extraction, or additional permissions. Android backup remains disabled.

A successful grade read is saved before attempting the timetable. A timetable failure no longer discards new grades. A transient grade read failure allows the timetable read; authorization failures, unsupported page structure and rate limiting still stop automatic refresh. The last successful section remains cached and its timestamp is visible. The current week replaces the previous timetable only after validation. Existing snapshots use the prior overall timestamp as a migration fallback. User-triggered source reconnection deliberately clears the old snapshot to avoid mixing accounts. Clearing local data or uninstalling removes the cache; switching accounts requires disabling sync, logging out in the portal and enabling sync again.

Runtime tests on a real account are not performed by CI. Grade weights and other portal modules are not added by this change.
