# VND Wallet

A native Android companion for tracking Vietnamese dong cash and trip expenses, with INR equivalents and offline-first local storage.

## Features

- Dashboard with remaining balance, INR equivalent, cash-in total, total spending and recent purchases.
- Add, edit and delete expenses. Record the amount handed over and the actual purchase price; change is calculated, while only the actual price is deducted.
- Set initial cash and record additional cash.
- Track physical cash by VND denomination.
- Optional note-count fields for recording denominations handed over and received as change.
- Exchange-rate reference refresh, saved last-known rate for offline use, last rate date and last checked time.
- System, light and dark theme choices with a warm amber accent.
- Expense records and settings persist locally on the device.

## Exchange rate

The app fetches VND→INR from The Rates API, which publishes daily reference rates derived from central-bank data. The app checks for a new rate on launch when the saved rate date differs from the current UTC date, schedules a network-constrained 24-hour background refresh through Android WorkManager, and supports manual refresh. If the request fails, the saved rate remains available offline. Weekends and public holidays may show the previous working day's reference rate.

Reference rates are indicative only. Money changers, banks, cards and payment providers may use different buy/sell rates and fees. Confirm actual exchange rates before exchanging cash.

API reference: https://theratesapi.com/

## Build

GitHub Actions builds a debug APK on pushes and pull requests to the main branch. Download the ZIP from the workflow run's Artifacts section and extract app-debug.apk.

## Data and limitations

Wallet data is stored locally in app preferences and is not synced to a cloud account. The physical denomination count is a manual inventory; set it to match the cash you actually hold. When exact tracking is enabled and note counts are entered, those counts adjust the inventory automatically. If you skip the note counts, the app flags the inventory for reconciliation.
