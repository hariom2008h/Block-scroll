# Shorts Blocker 🛡️
### Reclaim Your Time, Break the Loop.

**Shorts Blocker** is a powerful Android utility designed to combat mindless scrolling and digital addiction. By introducing intentional "friction-by-design," it helps users break impulsive habits by intercepting addictive feeds like YouTube Shorts, Instagram Reels, and Snapchat Spotlight.

---

## ✨ Features

- **Multi-Platform Interception**: Automatically detects and blocks addictive short-form video sections across YouTube, Instagram, and Snapchat.
- **Friction-by-Design**: Requires a master password to unlock blocked content, forcing a moment of conscious decision-making.
- **Session Cooldowns**: Customizable grace periods and cooldowns to prevent immediate re-entry into addictive apps.
- **Stealth Mode**: Ability to hide the application icon from the launcher for added privacy and reduced temptation.
- **Modern Material 3 UI**: A clean, responsive interface featuring dynamic color support that adapts to your wallpaper.
- **Guided Onboarding**: A smooth tutorial flow that explains why Accessibility and Overlay permissions are required for the app to function.
- **Privacy First**: Operates entirely offline. No data collection, no tracking, and no external servers.

---

## 🛠️ Tech Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Design System** | Material Design 3 (M3) |
| **Architecture** | MVVM (Model-View-ViewModel) |
| **Android Services** | AccessibilityService, WindowManager (Overlays) |
| **Build System** | Gradle (Kotlin DSL) |
| **Persistence** | SharedPreferences / DataStore |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Koala or newer.
- Android device or emulator running API 24 (Nougat) or higher.

### Installation Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-username/shorts-blocker.git
   ```
2. **Open the Project**:
   Launch Android Studio and select `Open an Existing Project`, then navigate to the cloned directory.
3. **Build the Project**:
   Allow Gradle to sync and download dependencies.
4. **Run the App**:
   Select your device and click the **Run** button (Shift + F10).

---

## 🔑 Permissions Required

To function effectively, Shorts Blocker requires:
- **Accessibility Service**: To detect when you navigate into "Shorts" or "Reels" sections.
- **Overlay Permission**: To display the blocking screen over protected applications.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
