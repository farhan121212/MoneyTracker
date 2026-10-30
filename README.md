# Paisa

A personal money tracker for Android. It logs UPI and card spending across any number of bank accounts and credit cards, finds payments in bank SMS, handles split bills, recurring bills and savings goals, and shows where the money went. All data stays on the phone: the app has no internet permission.

![Screens](docs/preview.png)

## Install

[Download the APK](https://github.com/farhan121212/MoneyTracker/raw/main/dist/Paisa-1.1.apk) on your phone, open it, and allow installs from that source when Android asks. Play Protect may warn because the app isn't from the Play Store. Installing over an earlier version keeps your data.

If Android blocks the SMS permission, open the app's info page, tap the three dots, choose **Allow restricted settings**, and try again.

## Build

Needs JDK 17 and the Android SDK. Both are installed on this PC, without being set up on `PATH`:

- JDK: `%LOCALAPPDATA%\Programs\Temurin\jdk-17.0.20.1+1`
- SDK: `%LOCALAPPDATA%\Android\Sdk`, referenced from `local.properties`

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Temurin\jdk-17.0.20.1+1"
.\gradlew testDebugUnitTest assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

## Signing key

`keystore/paisa-release.jks` and `keystore.properties` sign the release build. Keep both safe and out of version control. Android only installs an update over an existing app when the signature matches, so losing them means reinstalling and restoring from a backup.

## How the money works

- Amounts are stored as whole paise.
- A card purchase counts as spending when you swipe. Paying the bill is a transfer, so it never counts twice.
- A split bill counts only your share as spending. The rest becomes what others owe you.
- Someone paying you back adds to your account but isn't income.

- Unpaid recurring bills come off what's safe to spend. A bill counts as paid when a payment this month has a matching name and an amount within 25%.
- Goals divide up what's in your savings. The money itself stays in the savings account.

`domain/Calc.kt` and `domain/Plans.kt` hold these rules, with tests in `app/src/test`.

## How SMS detection works

`domain/Sms.kt` reads a message and `app/src/test/.../SmsTest.kt` holds samples in each bank's real format. A message has to pass every check:

1. It's from a known bank sender and isn't marked promotional (`-P`).
2. It isn't an OTP, a failure, a bill reminder, or an advert. Links count against it after the "Not you?" footer is removed.
3. It has an amount and says money moved.
4. It names an account or card by its digits.

Nothing changes a balance until it's confirmed on the Review screen, and the text of the SMS is never stored. To support a new bank, add a sample to `SmsTest`, then add its sender code to `KNOWN_SENDERS` or the patterns as needed. A sender can also be added in Settings without a new build.

## Changing the database

Bump the version in `PaisaDb` and add a Room migration, because real data lives in it. `app/schemas` keeps a copy of each schema version to migrate from.

## Licences

Plus Jakarta Sans is used under the SIL Open Font License, included in `third_party/`.
