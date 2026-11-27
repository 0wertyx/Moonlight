# Librus bridge

Moonlight provides a link to the official Synergia portal in the user's browser and a read-only local snapshot importer. The portal handles authentication. Moonlight does not capture credentials, cookies, tokens or school account data from other apps.

Automatic synchronization is **not implemented**. An authorized API integration requires the provider's documentation and application authorization, including the supported authentication flow and read-only scopes. No endpoints or OAuth credentials are assumed.

## Local import

This is Moonlight's own JSON format, not an official Librus export. Prepare a file containing only data you are authorized to use. It is displayed as an unverified local copy, never as live or official data. The snapshot is cached in app-private storage, and can be deleted by disconnecting the selected document.

```json
{
  "schemaVersion": 1,
  "lessons": [
    {"date":"2026-11-30","subject":"Matematyka","period":1,"status":"scheduled"}
  ],
  "grades": [
    {"subject":"Matematyka","value":"5","weight":2}
  ]
}
```

Lesson statuses: scheduled, cancelled, substitution. Dates use ISO format. Grade weight may be null. File limit: 512 KiB; up to 2000 entries per array. The panel displays up to 100 entries per category.

## Automatic local refresh

Select a JSON document using Android's file picker. The app requests persistent read-only access, reads it immediately, and schedules a refresh approximately every 30 minutes through WorkManager. Refresh timing depends on Android scheduling. The last good snapshot is retained when a refresh fails. The switch disables periodic refresh, and disconnect clears the cached snapshot and releases access. The file must be updated by an independent authorized source; this mechanism does not fetch records from Librus servers.

Official portal: https://synergia.librus.pl/
