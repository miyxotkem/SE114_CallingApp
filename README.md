<!-- HEADER BANNER -->
<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:4527A0,100:00BCD4&height=200&section=header&text=📞%20CallingApp&fontSize=60&fontColor=ffffff&desc=Real-Time%20Communication%20Platform%20for%20Android&descAlignY=75" width="100%" alt="CallingApp Banner" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin%20%2F%20Java-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin/Java" />
  <img src="https://img.shields.io/badge/Backend-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase" />
  <img src="https://img.shields.io/badge/Build-Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle" />
</p>

---

## 📞 Overview

**SE114_CallingApp** is an Android communication app designed to give users a real-time space to connect. The app centers around organizing contact groups (servers and channels) and flexibly switching between text messaging and voice calls, all backed by Firebase for data, auth, and call signaling.

---

## ✨ Key Features

- **Server & Channel Management:** Create independent servers and split them into multiple channels, making it easy to organize different conversation groups.
- **Text Chat:** Send and receive messages in real time within each channel.
- **Voice Calling:** Smooth voice calls between users, powered by a Firebase-based signaling mechanism.
- **User Authentication:** Sign-up, login, and account security handled through Firebase Authentication.

---

## 🏛️ System Architecture

The project follows a **Client-Backend** structure built around Firebase's real-time capabilities:

1. **Client (Android App):** Built with Kotlin/Java, handling the UI for servers, channels, chat, and calls.
2. **Signaling & Data (Firebase):** Firebase Realtime Database / Firestore stores servers, channels, and messages, and also acts as the signaling server that coordinates voice call setup between peers.
3. **Authentication (Firebase Auth):** Manages user sign-up, login, and session security.
4. **Backend (`calling-app-backend`):** Supporting backend service for the app's server-side logic.

---

## 🛠️ Technology Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Development Environment** | `Android Studio` | Primary IDE for building the app. |
| **Language** | `Kotlin` / `Java` | Core application logic. |
| **Backend & Database** | `Firebase` (Realtime Database / Firestore, Authentication) | Data storage, auth, and call signaling. |
| **Signaling** | `Firebase` | Used as the signaling server for voice call setup. |
| **Build Tool** | `Gradle` (Kotlin DSL) | Project build and dependency management. |

---

## 📂 Project Structure

Core folders in this repository:

*   **`app`**: Main Android application module (UI, chat, calling, and authentication logic).
*   **`core`**: Shared/core application code used across the app.
*   **`calling-app-backend`**: Backend service supporting the app.
*   **`database.rules.json`**: Firebase Realtime Database security rules.
*   **`firestore.rules`**: Firestore security rules.
*   **`gradle`**: Gradle wrapper files.

---

## 🚀 Getting Started

Follow these instructions to set up the project on your local machine for development and testing.

### Prerequisites
*   **Android Studio** (recent stable version)
*   **JDK** compatible with the project's Gradle/Kotlin configuration
*   A **Firebase project** with Realtime Database / Firestore and Authentication enabled

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/miyxotkem/SE114_CallingApp.git
   ```

2. **Set up Firebase:**
   - Create a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
   - Enable **Authentication**, **Realtime Database** (and/or **Firestore**).
   - Download your `google-services.json` and place it in the `app/` module directory.
   - Deploy the provided `database.rules.json` and `firestore.rules` to your Firebase project.

3. **Open the project:**
   - Open the repository root in **Android Studio**.
   - Let Gradle sync and download dependencies.

4. **Set up the backend (optional):**
   - If using `calling-app-backend`, configure and run it separately per its own setup instructions.

5. **Build and Run:**
   - Select a device or emulator.
   - Click **Run** in Android Studio to build and launch the app.

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!
If you would like to contribute:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/YourFeatureName`).
3. Commit your changes (`git commit -m 'Add some feature'`).
4. Push to the branch (`git push origin feature/YourFeatureName`).
5. Open a Pull Request.

---

## 👨‍💻 Team & Collaborators

**Thinh Phat Ho**
*Software Engineering Student @ UIT*
* **GitHub:** [@miyxotkem](https://github.com/miyxotkem)
* **Focus:** Full-Stack .NET, System Architecture & API Design
