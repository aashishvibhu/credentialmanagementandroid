# Roadmap

Agreed 2026-10-20. Each phase ends with a passing build and unit tests, and is reviewed before the next one starts.

## Decisions

- **Look:** minimal "Option A" design: neutral colours, letter avatars, pill search, one row per login.
- **Copy button:** the copy icon on a row copies the **password**. Long-press offers username, password, and the next backup code. The clipboard clears itself after 30 seconds.
- **Grouping:** logins with the same service name (case and spaces ignored) collapse into one expandable row, e.g. "Google · 3 accounts". A service with one login stays a normal row. Phase 5 makes this catalog-aware, so "Gmail" joins "Google".

## Phase 0: Roadmap and build fixes

- [ ] Add this roadmap and refresh `ISSUES.md` status
- [ ] Fix compile errors on `master` (missing imports in the list screen and view model, misplaced `snackbarHost` in the detail screen, missing `every` import in the detail view model test)

## Phase 1: Design system

- [ ] Custom light and dark colour schemes (no dynamic colour), two-weight type scale, rounded shapes
- [ ] Stable avatar colour per service name
- [ ] Shared components: service avatar, app mark, section card, setting row, soft text field

## Phase 2: Vault list with account grouping

- [ ] Header: sync status, settings, "Vault" title, login count, pill search
- [ ] Single rows with a copy-password button; long-press menu
- [ ] Group rows that expand to show each account with its own copy button, plus "Add another *Service* account" (pre-fills the service)
- [ ] Swipe to delete with undo; "Password copied" confirmation; empty and no-match states
- [ ] Search opens all matching groups

## Phase 3: Edit, sign-in, lock and settings screens

- [ ] Edit login: live avatar preview, "Grouped with N other accounts", soft fields, existing-service suggestion chips, segmented strength meter, delete at the bottom
- [ ] Sign-in and unlock screens with the app mark and one clear button
- [ ] Settings in sections: Account, Sync, About, Sign out

## Phase 4: Backup codes

- [ ] Optional backup codes on each login, stored encrypted and synced with the login (no database migration needed)
- [ ] Paste codes straight from the website; the app splits them (new lines, commas, numbering) and keeps codes like `1234 5678` whole
- [ ] Review before saving: "10 codes found", remove any wrong ones, choose to replace the old codes
- [ ] Tap a code to copy it and mark it used; used codes are struck through; tap again to undo
- [ ] "7 of 10 left" progress; warning in the list when 2 or fewer are left
- [ ] Note for release: update the app on every device, since an older version that saves will drop the codes

## Phase 5: Preloaded service catalog

- [ ] Bundled catalog of ~100–200 popular services: name, domains, aliases, category, brand colour, optional icon, backup-code support, security page link
- [ ] Name matching: "Gmail", "YouTube" and "google.com" all map to Google
- [ ] Autocomplete in the Service field; picking a service fills in the website
- [ ] Brand icons for known services, letter avatar otherwise; everything bundled, no network calls
- [ ] Grouping uses the catalog name when known
- [ ] "Tidy up" suggestion to rename existing logins so they group correctly
- [ ] Per-service hints ("This site offers backup codes", link to security settings)
- [ ] Category filter chips (Email, Banking, Social, Shopping…)
- [ ] Catalog updates ship with app releases

## Verification

- `gradlew :app:assembleDebug` and `gradlew :app:testDebugUnitTest`
- New unit tests for grouping, the backup-code parser and the catalog matcher
- Manual check on an emulator in light and dark mode: grouping, copy and clipboard clear, swipe-delete and undo, backup codes surviving sync and sign-out/sign-in

### Building on Windows

Gradle needs these environment variables, otherwise it fails with "Unable to establish loopback connection" or "SDK location not found":

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\gtmp"   # any short, existing folder
```
