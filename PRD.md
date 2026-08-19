# Product Requirement Document (PRD)
# Sovereign Peer-to-Peer Secure Chat Platform ("Chat App")

**Document Version:** 2.0.0 (Rebuild)  
**Status:** Approved / Architecture-Locked  
**Target Platform:** Android 8.0+ (API Level 26–35)  
**Primary Tech Stack:** Kotlin 2.1+, Jetpack Compose, Room (SQLite WAL), Hilt DI, ECDH / AES-256-GCM E2EE  

---

## 1. Executive Summary & Vision

The **Chat App** is a privacy-first peer-to-peer communication platform for Android. It provides zero-trust end-to-end encrypted (E2EE) messaging across local networks (LAN / Wi-Fi) and wide-area environments (Cellular / WAN) with **zero centralized user accounts, zero phone number requirements, and zero vendor lock-in**.

---

## 2. Core Functional Requirements

### 2.1 Module 1: Sovereign Identity & Key Management
- **FR-1.1 (Hardware-Backed Key Generation):** Generate an Elliptic Curve key pair (`secp256r1`) stored in the Android Keystore on first launch.
- **FR-1.2 (Sovereign Profile):** Support custom display name and local avatar generation/import.
- **FR-1.3 (Identity QR Presentation):** Dynamically render a QR code encoding the user's UUID, Public Key (Base64), local LAN IP, active messaging port, and cryptographic signature.

### 2.2 Module 2: Pairing & Trust-On-First-Use (TOFU)
- **FR-2.1 (Mutual QR Pairing):** Scan a peer's QR code to import their public identity and derive the mutual ECDH shared secret.
- **FR-2.2 (Key-Change Warning):** If a known contact presents a new/modified public key, alert the user with a security warning and require explicit confirmation before trusting the new key.
- **FR-2.3 (Contact Management):** Support custom nicknames, contact blocking, and contact deletion with session key erasure.

### 2.3 Module 3: Offline-First Messaging
- **FR-3.1 (Durable State First):** Messages are written to Room SQLite in `QUEUED` state before transport delivery is initiated.
- **FR-3.2 (Deterministic State Transitions):** Message states transition cleanly: `QUEUED` → `SENDING` → `SENT` → `DELIVERED` → `READ` (or `FAILED` with retry capability).
- **FR-3.3 (Idempotent Delivery):** Messages have globally unique UUIDs; duplicate packets do not create duplicate UI entries.

### 2.4 Module 4: Multi-Transport Routing
- **FR-4.1 (LAN Transport):** Direct TCP connection (Port 47832) for sub-50ms message arrival when on the same subnet.
- **FR-4.2 (Relay Transport):** Encrypted WAN transport over an SSE relay using HMAC topic hashing (`p2p_chat_<HMAC>`) to prevent user enumeration.

---

## 3. Cryptographic Invariants (Non-Negotiable)

- **Hard Failure on Error:** If encryption or decryption fails, the message send/receive fails explicitly. No plaintext or ciphertext fallback.
- **Zero Deterministic Keys:** Never derive AES keys from user IDs or plaintext strings.
- **Hardware Isolation:** Private keys never leave the Android Keystore.
