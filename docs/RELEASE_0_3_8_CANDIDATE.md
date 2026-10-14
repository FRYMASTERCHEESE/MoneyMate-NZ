# MoneyMate NZ 0.3.8 (12) — staging gate (NOT published)
- Candidate builds on audited draft PRs #23 and #24, with overlap changes integrated and tested.
- Production package remains `nz.moneymate.app`; no signing identity, secret or public release change.
- Signed production AAB requires the protected workflow on approved main, owner approval, exact version-specific R8 mapping, and app integrity/AdMob/privacy checks. This draft only builds *unsigned* release validation artifacts.
- Android 15/16 debug+R8 emulators, strict lint and browser tests must pass on the **exact** version-bump commit; real physical-device Google/email auth, backups, upgrades, multi-device conflict handling, privacy consent and representative bank imports remain distinct launch gates.
- On merge, Pages redeploys; verify the website against existing deployed copies before approving.
- Supabase retired points RPC revoke-only migration is **not** deployed by this PR; requires separate review/approval.
- No new Play Console release, Alpha track, tester group, account record or cloud setting was changed.
- Closed-testing participants continue with the same Play app ID and tester enrolment. A version upload does not itself reset 14-day opt-in, but do not recreate or pause the Alpha track.
- Play **Internal Early Access** is a testing presentation, not the app's Android launcher label. Closed testing is not searchable to the public. Users must join the approved Google Group at https://groups.google.com/g/moneymate-nz-testers/about, then opt in at https://play.google.com/apps/testing/nz.moneymate.app. Publicly discoverable **Early Access** via open testing requires Google granting production access.
