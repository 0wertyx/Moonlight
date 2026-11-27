# Experimental portal refresh (0.4.0)

Open the official portal in Moonlight, sign in normally, close the portal, then enable automatic portal refresh in the data-source screen. The Android WebView manages its own cookies on the device; no credential or cookie export, external backend or automatic login is implemented. Use only your own account where permitted. The user may turn refresh off at any time.

WorkManager requests two normal portal pages at approximately 30-minute intervals with network connectivity. No retries or access-control bypass. Manual refresh has a 60-second cooldown; the two jobs are serialized. Unexpected redirects, unauthorized responses, rate limiting and unrecognized page layouts stop refresh and require user intervention. Network errors preserve the previous copy. Android may delay background work.

Only grades and current-week schedule are supported. Both pages must parse successfully before replacing the previous local snapshot. Grade weights, empty tables, ambiguous statuses, other weeks, messages and attendance are not supported. The app labels imported data as unverified local copies. Enabling a new portal source clears the previous copy to avoid mixing accounts. To switch accounts, disable refresh, log out in the official portal, and re-enable after logging in. Disconnecting clears the local copy, but does not log out of the website.

Real account login, background WebView rendering, parser compatibility and runtime behavior require on-device testing. No official Librus API integration is claimed.
