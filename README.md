<!-- HEADER -->
<h1 align="center">📞 CallingApp</h1>
<p align="center"><i>Discord-Style Community, Chat &amp; Voice/Video Platform</i></p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Java-007396?style=for-the-badge&logo=java&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Voice%2FVideo-Agora%20RTC-099DFD?style=for-the-badge&logo=webrtc&logoColor=white" alt="Agora" />
  <img src="https://img.shields.io/badge/Backend-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase" />
  <img src="https://img.shields.io/badge/Media-Cloudinary-3448C5?style=for-the-badge&logo=cloudinary&logoColor=white" alt="Cloudinary" />
</p>

---

## 📞 Overview

**SE114_CallingApp** is a Discord-style Android community app combining server/channel organization, rich real-time text chat, and voice/video calling. It's built with a modular Gradle structure (`core/model`, `core/network`, `core/di`, `app`), backed by **Firebase** (Firestore, Realtime Database, Auth, Cloud Messaging), **Agora RTC** for calls, and **Cloudinary** for media, with a small **Node.js/Express** backend that issues Agora tokens and proxies Cloudinary uploads.

---

## ✨ Key Features

### 🗂️ Servers & Channels
- Create servers, invite/add members, and manage server settings (`CreateServerDialog`, `AddServerMemberDialog`, `ServerFragment`, `ServerViewModel`).
- Organize servers into chat channels and post (community feed) channels (`PostChannelAdapter`, `ChatZoneAdapter`).
- Join servers via a bottom-sheet join flow (`bottom_sheet_join_server`) and QR code support (`ic_qr_code`).

### 💬 Chat
- Real-time messaging with local caching for offline access (Room DB: `AppDatabase`, `MessageDao`, `CachedMessage`).
- Rich chat features: replies, @mentions with autocomplete (`MentionAdapter`), reactions, pinned messages, media grid view, shared files/links tabs, in-chat search, and message reminders.
- Chat info panel with tabs for members, shared files, shared links, and pinned messages (`ChatInfoPagerAdapter`, `ChatMembersFragment`, `SharedFilesFragment`, `SharedLinksFragment`).
- Audio message playback with waveform UI (`AudioPlayerManager`, `lottie_audio_wave`).

### 📞 Voice & Video Calling
- Group voice/video calls powered by **Agora RTC SDK** (`VoiceCallRepository`, `CallActivity`, `VoiceCallFragment`, `CallForegroundService`).
- Incoming call UI with ringing animation (`IncomingCallActivity`, `lottie_ringing`).
- In-call controls: mute, camera toggle, camera switch, **virtual background removal** (Agora extension), and **screen sharing** (`MyScreenShareService`).
- Participant grid view for group calls (`ParticipantAdapter`, `item_call_participant_grid`).

### 🧑‍🤝‍🧑 Social & Community
- Friends system: add, manage, and view friend profiles (`ManageFriendsFragment`, `AddFriendDialog`, `ProfileFragment`, `EditProfileFragment`).
- Community feed: create posts, comment, and share within a server's post channel (`CreatePostFragment`, `PostChannelFragment`, `PostCommentFragment`, `CommentListAdapter`).
- Home screen with direct-message list and a dedicated notifications tab (`HomeFragment`, `HomeDMAdapter`, `NotificationsFragment`).

### 🔔 Notifications & Background Services
- Push notifications via Firebase Cloud Messaging (`MyFirebaseMessagingService`, `MessageNotificationService`, `NotificationActionReceiver`).
- Scheduled reminders (`ReminderReceiver`) and network status monitoring (`NetworkMonitor`).

### 🎨 Personalization
- Light/dark theming and custom app wallpapers/backgrounds (`ThemeHelper`, `bg_cozy_study.png`, `bg_cyberpunk_office.png`, `bg_misty_forest.png`).
- Upgrade/plan management UI (`UpgradePlanFragment`, `dialog_payment_method`).

### 🖥️ Backend (`calling-app-backend`)
- **Express.js** server issuing Agora RTC tokens (`agoraController.js`, `agoraRoutes.js`) for secure call session authentication.
- **Cloudinary** upload endpoints for chat/media attachments (`cloudinaryController.js`, `cloudinaryRoutes.js`).
- **Firebase Admin SDK** integration and auth middleware (`middlewares/auth.js`).

---

## 🏛️ System Architecture

The project uses a **modular, multi-module Gradle** structure on the client, paired with a lightweight Node.js backend:

1. **`app` module:** The main Android application — UI (Fragments/Activities), ViewModels, and feature-specific repositories, organized by feature (`auth`, `call`, `chat`, `friend`, `home`, `post`, `server`) plus shared `core` utilities (viewers, notifications, theming).
2. **`core/model`:** Shared domain models (`User`, `Server`, `ServerMember`, `ChatChannel`, `CallChannel`, `Message`, `Comment`, `Post`, `PostChannel`, `Participant`, `NotificationItem`).
3. **`core/network`:** Shared networking layer (`ApiClient`, `BackendService`) for talking to `calling-app-backend`.
4. **`core/di`:** Dependency injection module (Hilt) wiring dependencies across features (`AppModule`).
5. **Firebase:** Firestore/Realtime Database for servers, channels, messages, and profiles; Firebase Auth for sign-in; Cloud Messaging for push notifications. Rules are defined in `firestore.rules` and `database.rules.json`.
6. **`calling-app-backend`:** Node.js/Express service responsible for Agora token generation and Cloudinary media handling, authenticated via Firebase.

---

## 🛠️ Technology Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Development Environment** | `Android Studio` | Primary IDE. |
| **Language** | `Java` | Application logic across all modules. |
| **DI** | `Dagger Hilt` | Dependency injection (`core/di`). |
| **Local Cache** | `Room` | Offline message caching (`AppDatabase`, `MessageDao`). |
| **Networking** | `Retrofit` + `Gson` | REST calls to the backend. |
| **Voice/Video** | `Agora RTC SDK` (full-sdk + virtual-background) | Group calls, virtual background. |
| **Backend & Database** | `Firebase` (Firestore, Realtime Database, Auth, Cloud Messaging) | Data storage, auth, push notifications. |
| **Media Storage** | `Cloudinary` (Android SDK + backend API) | Image/file uploads. |
| **Image Loading** | `Glide` | Image loading/caching in the UI. |
| **Animations** | `Lottie` | Ringing, typing, and audio-wave animations. |
| **Backend Server** | `Node.js` / `Express` | Agora token issuance and Cloudinary proxy. |
| **Build Tool** | `Gradle` (Kotlin DSL) | Multi-module build configuration. |

---

## 📂 Project Structure

*   **`app`**: Main Android app module — feature packages (`auth`, `call`, `chat`, `friend`, `home`, `post`, `server`) plus `core` UI utilities (viewers, notification/reminder services, theming).
*   **`core/model`**: Shared domain model classes.
*   **`core/network`**: Shared API client and backend service interface.
*   **`core/di`**: Hilt dependency injection module.
*   **`calling-app-backend`**: Node.js/Express backend — Agora and Cloudinary modules, Firebase config, auth middleware.
*   **`database.rules.json`**: Firebase Realtime Database security rules.
*   **`firestore.rules`**: Firestore security rules.
*   **`gradle`**: Gradle wrapper and version catalog (`libs.versions.toml`).

---

## 🚀 Getting Started

### Prerequisites
*   **Android Studio** (recent stable version)
*   **JDK** compatible with the project's Gradle/Java configuration
*   A **Firebase project** with Firestore/Realtime Database, Authentication, and Cloud Messaging enabled
*   An **Agora.io** project (App ID + token server credentials)
*   A **Cloudinary** account for media storage
*   **Node.js** (for running `calling-app-backend`)

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/miyxotkem/SE114_CallingApp.git
   ```

2. **Set up Firebase:**
   - Create a Firebase project and enable Authentication, Firestore/Realtime Database, and Cloud Messaging.
   - Download `google-services.json` and place it in `app/` (already present as a placeholder in this repo — replace with your own).
   - Deploy `database.rules.json` and `firestore.rules` to your project.

3. **Set up the backend:**
   ```bash
   cd calling-app-backend
   cp .env.example .env   # fill in Agora, Cloudinary, and Firebase credentials
   npm install
   node index.js
   ```

4. **Configure the Android app:**
   - Point `core/network`'s `ApiClient` base URL at your running backend.
   - Add your Agora App ID to the relevant call configuration.

5. **Open in Android Studio:**
   - Open the repository root and let Gradle sync.

6. **Build and Run:**
   - Select a device or emulator and click **Run**.

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/YourFeatureName`).
3. Commit your changes (`git commit -m 'Add some feature'`).
4. Push to the branch (`git push origin feature/YourFeatureName`).
5. Open a Pull Request.

---

## 👨‍💻 Team & Collaborators

**Võ Tấn Nhã**  
*Software Engineering Student @ UIT*
* **GitHub:** [@nha-blip](https://github.com/nha-blip)
* **Focus:** Full-Stack .NET, System Architecture & API Design

**Thinh Phat Ho**  
*Software Engineering Student @ UIT*
* **GitHub:** [@miyxotkem](https://github.com/miyxotkem)
* **Focus:** Full-Stack .NET, System Architecture & API Design

**Đinh Quang Nhật**  
*Software Engineering Student @ UIT*
* **GitHub:** [@PeterBrr](https://github.com/PeterBrr)
* **Focus:** Full-Stack .NET, System Architecture & API Design

**innguyen**  
*Software Engineering Student @ UIT*
* **GitHub:** [@innguyen](https://github.com/innguyen)
* **Focus:** Full-Stack .NET, System Architecture & API Design
