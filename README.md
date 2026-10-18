# Samgyupsal Ordering & Kitchen Management System

> **CC106 Application Development Final Project**  
> A full-stack, real-time Android restaurant ordering system featuring multi-table dining sessions, live kitchen order dispatch, table collision protection, and cloud database synchronization.

---

## Overview

The **Samgyupsal Ordering System** is designed for Korean BBQ restaurants to streamline customer self-ordering, kitchen dispatch, and table turnaround. The application synchronizes orders, table timers, and staff calls in real time between customer tablets/phones and the staff/kitchen management dashboard.

---

## Key Features

### 1. Customer Ordering & Table Entry
- **Table Selection with Collision Prevention**: Prevents two customers from claiming or overwriting the same dining table while actively occupied.
- **Set Meal Selection**: Set A (Pork only), Set B (Pork & Beef), Set C (Pork, Beef & Seafood) with live head count and pricing calculations.
- **Interactive Menu & Cart**: Clean, card-based Korean BBQ meat and side dish selection with instant order submission.
- **Add-on Ordering**: Seamless replenishments after the initial order is served without resetting the dining session.
- **Service Requests**: In-app "Call Staff" and "Request Checkout / Bill" actions sent directly to the staff floor alert queue.

### 2. Kitchen & Staff Dashboard
- **Live Order Feed**: Real-time order dispatch with order IDs (`ORD-T1-001`), head count, set meal details, and selected items.
- **Order Progression Lifecycle**:
  - `PREPARING` &rarr; Kitchen is assembling meats/sides.
  - `READY_FOR_PICKUP` &rarr; Staff alerted that tray is ready for delivery.
  - `SERVED` &rarr; Order delivered to table; triggers the **1-hour dining countdown**.
- **Real-Time Dining Timer**: Live synchronized countdown timers for each active table with expiration warnings and time extension controls (+30m).
- **Service Request Alerts**: Instant notification banner for customer assistance and checkout requests.
- **Table Checkout**: Marks table as paid/vacated and cleans up the session for the next party.

### 3. Admin Control Panel
- **Protected Access**: Role-based authentication with configurable staff and admin passkeys.
- **Live Menu Pricing**: Adjust Set A, B, and C pricing on the fly with instant backend synchronization.
- **Passkey Management**: Update security credentials without recompiling the app.
- **System Database Reset**: Clear test data with one click before a fresh operational shift.

---

## Tech Stack & Architecture

- **Mobile Client**: Android Java, Android Jetpack, Material Design 3 (Custom Korean BBQ Warm Theme).
- **Networking & Sync**: Asynchronous REST sync via `BackendSyncManager.java` with offline local memory fallback.
- **Cloud Backend**: PHP 8.1 RESTful API with LiteSpeed web server hosted on **x10hosting** (`https://cc106tsamgyup-orders.x10.mx`).
- **Local Backend Fallback**: C# ASP.NET Core Web API with embedded **Microsoft SQL Server Compact Edition (MS SQL CE `Database.sdf`)**.
- **Database Schema**:
  - `Orders`: Stores order ID, table code, head count, set meal, items JSON, add-on flag, status, timestamp.
  - `TableSessions`: Stores table dining start time, expiry time, and checkout status.
  - `ServiceRequests`: Customer call staff and checkout alerts.
  - `MenuPricing`: Dynamic pricing for Set Meal tiers.
  - `AppConfig`: System credentials and configuration key-values.

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 34+
- Java Development Kit (JDK 17+)
- Physical Android phone or emulator (Android 8.0+ / API 26+)

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/Alessandre-KS/CC106T-Project---SamgyupsalOrderingSystem.git
   ```
2. Open the project in **Android Studio**.
3. Allow Gradle to sync dependencies.
4. Run on an emulator or connect a physical Android device via USB.

### Download Pre-built APK
Pre-compiled APKs are available directly under the [Releases](https://github.com/Alessandre-KS/CC106T-Project---SamgyupsalOrderingSystem/releases) tab.

---

## Author
- **Course**: CC106 - Application Development
- **Project**: Samgyupsal Ordering & Kitchen Management System
