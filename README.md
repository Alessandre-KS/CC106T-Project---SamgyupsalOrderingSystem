# Samgyupsal Ordering System

A Android app for ordering and managing Korean BBQ dining, built for our **CC106 Application Development** project for _Sir Catindoy_.

---

## What the App Does

### 1. Customer Mode
- **Table Entry**: Select a table (Table 1, 2, or 3) or scan a QR code.
- **Set Meal**: Choose between Set A (Pork), Set B (Pork & Beef), or Set C (Pork, Beef & Seafood).
- **Ordering**: Pick your favorite meats and sides, then tap submit.
- **Dining Timer**: Shows a 1-hour countdown timer once your food is served.
- **Call Staff / Bill**: Tap to call the waiter or request your bill.

### 2. Staff & Kitchen Mode
- **Live Orders**: See incoming orders from every table in real-time.
- **Order Status**: Update order status (`Preparing` &rarr; `Ready` &rarr; `Served`).
- **Table Timers**: Check which tables are dining and how much time they have left.
- **Checkout**: Settle bills and clear tables for the next customer.

### 3. Admin Mode
- Change meal set prices (Set A, B, C).
- Update staff and admin passkeys.
- Reset the database when testing.
- Check daily customer consumptions

---

## How to Run the App

### Option A: Install on Phone (Easiest)
1. Go to the [Releases](https://github.com/Alessandre-KS/CC106T-Project---SamgyupsalOrderingSystem/releases) tab.
2. Download **`SamgyupsalOrderingSystem-v1.0.2.apk`**.
3. Install and open the app on your Android phone.

### Option B: Open in Android Studio
1. Clone this repository or download the ZIP.
2. Open the folder in Android Studio.
3. Click the green **Run (▶)** button.

---

## Tech Used
- **Platform**: Android (Java)
- **Design**: Material Design
- **Cloud Backend**: x10hosting (`Database.sdf`)
