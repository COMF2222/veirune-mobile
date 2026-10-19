# Community music (0.1.9)

Home adds popular music for today, yesterday and week, plus Editors’ picks. The
account preference in Settings can hide both sections across devices. Popularity
uses unique approved accounts, not repeat plays or device count. A song needs at
least two listeners; each must contribute at least 30 seconds and a counted play
in the period. Day boundaries are UTC, matching existing statistics.

Only the server owner sees User management and can appoint editors. Editors can
recommend music from the mini/full player or track menu, without receiving user
management rights. Up to ten picks per editor and thirty distinct shared tracks
are allowed. A shared track appears once even if several editors recommend it.
Editors remove their own picks; the owner can remove an entire shared track.

Deploy the backend before releasing this client. No mobile server setting or
signing-key change is required. Personal preference protocol 3 is used by the
web and native gateways; old clients keep their previous compatible fields.

Local verification includes TypeScript/tests, signed release APK, JVM checks and
Android lint. The browser preview at 390×844 was checked with listener, editor
and owner roles, add/remove actions in both players, section visibility, period
switching and English/light appearance. No phone was connected for this change;
actual background playback, network conditions and installation still need
device verification. The iOS gateway header was kept compatible; no iOS release
was built.
