# SMS Bridge Pro 📡

> Turn your Android phone into a programmable SMS gateway send messages via a secure REST API from anywhere in the world.
---
## What is this?

SMS Bridge Pro runs an embedded **Ktor/Netty HTTP server directly on your Android device**. Any tool that can make an HTTP request: Postman, cURL, Python, Node.js, your backend can send SMS messages through your phone's SIM card by hitting a single authenticated endpoint.

No third-party SMS APIs. No per-message fees. No SIM registration. Just your phone.

```
Your App / Script
      │
      │  POST /api/v1/dispatch
      │  { "phone": "+1234567890", "text": "Hello!" }
      ▼
SMS Bridge Pro (running on Android)
      │
      │  SmsManager.sendTextMessage()
      ▼
 Your SIM Card → Recipient's Phone
```

---

## Features

- **3-Point Security Interlock** : every request validated against a session token, username, and password
- **Auto-rotating credentials** : new cryptographically secure credentials generated on every server start
- **Two gateway modes** : LOCAL (LAN) and GLOBAL (Ngrok tunnel)
- **Tap-to-copy credential cards** : credentials revealed and copied on tap
- **SMS Log tab** : live log of every sent message with status
- **Multipart SMS** : long messages auto-split and reassembled on recipient device
- **Foreground service** : server stays alive even when app is backgrounded
- **E.164 validation** : phone numbers validated before dispatch
- **Bulk sending** : comma-separated numbers in a single request

---

> _Toggle the server on, copy your credentials, and start dispatching SMS from anywhere._

| Home — Server Off | Home — Server Active | SMS Log |
|:-:|:-:|:-:|
| Dark `#0b0e14` UI | Credentials revealed on tap | Live dispatch log |

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| HTTP Server | Ktor 2.3.8 + Netty |
| JSON | Kotlinx Serialization |
| SMS | Android SmsManager |
| Background | Foreground Service |
| HTTP Client | OkHttp (Ngrok API) |
| Min SDK | Android 8.0 (API 26) |

---

## Getting Started

### Prerequisites
- Android Studio (Hedgehog or newer)
- Android device with an active SIM card (API 26+)
- Both device and PC on the same Wi-Fi (for LOCAL mode)

### Build

```bash
# 1. Clone the repo
git clone https://github.com/gsubigya/SMSBridgePro.git

# 2. Open in Android Studio
#    File → Open → select the SMSBridgePro folder

# 3. Sync Gradle (downloads ~150 MB of dependencies on first run)
#    Click "Sync Now" when prompted

# 4. Build & install
#    Build → Build Bundle(s)/APK(s) → Build APK(s)
#    OR press Shift+F10 to run directly on your device
```

### First Run

1. Open the app → toggle the **switch ON**
2. Grant the **Send SMS** permission when prompted
3. The server starts on port `8080` status changes to **ACTIVE**
4. Tap each credential card to reveal and copy it

---

## API Reference

### Base URL

| Mode | URL |
|---|---|
| LOCAL | `http://<device-lan-ip>:8080` |
| GLOBAL | `https://<your-id>.ngrok.io` (requires Ngrok) |

---

### Authentication — 3-Point Interlock

Every request to `/api/v1/dispatch` must include all three headers. They are displayed in the app and rotate on every server restart.

| Header | Description | Example |
|---|---|---|
| `X-SMS-Auth-Key` | 32-char hex session token | `3f8a2c91b047e56d...` |
| `SMS-Username` | Random node alias | `node_742` |
| `SMS-Password` | 12-char alphanumeric | `aB3kMnPqR7xZ` |

Missing or wrong credentials → **HTTP 401 Unauthorized**

---

### `GET /health`

Liveness check. No authentication required.

**Response `200 OK`**
```json
{
  "success": true,
  "message": "SMS Bridge Pro is running"
}
```

---

### `POST /api/v1/dispatch`

Send an SMS to one or more phone numbers.

**Headers**
```
X-SMS-Auth-Key : <token from app>
SMS-Username   : <username from app>
SMS-Password   : <password from app>
Content-Type   : application/json
```

**Request Body**
```json
{
  "phone": "+9779870293027, +918303730172",
  "text": "OTP: 5542. Valid for 5 minutes."
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `phone` | string | ✅ | E.164 number(s), comma-separated for bulk |
| `text` | string | ✅ | SMS body (auto-splits if > 160 chars) |

**Response `200 OK` — all sent**
```json
{
  "success": true,
  "message": "All messages dispatched successfully",
  "results": [
    { "phone": "+9779870293027", "sent": true },
    { "phone": "+918303730172",  "sent": true }
  ]
}
```

**Response `207 Multi-Status` partial failure**
```json
{
  "success": false,
  "message": "Some messages failed",
  "results": [
    { "phone": "+9779870293027", "sent": true },
    { "phone": "+000",           "sent": false, "error": "Invalid E.164 format" }
  ]
}
```

**Error responses**

| Code | Cause |
|---|---|
| `400` | Missing `Content-Type: application/json` header, or malformed JSON body |
| `401` | Missing or incorrect credentials |
| `500` | SmsManager error (no SIM, no permission, carrier rejection) |

---

## Postman Quick Start

Import the collection from the repo (`SMSBridgePro_Postman.json`) and update these variables:

| Variable | Value |
|---|---|
| `base_url` | `http://<your-device-ip>:8080` |
| `auth_key` | Paste from `X-SMS-Auth-Key` card in app |
| `username` | Paste from `SMS-Username` card in app |
| `password` | Paste from `SMS-Password` card in app |

> ⚠️ Always start with `GET /health` to confirm connectivity before adding credentials.

---

## cURL Examples

```bash
# Health check
curl http://192.168.1.42:8080/health

# Send to single number
curl -X POST http://192.168.1.42:8080/api/v1/dispatch \
  -H "X-SMS-Auth-Key: 3f8a2c91b047e56d1a0f4b8c7e2d9051" \
  -H "SMS-Username: node_742" \
  -H "SMS-Password: aB3kMnPqR7xZ" \
  -H "Content-Type: application/json" \
  -d '{"phone":"+9779870293027","text":"Hello from cURL!"}'

# Send to multiple numbers
curl -X POST http://192.168.1.42:8080/api/v1/dispatch \
  -H "X-SMS-Auth-Key: 3f8a2c91b047e56d1a0f4b8c7e2d9051" \
  -H "SMS-Username: node_742" \
  -H "SMS-Password: aB3kMnPqR7xZ" \
  -H "Content-Type: application/json" \
  -d '{"phone":"+9779870293027, +918303730172","text":"Bulk message!"}'
```

---

## Using with Ngrok (Global Mode)

To expose the server to the public internet:

```bash
# Install Ngrok on your Android device (via Termux or standalone APK)
ngrok http 8080
```

The app's **GLOBAL** tab auto-detects the tunnel URL from Ngrok's local API (`127.0.0.1:4040`). Use that URL in Postman instead of the LAN IP — works from anywhere in the world.

---

## Project Structure

```
SMSBridgePro/
└── app/src/main/
    ├── AndroidManifest.xml
    └── java/com/smsbridgepro/
        ├── SmsBridgeApplication.kt          # App init, notification channel
        ├── model/
        │   └── ApiModels.kt                 # Request/response data classes
        ├── security/
        │   └── SecureIdentityGenerator.kt   # Cryptographic credential generation
        ├── network/
        │   ├── KtorServerEngine.kt          # Embedded HTTP server + routes + auth
        │   ├── SmsDispatcher.kt             # SmsManager wrapper + E.164 validation
        │   └── NetworkUtils.kt              # LAN IP detection + Ngrok API query
        ├── service/
        │   └── SmsGatewayService.kt         # Foreground service (keeps server alive)
        └── ui/
            └── MainActivity.kt              # UI — toggle, credential cards, SMS log
```

---

## Security Notes

- Credentials are generated using `java.security.SecureRandom` (backed by `/dev/urandom`)
- The 32-char hex token provides ~128 bits of entropy
- Credentials rotate every time the server is restarted never reused across sessions
- For production use, run behind Ngrok with HTTPS never expose port 8080 directly to the internet
- The server only accepts requests that pass all 3 authentication checks partial credential matches are rejected

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Request timeout | PC and phone on different networks | Connect both to same Wi-Fi, or use Ngrok |
| `400 Bad Request` | Missing `Content-Type: application/json` | Add the header; set body to **raw → JSON** in Postman |
| `401 Unauthorized` | Wrong or stale credentials | Restart the server → copy fresh credentials from app |
| `500 Internal Server Error` | SMS permission not granted, or serialization plugin missing | Grant SMS permission; ensure `kotlin.plugin.serialization` is in `build.gradle` |
| Server shows ACTIVE but no SMS received | Carrier filtering, DND, or wrong number format | Use E.164 format (`+countrycode...`), check carrier SMS settings |
| App killed in background | Battery optimization | Settings → Apps → SMS Bridge Pro → Battery → Unrestricted |

---

## Contributing

Pull requests welcome. For major changes, open an issue first.

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit: `git commit -m "Add your feature"`
4. Push: `git push origin feature/your-feature`
5. Open a Pull Request

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Acknowledgements

- [Ktor](https://ktor.io/) — the embedded Kotlin HTTP server that makes this possible
- [Ngrok](https://ngrok.com/) — for the public tunnel support
- Architectural blueprint by **VIBECODE**

---

<p align="center">Built with Kotlin · Powered by Ktor · Runs on your SIM</p>
