# MoneyMate NZ: Bank integration roadmap and in-app points

## MoneyMate Points (implemented)
- Sign in with Google or email and open **Rewards / Points** on the website or Android app.
- Tap **Claim 10 points** once per UTC calendar day. The server derives the date, checks the authenticated account ID, inserts a unique daily claim and adds 10 points atomically. Repeats return 0.
- The same account ID yields the same points balance on Android and web. Anonymous users cannot call the rewards RPC. Clients cannot insert or edit balances directly.
- Points are free participation markers. They are not cash, gambling, gift cards, transferable credit or redeemable rewards. There is no money reward or future cash promise. No bank account connection, payment, transaction spending, account balance, or income level is required to claim.

## Fee-free bank statement CSV import (new, review branch)
- On the website, go to **Bank connections → Choose bank CSV file**; on Android choose **Banking → Import NZD bank statement CSV** or **Export → Import bank CSV**.
- Supports common CSV forms with Date, Amount and Description or Date, Debit, Credit and Details, plus MoneyMate NZ's own exported CSV. Text can include commas in quotes. Date format YYYY-MM-DD or DD/MM/YYYY; choose a short consistent account nickname that contains no bank account number.
- Files stay on the device unless the user has enabled optional Supabase cloud sync. User gets a preview and must explicitly confirm. The importer appends rows, never replaces records, and suggests Groceries, Housing, Transport, Utilities, Health and Entertainment from descriptions (otherwise Other).
- Re-importing a statement skips rows matching previously imported records under the **same nickname**. Deduplication uses date, amount, type, category, source nickname and description, including counts for identical rows. If two true transactions are indistinguishable they may not be reliably recognised; users should review the preview.
- **Only NZD can enter existing NZD budgets. AUD statements are intentionally refused**, not converted. Australian multi-currency budgets and statement imports require a separate data model before launch.
- No paid provider, bank password, direct bank API, or bank-connected automatic refresh is involved. New transactions will appear on other devices only if existing optional account cloud sync is enabled and conflict-free.

## NZ and Australia bank connections (preparation only)
MoneyMate NZ does not have live bank data access yet. **Bank connections are not available** until the owner signs onboarding agreements and is approved by an accredited provider. The interface accurately displays this limitation.

- **NZ:** Akahu intermediary official open banking — https://developers.akahu.nz/docs/official-open-banking
- **Australia:** Basiq Consumer Data Right accredited recipient/partner arrangement — https://docs.basiq.io/en/articles/5088017-consumer-data-right-cdr-policy

Bank integration release gates:
1. Confirm provider approval, contracts, fees, accreditation or partner status, precise bank coverage and allowed purposes.
2. Use only bank/provider-controlled, explicit **read-only consent**. No bank usernames/passwords within MoneyMate NZ.
3. Implement provider OAuth via a secure backend (for example, Supabase Edge Functions), with state/nonce/redirect verification, secrets encrypted or stored securely server-side. Never expose API tokens, client secrets or access tokens in GitHub Pages or the APK.
4. Add transaction sync with deduplication of provider IDs, owner isolation, revocation, reconnection, outages, retention/deletion and statement history.
5. Keep AUD and NZD balances separate or introduce a documented conversion method; never silently treat AUD as NZD.
6. Complete real-provider end-to-end consent, multi-user isolation, disconnect and deletion tests before enabling the Connect button publicly.

Existing optional account financial syncing is separate from open-banking consent. Bank connection must never be enabled automatically just because a user signs into MoneyMate NZ.

## Publish/test
The new source is under `docs/web-app.js` and `app/src/main/java/nz/moneymate/app/`. The matching SQL is `supabase/migrations/20261013_moneymate_daily_points.sql`, applied to the dedicated MoneyMate NZ project. Check GitHub Actions CI, then test a real daily points claim on a signed-in user and refresh the balance on another device.
