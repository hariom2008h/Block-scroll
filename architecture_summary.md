# Shorts Blocker - Project Setup & Architecture Summary

## 1. System Instructions / System Prompt
**Role:** Google AI Studio's AI Coding agent (powered by Gemini models).
**Objective:** Build, modify, and maintain an Android application (Shorts Blocker) based on user intent using Kotlin and Jetpack Compose.
**Key Constraints & Guidelines:**
- **Language & Framework:** Kotlin, Jetpack Compose for UI, Gradle (Kotlin DSL).
- **Architecture:** Clean Architecture / MVVM (Model-View-ViewModel). Single-Activity architecture with Navigation Compose for routing.
- **State Management:** `ViewModel`, `MutableStateFlow`, and SharedPreferences for local persistence.
- **Background Tasks:** Kotlin Coroutines and Flow for asynchronous operations (e.g., sending feedback, checking updates).
- **Permissions:** Implement Compose dynamic runtime request flows for required system access (Accessibility, Overlay, Notifications, Battery Optimizations).
- **UI/UX:** Material Design 3 (M3) adherence, dynamic colors, edge-to-edge support, and responsive scaling.

## 2. Model & Configuration
- **Model Version:** `models/gemini-pro-latest` (and previously `models/gemini-3.1-pro-preview`).
- **Temperature & Top-P:** Standard AI Studio coding configuration (typically Temp 0.4 - 0.7 for coding tasks).
- **Runtime Environment:** Cloud-based Android build environment with Streaming Android Emulator.

## 3. Core Architecture & Logic Breakdown

### Application Structure
The application operates entirely within a Single-Activity (`MainActivity`) containing multiple Composable screens managed by a custom state-based navigation system (`currentScreen`).

### Key Modules & Components
1. **Accessibility Service (`ShortsBlockerService`)**
   - **Purpose:** The core engine of the app. It uses Android's `AccessibilityService` to monitor user interactions and window state changes.
   - **Logic:** It intercepts scroll events or window transitions to detect and block short-form video content (e.g., YouTube Shorts, Instagram Reels) based on predefined rules or UI nodes.

2. **UI & Navigation (`MainActivity`)**
   - **Onboarding Screen:** Guides new users through initial setup.
   - **Home Screen:** The main dashboard displaying the status of the blocker.
   - **Settings Screen:** General app configurations.
   - **System Settings / Permissions Screen:** Manages critical Android permissions.

3. **Permission Management (`SystemSettingsScreen`)**
   - Evaluates and requests complex system permissions required for the blocker to function:
     - **Overlay Permission (`canDrawOverlays`):** To draw block screens over other apps.
     - **Accessibility Service:** To read screen content and intercept scrolling.
     - **Battery Optimizations (`IGNORE_BATTERY_OPTIMIZATIONS`):** To prevent the OS from killing the background service.
     - **Notification Permission:** For background service foreground notifications.

4. **Feedback & Telemetry (`sendFeedbackToTelegram`)**
   - **Logic:** Collects user feedback (text and images) along with critical device diagnostics:
     - Device Manufacturer & Model
     - Android Version
     - App Version
     - Status of Permissions (Overlay, Accessibility, Notifications, Battery Optimization)
   - **Network:** Uses `OkHttpClient` and Coroutines (`Dispatchers.IO`) to securely transmit this payload to a pre-configured Telegram Bot using the Telegram Bot API.

## 4. Input/Output Examples (Few-Shot for Logic)
**Example: Appending Device Info to Feedback**
*Input Logic:* User submits feedback via UI.
*Processing:*
```kotlin
val isOverlayEnabled = Settings.canDrawOverlays(context)
val isNotificationEnabled = // ... Permission check
val isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
val appVersion = UpdateChecker.getCurrentVersion(context)

val deviceInfo = """
    📱 Device Info:
    Device: ${Build.MANUFACTURER} ${Build.MODEL}
    Android Version: ${Build.VERSION.RELEASE}
    App Version: $appVersion
    
    ⚙️ Settings Status:
    Overlay Permission: ON/OFF
    Accessibility Service: ON/OFF
    Notifications: ON/OFF
    Unrestricted Battery: ON/OFF
"""
val currentText = feedbackText + "\n" + deviceInfo
```
*Output:* Telegram bot receives user message + diagnostic payload for debugging.
