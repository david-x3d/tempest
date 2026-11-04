<div align="center">

<img src="branding/logo-round.svg" width="96" alt="">

# Tempest

**A wavy, expressive thermometer for your Android phone's battery and chip.**

[![Release](https://img.shields.io/github/v/release/david-x3d/tempest?label=Release&color=B3261E)](https://github.com/david-x3d/tempest/releases/latest)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](#install)
[![Material 3 Expressive](https://img.shields.io/badge/Material%203-Expressive-7F67BE?logo=materialdesign&logoColor=white)](#how-it-works)
[![Build](https://img.shields.io/github/actions/workflow/status/david-x3d/tempest/build.yml?branch=main&label=Build)](https://github.com/david-x3d/tempest/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/david-x3d/tempest?label=License)](LICENSE)

<b>English</b> · <a href="README.de.md">Deutsch</a>

<br>

<img src="docs/screenshots/overview.png" width="150" alt="Overview">&nbsp;
<img src="docs/screenshots/battery.png" width="150" alt="Battery">&nbsp;
<img src="docs/screenshots/sensors.png" width="150" alt="Thermal">&nbsp;
<img src="docs/screenshots/cpu.png" width="150" alt="Chip">&nbsp;
<img src="docs/screenshots/settings.png" width="150" alt="Settings">

<sub>Captured on an OPPO CPH2913. Status bars are standardized for presentation; app readings are unchanged.</sub>

</div>

Most temperature apps show you a number. Tempest shows you how the heat *feels*: the temperature sits in a morphing Material shape inside a wavy gauge, every thermal zone gets its own wavy bar, and the history is drawn as a living wave that ripples harder the hotter your phone gets. No root needed, no ads, nothing leaves your device.

## At a glance

| | What you get |
|---|---|
| **Overview** | Battery and hottest SoC zone side by side, with heat level, trend in °C/min, charge and history |
| **Battery** | Temperature, voltage, current, power, charge cycles, health, time to full or runtime left |
| **Thermal** | Every thermal zone the kernel exposes, hottest first, plus cooling devices and thermal headroom |
| **CPU** | Chip name with maker emblem, live clock per core, clusters and core types, GPU, RAM, storage, display |
| **Sensors** | Every sensor the phone has, with live values, a mini graph and the full datasheet on tap |
| **History** | Up to 24 hours as an animated wave – scrub along it to read any point, see min / avg / max |
| **Alerts** | Heads-up notification in huge type, optional full-screen alarm on the lock screen, Quick Settings tile |

## More access, more readings

Tempest works out of the box. If you have [Shizuku](https://shizuku.rikka.app/) or root, it can see further.

| | Without | With Shizuku | With root |
|---|:---:|:---:|:---:|
| Battery temperature and stats | ✓ | ✓ | ✓ |
| Thermal zones apps may read | ✓ | ✓ | ✓ |
| **All** thermal zones | | ✓ | ✓ |
| CPU and GPU load | | ✓ | ✓ |
| Thermal HAL sensors (skin, modem, NPU …), hwmon, charger temps | | ✓ | ✓ |
| Battery capacity health and charge limits | | ✓ | ✓ |
| History while the app is closed | | ✓ | |

Everything Tempest runs is read-only. Shizuku wins over root when both are on.

## Install

Download `Tempest-….apk` from the [latest release](https://github.com/david-x3d/tempest/releases/latest) and open it on your phone. Android 12 or newer.

Releases are signed with the same key, so new versions install over the old one. Every push to `main` also builds a debug APK – grab it from the run's **Artifacts** on [GitHub Actions](https://github.com/david-x3d/tempest/actions/workflows/build.yml).

<details>
<summary><b>Setting up Shizuku</b></summary>

1. Install and start [Shizuku](https://shizuku.rikka.app/).
2. In Tempest, turn on **Settings → Shizuku access** and allow it in the prompt.

**Background recording** (Settings → Background recording) starts a second, tiny Shizuku service that samples battery and SoC temperature every 30 s to 5 min and keeps the last 24 hours in `/data/local/tmp/tempest/history.csv`. Tempest merges it into the chart when you open the app. No notification, no wake locks – it pauses while the phone sleeps. Without root, Shizuku stops on reboot; start it again and recording resumes.

</details>

<details>
<summary><b>Using root</b></summary>

Root is never required. Turn on **Settings → Root access** and approve the prompt from KernelSU, Magisk or your `su`. Tempest then runs one read-only shell loop per refresh (`cat /sys/class/thermal/thermal_zone*/type` and `.../temp`) and nothing else. If root is denied, it falls back to the zones it can read without it.

</details>

## Make it yours

**SoC profiles** decide when the chip counts as hot. They are tuning presets, not manufacturer-certified limits; battery limits stay separate (default: hot from 40 °C).

| Profile | For | Hot from |
|---|---|:---:|
| **Everyday** | Daily use | 60 °C |
| **Active use** | Heavy apps, navigation, camera | normal under load + 20 °C |
| **Gaming** | Long gaming sessions | normal under load + 25 °C |
| **Custom** | Your own limit | you choose |

"Normal under load" is adjustable (default 45 °C).

You can also:

- define your own **cold, cool, warm, hot and ultra hot** levels – colours, shapes and waves follow them – or start from a preset (default, gaming, careful)
- set **alert thresholds**, a test alert and **reminders** every 2, 5 or 10 minutes while it stays hot
- pick the **start tab**, **light / dark / system**, **dynamic colour**, and switch **waves, motion and haptics** on or off
- show **°C or °F** and **export the history as CSV**

## How it works

- **Material 3 Expressive throughout.** `MaterialShapes` morph between Cookie, Sunny, Pill and Burst as it heats up, `CircularWavyProgressIndicator` and `LinearWavyProgressIndicator` carry the readings, and `MotionScheme.expressive()` springs drive the motion.
- **Heat you can see.** A cool-blue to hot-red colour scale, waves whose amplitude and wavelength follow the temperature, and variable Roboto Flex type that gets heavier the hotter it gets.
- **Straight from the source.** Battery data comes from `ACTION_BATTERY_CHANGED`, zones from `/sys/class/thermal/thermal_zone*`, sensors from Android's `SensorManager`. Two-cell batteries are detected from their voltage.
- **Knows your chip.** A large built-in list maps model numbers to names (Snapdragon, Dimensity, Exynos, Tensor, Kirin, Unisoc – down to the Dimensity 9600 Pro, `MT6995`). For unknown chips there is an optional Wikipedia lookup.

## Privacy

Nothing about your device ever leaves it – no telemetry, no ads, no accounts. The internet permission is only used by the optional chip lookup, which is **off by default** and downloads a public chip list to match it locally.

## Languages

English, German, French and Italian. Tempest follows the system language; pick another one in the app's settings or in Android's per-app language menu (Android 13+).

## Development

Requirements: JDK 17 and the Android SDK (compile SDK 37).

```bash
git clone https://github.com/david-x3d/tempest.git
cd tempest
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

MVVM with Kotlin coroutines: repositories expose `StateFlow`s, one `ViewModel` per screen combines them into UI state, and Compose renders it.

```
data/       battery · thermal · device · sensors · history · settings · privilege (Shizuku, root)
alerts/     background monitor, notifications, full-screen alert, Quick Settings tile
ui/         overview · battery · thermal · cpu · sensors · settings · components · theme
branding/   logo sources and icon generator
```

## License

[MIT](LICENSE). The bundled Roboto Flex font is licensed under the [SIL Open Font License 1.1](third_party/roboto-flex/OFL.txt).
