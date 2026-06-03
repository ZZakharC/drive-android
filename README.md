# Drive Android Client — Mobile File Manager

![License](https://img.shields.io/badge/License-GPLv3-green)
![Platform](https://img.shields.io/badge/Platform-Android-red)
![Language](https://img.shields.io/badge/Language-Java-blue)
![Backend](https://img.shields.io/badge/API-29-orange)

🇬🇧 English (Translation) | [🇷🇺 Русский язык](./RU.md)

Android client for the **Drive cloud storage system**.
Provides mobile access to file management, authentication, and user accounts via the Drive backend API.

[Repositories with a web client and server](https://github.com/ZZakharC/drive)

---

## 🚀 Features

### 🔐 Authentication

* User login and registration
* Session-based authentication (via backend)
* Token/session persistence
* Auto-login support

### 📁 File Manager

* Browse folders and files
* Upload files from device storage
* Download files locally
* Delete files (based on permissions)
* File type visualization (icons per format)

### 👥 User System

* User profile access
* Role-based permissions (R / C / A)
* Admin-restricted actions (if permitted)

### 📡 Server Integration

* Direct communication with Drive backend
* HTTPS support (self-signed certificates supported)
* JSON-based API communication

---

## 🧱 Technology Stack

| Layer        | Technologies                           |
| ------------ | -------------------------------------- |
| Language     | Java                                   |
| UI           | Android XML (Views)                    |
| Networking   | HttpURLConnection / custom HTTP client |
| Architecture | Activity-based (MVC-like structure)    |
| Backend      | Drive Node.js server                   |

---

## ⚙️ Setup & Configuration

### 📌 Requirements

* Android Studio
* Android SDK 24+
* Running Drive backend server

---

### Configure backend URL

Edit:

```
app/src/main/java/org/example/drive/Config.java
```

Example:

```java
public class Config {
    public static final String SERVER_URL = "https://192.168.1.10:3000";
}
```

---

### Run application

* Start Drive backend server
* Launch Android app
* Login or register user
* Start managing files

---

## 📂 Project Structure

```
app/src/main/java/org/example/drive/
├── AdminActivity.java     # Admin panel
├── Auth.java              # Authentication logic
├── Config.java            # Server configuration
├── DriveActivity.java     # File manager UI
├── LoginActivity.java     # Login screen
├── MainActivity.java      # Entry point
├── RegisterActivity.java  # Registration
├── Server.java            # API communication layer
├── Utils.java             # Helpers
```

---

## 🔐 Permissions Model

Follows backend bitmask system:

| Bit | Permission |
| --- | ---------- |
| 1   | Read (R)   |
| 2   | Change (C) |
| 4   | Admin (A)  |

Client enforces UI restrictions based on received permissions.

---

## 🔒 Security Notes

* Session-based auth (managed by backend)
* HTTPS supported
* No local sensitive storage (except session token)
* Permission validation enforced server-side

---

## Support the Project

[![Donate](https://img.shields.io/badge/Donate-Support%20project-007BFF?style=for-the-badge)](https://pay.cloudtips.ru/p/204a4487)

---

## 📄 License

GNU GPL v3 — see [LICENSE](LICENSE)
