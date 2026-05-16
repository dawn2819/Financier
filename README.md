# 💰 Financier — Smart Finance Management

A premium dark-themed Android financial management app built with Kotlin, featuring AI-powered financial advice via Gemini API.

## ✨ Features

### 📊 Dashboard
- Real-time balance, income & expense tracking
- 7-day spending trend chart (MPAndroidChart)
- Quick access to recent transactions

### 💳 Transactions
- Full CRUD — Add, Edit, Delete with confirmation
- 12 expense categories with icons
- Custom numpad with thousands separator
- Search & filter by note/category
- Date picker for each transaction

### 📋 Budget
- Monthly budget per category with progress bars
- Color-coded indicators (🟢 <70% 🟠 70-90% 🔴 >90%)
- Month navigation (prev/next)
- Add/delete budgets

### 📈 Reports
- **PieChart** — Spending by category with donut hole
- **BarChart** — 6-month income vs expense comparison
- Time filter: Week / Month / Year
- Category breakdown list with percentages

### 🤖 AI Chat (Gemini)
- Financial advice powered by Google Gemini 1.5 Flash
- Context-aware — injects user's real financial data
- Offline fallback with keyword-based responses
- Quick action chips

### 👤 Profile & Settings
- Dark mode toggle
- Language switcher (Vietnamese / English)
- Currency switcher (VND ₫ / USD $)
- Manage financial accounts (Cash, Bank, E-Wallet)
- Biometric toggle
- Sign out

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| Architecture | MVVM + LiveData |
| Database | Room + SQLCipher (encrypted) |
| Networking | Retrofit + OkHttp |
| Charts | MPAndroidChart |
| UI | Material Design 3 (Dark Theme) |
| Navigation | Jetpack Navigation Component |
| AI | Google Gemini 1.5 Flash API |

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11+
- Android SDK 34
- Pixel 7 emulator (API 30+)

### Setup
1. Clone the repo:
   ```bash
   git clone https://github.com/YOUR_USERNAME/Financier.git
   ```

2. Add Gemini API key to `local.properties`:
   ```properties
   gemini.api.key=YOUR_GEMINI_API_KEY
   ```

3. Open in Android Studio → Sync Gradle → Run

### Default Login
- **Username:** `dawn`
- **Password:** `dawn123`

## 📁 Project Structure

```
app/src/main/java/com/financier/app/
├── common/          # Utils (CurrencyFormatter, SecurityUtils, SessionManager, Constants)
├── data/
│   ├── local/       # Room DB, DAOs, Entities
│   ├── remote/      # Gemini API service + Retrofit client
│   └── repository/  # AI Repository
├── ui/
│   ├── auth/        # Login + Register
│   ├── dashboard/   # Home screen
│   ├── transactions/# Transaction CRUD + Adapter
│   ├── budget/      # Budget tracking
│   ├── reports/     # Charts & analytics
│   ├── ai/          # Gemini AI chat
│   └── profile/     # Settings + Manage Accounts
└── MainActivity.kt
```

## 📄 License

This project is for educational purposes.

---

Built with ❤️ using Kotlin & Jetpack
