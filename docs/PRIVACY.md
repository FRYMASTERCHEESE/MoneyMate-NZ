# MoneyMate NZ — Privacy Notice (draft for Google Play)
Last updated: 12 October 2026. Operator: FRYMASTERCHEESE, developer of MoneyMate NZ.

Transactions, budgets, goals, bills and repeating transactions stay in Android app-private SQLite storage. There is no bank connection or cloud financial sync. Accounts are optional. The original local profile is preserved without an account. Each signed-in Supabase user ID gets a separate database on this device. Signing out hides account records but retains them for the next sign-in. Anyone with access to your unlocked device and app can view the original local profile; accounts do not replace a device lock.

Existing records are not automatically assigned to an account. You may explicitly copy them into an empty account profile. The original remains intact. Only import records that belong to you. Export and restore act on the current profile. Exported backups contain financial records, not passwords or session tokens. Store exported files securely.

## Account processing

Supabase processes email, password, authentication identity, sessions, IP address and authentication/security events for sign-up, sign-in, email confirmation, password reset and deletion. Supabase stores password hashes; the app never persists passwords. Tokens are encrypted with Android Keystore and excluded from backup. Account requests use HTTPS. Financial entries are not uploaded. The deletion function verifies the account and password without logging passwords, tokens or request bodies.

The intended dedicated project region is Sydney, Australia; confirm the deployed region before publication. Supabase and the selected email provider may process authentication data outside New Zealand. See [Supabase privacy information](https://supabase.com/privacy). The SMTP provider's name, privacy policy and retention must be added once selected.

The app also retrieves public colours/tagline from GitHub Pages. This does not include account identity or financial entries, but hosting receives network information such as IP addresses.

## Retention and deletion

Local records remain until removed, replaced by restore, or app storage is cleared/uninstalled. **Account → Delete account permanently** requires your current password and explicit confirmation. After server success, it hard-deletes your Supabase account, revokes refresh sessions and removes that account's database on this device. The original local profile remains intact. Access JWTs may live until expiry; privileged deletion always verifies a live user and reauthenticates.

You can delete your account without reinstalling via [the web deletion page](https://frymastercheese.github.io/MoneyMate-NZ/delete-account.html). Web deletion cannot erase device-local files. Clear app storage on other devices and delete exported CSV/JSON files separately. An offline device can still display a retained account profile until it reconnects or you clear storage. If the app is killed after backend deletion but before local cleanup, clear app storage. Clearing storage removes every local profile.

Supabase and the email provider may retain security logs and disaster-recovery backups under their configured retention and applicable requirements. Confirm these periods before release. This policy does not promise immediate erasure of third-party logs/backups. Google advertising data is governed by Google's policies.

## Advertising
The app uses Google AdMob to show advertising. Google and its partners may collect advertising identifiers, device information and other data as described in [Google's Privacy Policy](https://policies.google.com/privacy). The app requests consent where required using Google's User Messaging Platform. Until the developer configures a production AdMob account, the app only uses Google's sample test ad IDs.

## Contact and updates
Do not post passwords, email codes or financial details in public GitHub issues. Use the issue tracker only for technical reports with sensitive information removed. **Before publication**, supply a monitored private support/privacy email, final SMTP provider details and retention periods in this policy and `privacy.html`. Link the hosted policy and web deletion page in Google Play, and complete Data safety disclosures including Auth and advertising processing. MoneyMate NZ is a general-purpose budgeting tool, not directed at children. No claim of Play approval is made.
