# Live app design updates (from GitHub Pages)

MoneyMate NZ Android 0.2.1+ can refresh its **colours and tagline** from the public theme file:

- Published JSON: https://frymastercheese.github.io/MoneyMate-NZ/app-theme.json
- Edit the file: https://github.com/FRYMASTERCHEESE/MoneyMate-NZ/edit/main/docs/app-theme.json

## How to change the design without an APK

1. Edit `docs/app-theme.json` on GitHub **main** (the branch deploying GitHub Pages).
2. Save/commit. GitHub Pages automatically republishes the website.
3. Open or return to MoneyMate NZ on your Android phone. It checks for a new theme on launch/return (at most once a minute).
4. To check immediately, open **Export → Refresh app colours & text**.

Supported theme properties: `accent`, `background`, `text`, `hero` (strict six-digit hex), and `tagline` (up to 90 characters). The schema `version` must be 1. Themes with unreadable text contrasts or invalid values are rejected. The last good theme remains available offline.

**Limitations:** this changes colours and a short text, not Android screens, database schema, app icons, permissions, advertising SDKs or features. Those still require a new signed APK, or Play Store auto-updates after publication. It does not fetch or execute remote code. The theme request exposes a normal internet connection (e.g. device IP) to GitHub Pages, but it sends **no private financial records**.

**Signing:** 0.2.1 uses the same persistent test-signing configuration as 0.2.0 on GitHub Actions. Android should allow an update from the same certificate if the package and version are compatible. The earlier 0.1.0 build used a different certificate; make a full JSON backup, uninstall, then reinstall/restore if needed. Always save a backup before testing an update.
