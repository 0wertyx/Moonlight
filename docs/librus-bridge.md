# Librus bridge

Moonlight provides a link to the official Synergia portal in the user's browser and a read-only local snapshot importer. The portal handles authentication. Moonlight does not capture credentials, cookies, tokens or school account data from other apps.

Automatic synchronization is **not implemented**. An authorized API integration requires the provider's documentation and application authorization, including the supported authentication flow and read-only scopes. No endpoints or OAuth credentials are assumed.

## Local import

This is Moonlight's own JSON format, not an official Librus export. Prepare a file containing only data you are authorized to use. It is displayed as an unverified local copy, never as live or official data. The snapshot is held only in memory.

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

Official portal: https://synergia.librus.pl/
