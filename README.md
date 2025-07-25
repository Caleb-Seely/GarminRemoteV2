# CameraClick – Remote Camera Control from Your Garmin Watch

**CameraClick** is an Android app with a Garmin Connect IQ companion app that lets your smartwatch remotely trigger your Android device’s camera for both **photo** and **video** capture. Whether you're taking group shots, recording action clips, or just enjoying hands-free photography, CameraClick gives you seamless control right from your wrist.

---

## 📦 Overview

CameraClick includes two components:

- **Android App** – Runs on your phone, managing camera control, device discovery, and communication.
- **Garmin Watch App** – Runs on your smartwatch, with a clean and intuitive interface for remote triggering.

---

## ✨ Features

### Android App
- 🔄 **Device Discovery**: Automatically detects and connects to nearby Garmin devices  
- 📸 **Remote Camera Control**: Trigger photo and video capture from your Garmin watch  
- 🔔 **Background Service**: Maintains connection with your watch even in the background  
- 🎯 **Accessibility Integration**: Uses Android’s Accessibility Service for reliable shutter triggering  
- 📊 **Firebase Analytics & Crash Reporting**  
- 🚀 **Auto-Launch**: Optionally launches your default camera app when the watch connects  

### Garmin Watch App
- 🎛️ **Minimal UI**: Clean, icon-based interface  
- 📷 **Remote Triggering**: Send a message to capture a photo/video  

---

## ✅ Requirements

### Android App
- Android 6.0 (API 23) or higher  
- Garmin Connect Mobile app installed  
- A compatible camera app  
- Permissions: `Bluetooth`, `Notifications`, `Accessibility`, `Foreground Service`, `Internet`

### Garmin Watch App
- Compatible Garmin smartwatch with Connect IQ support  
- Connect IQ version 3.2 or higher  

---

## 🛠️ How It Works

When CameraClick is running on your Android phone (even in the background), it **listens for incoming messages** from the Garmin companion app.  

When a message is received:
1. The app scans the screen using Android’s **Accessibility Service**.
2. It identifies all visible UI elements and locates the **largest clickable button**.
3. It simulates a click on that button to trigger a photo or video capture.

> ⚠️ Because this action requires analyzing and interacting with other apps on the screen, **Accessibility permissions** are essential for CameraClick to function.

---

### 🔒 Privacy First

CameraClick uses Android’s **AccessibilityService** to perform a very specific task: simulate a tap on your phone’s camera shutter when triggered from your Garmin watch. This is the only reason the service exists — it’s not used for any form of surveillance, keylogging, or data harvesting.

I want to be completely transparent:  
**I have no interest in collecting personal data.**  
CameraClick does **not** read or store any app content, camera images, or personal information. The AccessibilityService is only used because it’s the **only available method** on Android to interact with other apps in this way.

The only data I do collect is **non-personal usage statistics** — things like how often the remote shutter is triggered or whether the app has crashed. These help me improve reliability, diagnose bugs, and better support the app.  
**No app names, no user identifiers, and no camera data is ever collected or stored.**

Apps and their button prefrences are stored locally on device only.

If you ever have questions about what’s being used or why, reach out — I'm happy to explain it. I built CameraClick for myself, and I’m sharing it with others who want the same simple, private functionality.
---

## 🚀 Installation

### Android App
1. Download the latest APK from the [Releases](https://github.com/Caleb-Seely/GarminRemoteV2/raw/main/Comm%20Android/app/release/app-release.apk) page  
2. Enable “Install from Unknown Sources” in your Android settings  
3. Install the APK on your device  
4. Enable restricted settings for the CameraClick app:
    - Open **Settings** > **Apps**  
    - Tap **CameraClick**  
    - Tap **More** > **Allow restricted settings**  
    - Follow the on-screen instructions  

### Garmin Watch App
- Install the companion app via the Garmin Connect [IQ Store](https://apps.garmin.com/apps/789012f0-dcb2-46c2-b5b8-ef00a75968fa)

---

## ⚙️ Setup

1. Launch the **CameraClick** app on your Android phone  
2. Grant required permissions when prompted:
   - Notifications  
   - Accessibility Service  
   - Enable **Switch Access** (required on Android 15+)  
3. Select your Garmin watch from the device list  
4. Launch the watch app – it will auto-connect if the phone app is running  
5. Open your preferred camera app  
6. Use the **action/start button** on your Garmin watch to trigger photo or video capture  

---

## 🔐 Permissions Used

- `BLUETOOTH`, `BLUETOOTH_ADMIN`: For device discovery and connection  
- `FOREGROUND_SERVICE`: Maintain service while app is in background  
- `POST_NOTIFICATIONS`: For service status notifications  
- `SYSTEM_ALERT_WINDOW`: Overlay functionality for Accessibility Service  
- `INTERNET`, `ACCESS_NETWORK_STATE`: Required for Firebase analytics and crash reporting  

---

## 📥 Download

[**⬇ Download Latest app-release.apk**](https://github.com/Caleb-Seely/GarminRemoteV2/raw/main/Comm%20Android/app/release/app-release.apk)

---

## ⌚ Garmin App

You can install the Garmin companion app directly from the [Connect IQ Store](https://apps.garmin.com/apps/789012f0-dcb2-46c2-b5b8-ef00a75968fa)

---

