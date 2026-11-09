# Stealth Mode: Reliable App Icon Hiding Across All Android Versions

This plan resolves the issue where the **Hide App Icon (Stealth Mode)** feature works on older Android versions (Android 8/9) but fails to hide or stays accessible on newer Android versions (Android 10–15). It ensures the app disappears from the Home Screen, App Drawer, and Recent Apps, and can only be opened through **Phone Settings → Accessibility → Shorts Blocker**.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Why Android 10+ Stops Normal Icon Hiding:**
> Starting in Android 10 (API 29), Android's `LauncherAppsService` runs a security check (`shouldShowSyntheticActivity`). If an installed app disables all of its `CATEGORY_LAUNCHER` activities (leaving 0 enabled launcher icons), Android 10+ automatically generates a **synthetic fallback icon** using the app's original name and logo so the icon refuses to disappear.

- **Confirmed Decision 1 (Inaccessible from Home Screen, Accessible via Accessibility Settings)**: When Stealth Mode is enabled, the app must not be visible or openable from the Home Screen, and the user will access the app exclusively via the **Accessibility Service Settings** button (`android:settingsActivity="com.example.MainActivity"`).
- **Confirmed Decision 2 (Transparent / Blank Minimal Disguise for Android 10+)**: To bypass Android 10+'s synthetic icon injection while keeping the home screen visually clean and inaccessible, we will use an `<activity-alias>` architecture with a **100% transparent/blank icon and invisible label** backed by a no-op `GhostLauncherActivity` that immediately closes (`finish()`) if tapped, combined with full component disabling on Android 9 and below.
- **Confirmed Decision 3 (Hide from Recent Apps)**: When Stealth Mode is active, `MainActivity` will also exclude itself from the Recent Apps (multitasking) screen (`setExcludeFromRecents(true)`) so no one can reopen it from multitasking after the user adjusts settings.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the single-activity disable call (`SplashActivity`) with a multi-version **Stealth Launcher Architecture** that reliably hides the Shorts Blocker icon and prevents opening the app from the launcher across Android 8 through Android 15+.
- **Target Audience / Persona**: Users who want strict self-control against doomscrolling without being able to impulsively open the blocker app from their home screen to weaken their settings.
- **Key Value**:
  1. Eliminates the visible **Shorts Blocker** icon on Android 10–15 devices where the OS otherwise forces a synthetic icon.
  2. Makes the launcher entry completely inert (tapping the blank/transparent slot does nothing), forcing the user to go through **Phone Settings → Accessibility → Shorts Blocker → Settings** to make any changes.
  3. Automatically hides the app from **Recent Apps** when Stealth Mode is active.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Enabling Stealth Mode**:
     - User opens **Settings → Stealth Mode → Hide App Icon** and toggles the switch **ON**.
     - A confirmation dialog/banner briefly explains how to reopen the app: *"Go to Phone Settings → Accessibility → Shorts Blocker → Settings (Gear Icon) to access the app while hidden."*
     - The app switches the launcher component state, activates `setExcludeFromRecents(true)`, and removes the visible Shorts Blocker icon from the launcher.
  2. **Accessing the App While Hidden**:
     - The user opens **Phone Settings → Accessibility → Downloaded Apps / Installed Services → Shorts Blocker** and taps the **Settings (Gear)** button (`MainActivity`).
     - When the user leaves `MainActivity`, it automatically vanishes from the Recent Apps list.
  3. **Restoring the App Icon**:
     - Inside `MainActivity` → **Settings → Stealth Mode**, the user toggles **Hide App Icon** back **OFF**.
     - The primary `.DefaultLauncherAlias` is re-enabled and the Shorts Blocker icon returns to the Home Screen / App Drawer.
- **Visual Identity & Theme**:
  - Uses the app's **Material You (Dynamic Color)** tonal surfaces (`surfaceContainer`, `secondaryContainer`, `24dp` rounded corners).
  - Includes a helpful **"Test / View Accessibility Path"** helper button inside the Stealth Mode card so users know exactly where to find the app in Accessibility Settings before hiding it.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: `<activity-alias>` + Transparent No-Op Ghost Alias on Android 10+**
  - *Chosen Approach*:
    - Define `.DefaultLauncherAlias` (enabled by default, visible Shorts Blocker icon + label, opens `SplashActivity` → `MainActivity`).
    - Define `.GhostLauncherAlias` (disabled by default, 100% transparent adaptive/vector icon `@mipmap/ic_launcher_ghost` + zero-width space label `\u200B`, targets `GhostTrapActivity`).
    - On Android 10+ (API 29+), enabling Stealth Mode disables `.DefaultLauncherAlias` and enables `.GhostLauncherAlias`. Because Android 10+ sees 1 enabled `CATEGORY_LAUNCHER` component, `LauncherAppsService` does **not** inject the synthetic visible Shorts Blocker icon. And because `.GhostLauncherAlias` points to `GhostTrapActivity` (which uses `@android:style/Theme.NoDisplay` and calls `finish()` immediately in `onCreate`), the app cannot be opened from the launcher even if someone taps the blank space.
    - On Android 9 and below (API < 29), enabling Stealth Mode disables both `.DefaultLauncherAlias` and `.GhostLauncherAlias` so zero icons exist.
  - *Why*: Directly disabling all launcher activities on Android 10+ triggers Google's `shouldShowSyntheticActivity()` anti-hiding enforcement, which puts the visible Shorts Blocker icon right back on the launcher. A transparent no-op alias satisfies the OS check while keeping the app invisible and un-openable from the launcher.
- **Decision 2: Dynamic Recent Apps Exclusion (`setExcludeFromRecents`)**
  - *Chosen Approach*: In `MainActivity.onResume()` / `onStop()`, check `sharedPrefs.getBoolean("hide_launcher_icon", false)` and apply `ActivityManager.AppTask.setExcludeFromRecents(isHidden)`.
  - *Why*: Even if the launcher icon is hidden, an app left in the Recent Apps switcher can still be opened with one tap. Excluding it from Recents when Stealth Mode is active completes the stealth protection.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Android Manifest Layer                           │
│                                                                         │
│  ┌──────────────────────────┐        ┌───────────────────────────────┐  │
│  │ .DefaultLauncherAlias    │        │ .GhostLauncherAlias           │  │
│  │ (Normal Icon + Label)    │        │ (100% Transparent Icon + "\u200B") │  │
│  │ enabled = true           │        │ enabled = false               │  │
│  └────────────┬─────────────┘        └───────────────┬───────────────┘  │
│               │                                      │                  │
│               ▼                                      ▼                  │
│  ┌──────────────────────────┐        ┌───────────────────────────────┐  │
│  │ SplashActivity           │        │ GhostTrapActivity             │  │
│  │ -> Launches MainActivity │        │ (Theme.NoDisplay -> finish()) │  │
│  └────────────┬─────────────┘        └───────────────────────────────┘  │
└───────────────┼─────────────────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Settings UI)                       │
│  • Accessible via Accessibility Service (android:settingsActivity)      │
│  • Toggles Stealth Mode via StealthModeManager.setStealthEnabled()      │
│  • Calls AppTask.setExcludeFromRecents(isHidden)                        │
└─────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

1. **`StealthModeManager` Helper**:
   - Centralizes `setStealthMode(context: Context, enabled: Boolean)` and `syncStealthModeState(context: Context)`.
   - Safely toggles `ComponentName(context.packageName, "com.example.DefaultLauncherAlias")` and `ComponentName(context.packageName, "com.example.GhostLauncherAlias")` using `PackageManager.DONT_KILL_APP`.
   - Syncs `ActivityManager.AppTask.setExcludeFromRecents(enabled)` so the app never lingers in Recent Apps while hidden.
2. **`GhostTrapActivity`**:
   - Configured with `@android:style/Theme.NoDisplay`, `android:excludeFromRecents="true"`, and `android:noHistory="true"`.
   - Immediately calls `finish()` in `onCreate()` so accidental taps on the blank slot never open the app.
3. **Stealth Mode UI Card (`MainActivity.kt`)**:
   - Displays current Stealth status and clear instructions on how to reopen the app via **Accessibility Settings → Shorts Blocker → Settings**.
