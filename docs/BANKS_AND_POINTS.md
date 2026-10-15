# MoneyMate NZ: free statement imports and retired points

## MoneyMate Points (retired)
Both client versions no longer offer point claims or points pages. Historic point data remains. Apply the separate `20261013_retire_moneymate_points.sql` migration only after updated clients have rolled out to disable old reward RPCs without deleting prior records.

## PDF bank statements (new, reviewed feature candidate)

**ANZ NZ template candidate:** For ANZ 'Statement of Accounts' PDFs with Date / Transaction type and details / Withdrawals / Deposits / Balance, we now calculate the direction from the change between verified consecutive running balances; '10 Sep' dates are resolved using the explicit statement period. If the balance is missing, the PDF spans multiple accounts, the statement period cannot be validated, or a single amount fails to reconcile exactly, the entire import is refused. Synthetic fixtures for ANZ and other generic signed/debit/credit layouts are unit-tested. **This is NOT verification of any customer's real ANZ PDF or every NZ bank layout**. Other banks may work with compatible, unambiguous formats; the RBNZ registry lists banks, not guaranteed PDF compatibility.

- People can download a **NZD PDF statement** from their own bank and select it under **Import statements**, **Transactions** or **Export** (Android) without paid bank APIs. This is MANUAL import, not automatic bank linking.
- The website fetches the pinned PDF.js browser reader code from cdnjs only when requested and processes the PDF bytes in the user's browser. It does not upload the statement document to a PDF service. Text-based PDFs only on the website; image-only scanned PDFs are not supported there yet.
- Android uses Android PdfRenderer and Google Play services on-device Latin OCR (model may download on first use) for text or scanned PDF pages. No off-device OCR service or bank credentials. On-device OCR can misread numbers.
- The fail-closed parser accepts only a limited set of clearly headed NZD statement tables, with signed (+/-) amounts or explicit DR/CR entries; it rejects unclear debit/credit/balance columns instead of guessing. Confirm the original PDF is NZD, not AUD.
- Users must review **every** newly recognised PDF transaction and explicitly confirm before the existing append-only, duplicate-aware import commits. Save a full JSON backup first. Complex PDFs, unreadable pages and unsupported date/amount layouts may be rejected without importing anything.
- Website limits PDFs to 4 MB / 12 pages / 200 new rows; Android limits to 4 MB / 8 pages / 200 parsed rows. These are intentionally conservative. The PDF library requires internet to download on first use on the website. No per-bank provider subscriptions apply.
- This candidate cannot be considered validated for a specific bank until a real **redacted** representative PDF has passed a manual comparison with the full preview and test-device runs; do not request users to upload their unredacted bank records to GitHub.

## Fee-free bank statement CSV import (new, review branch)
- On the website, go to **Import statements → Choose statement CSV file**; on Android choose **Statements → Import NZD statement CSV** or **Export → Import bank CSV**.
- Supports common CSV forms with Date, Amount and Description or Date, Debit, Credit and Details, plus MoneyMate NZ's own exported CSV. Text can include commas in quotes. Date format YYYY-MM-DD or DD/MM/YYYY; choose a short consistent account nickname that contains no bank account number.
- Files stay on the device unless the user has enabled optional Supabase cloud sync. User gets a preview and must explicitly confirm. The importer appends rows, never replaces records, and suggests Groceries, Housing, Transport, Utilities, Health and Entertainment from descriptions (otherwise Other).
- Re-importing a statement skips rows matching previously imported records under the **same nickname**. Deduplication uses date, amount, type, category, source nickname and description, including counts for identical rows. If two true transactions are indistinguishable they may not be reliably recognised; users should review the preview.
- **Only NZD can enter existing NZD budgets. AUD statements are intentionally refused**, not converted. Australian multi-currency budgets and statement imports require a separate data model before launch.
- No paid provider, bank password, direct bank API, or bank-connected automatic refresh is involved. New transactions will appear on other devices only if existing optional account cloud sync is enabled and conflict-free.

## No paid bank connections
MoneyMate NZ does not offer direct bank account connections or provider subscriptions. There is no plan to charge the owner or users for Akahu/Basiq integration. NZD bank statements can be imported manually from supported CSV or PDF files without an open-banking provider. Account cloud syncing is a separate service and still uses existing optional Supabase storage until a safe replacement is implemented. Do not remove or overwrite existing financial records during any auth migration.

## Publish/test
The new source is under `docs/web-app.js` and `app/src/main/java/nz/moneymate/app/`. The historical points SQL remains for safe retention. Check CI and verify no points tabs appear in either client. Do not delete old Supabase data.
