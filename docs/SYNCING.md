# MoneyMate NZ cloud sync

MoneyMate uses its existing authenticated, owner-only Supabase snapshot service with compare-and-swap revisions. No service-role keys are embedded in the website or Android client.

To start, sign into the same Google/email account on both devices, export separate full JSON backups, then enable cloud sync in Account on the first device and then on the second. Initial upload requires consent. Guest records are never uploaded automatically.

After opt-in, transactions, budgets, goals, bills and recurring entries upload on changes when connected. Android polls in the foreground; the website checks on changes, browser visibility and a short interval.

If both devices edit separately while offline, sync stops at a conflict instead of losing unpublished changes. Export both backups before reconciling. The website stores a local recovery copy before replacing a profile.

Server snapshot size is capped at 512 KiB. No bank connection or real bill payment is made. Tests must include two physical devices, account isolation, offline edits, token renewal and recoverability before claiming complete real-world verification.
