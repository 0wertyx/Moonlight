# Calendar and compact UI 0.5.1

The daily timetable no longer has the large local-copy banner or repeated explanatory empty-day warning. Moonlight remains identified as an independent planner; source details stay in settings and last-refresh time stays visible. Empty days say there are no lessons in the saved plan, not that a school closure has been confirmed.

Terminarz is read through the same authorized WebView session. The optional calendar parser uses the public timetable/calendar DOM conventions (table.kalendarz.decorated.center, .kalendarz-dzien, .kalendarz-numer-dnia). It requires explicit month/year form values and distinct dates for the entire month before accepting a result. Events are plain text, kept separate from lessons; no automatic interpretation as cancelled lessons. Only visible event cells with a title attribute are imported, with no detail-page fetching. Current month only. An unsupported calendar layout does not stop grades/timetable; existing calendar data remains available. Authorization redirects and rate limits stop requests. Snapshot schema remains backwards compatible and all data stays on device.

Real calendar HTML and event coverage require on-device verification. No real-account testing in CI.
