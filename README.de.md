<div align="center">

<img src="branding/logo-round.svg" width="96" alt="">

# Tempest

**Ein welliges, expressives Thermometer für Akku und Chip deines Android-Handys.**

[![Release](https://img.shields.io/github/v/release/david-x3d/tempest?label=Release&color=B3261E)](https://github.com/david-x3d/tempest/releases/latest)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](#installieren)
[![Material 3 Expressive](https://img.shields.io/badge/Material%203-Expressive-7F67BE?logo=materialdesign&logoColor=white)](#so-funktionierts)
[![Build](https://img.shields.io/github/actions/workflow/status/david-x3d/tempest/build.yml?branch=main&label=Build)](https://github.com/david-x3d/tempest/actions/workflows/build.yml)
[![Lizenz](https://img.shields.io/github/license/david-x3d/tempest?label=Lizenz)](LICENSE)

<a href="README.md">English</a> · <b>Deutsch</b>

<br>

<img src="docs/screenshots/overview.png" width="150" alt="Übersicht">&nbsp;
<img src="docs/screenshots/battery.png" width="150" alt="Akku">&nbsp;
<img src="docs/screenshots/sensors.png" width="150" alt="Thermik">&nbsp;
<img src="docs/screenshots/cpu.png" width="150" alt="Chip">&nbsp;
<img src="docs/screenshots/settings.png" width="150" alt="Einstellungen">

<sub>Auf einem OPPO CPH2913 aufgenommen. Die Statusleisten wurden für die Präsentation vereinheitlicht; die Messwerte der App sind unverändert.</sub>

</div>

Die meisten Temperatur-Apps zeigen dir eine Zahl. Tempest zeigt dir, wie sich die Hitze *anfühlt*: Die Temperatur sitzt in einer sich verwandelnden Material-Form in einem welligen Ring, jede Thermal Zone bekommt ihren eigenen Wellenbalken, und der Verlauf erscheint als lebendige Welle, die umso stärker schwingt, je heißer das Handy wird. Kein Root nötig, keine Werbung, nichts verlässt dein Gerät.

## Auf einen Blick

| | Was du bekommst |
|---|---|
| **Übersicht** | Akku und heißeste SoC-Zone nebeneinander, mit Stufe, Trend in °C/min, Ladestand und Verlauf |
| **Akku** | Temperatur, Spannung, Strom, Leistung, Ladezyklen, Zustand, Zeit bis voll oder Restlaufzeit |
| **Thermik** | Alle Thermal Zones des Kernels, die heißeste zuerst, dazu Kühlgeräte und thermischer Spielraum |
| **CPU** | Chipname mit Hersteller-Emblem, Live-Takt je Kern, Cluster und Kerntypen, GPU, RAM, Speicher, Display |
| **Sensoren** | Alle Sensoren des Handys mit Live-Werten, kleinem Graphen und Datenblatt per Antippen |
| **Verlauf** | Bis zu 24 Stunden als animierte Welle – mit dem Finger jeden Punkt ablesen, Min / Schnitt / Max |
| **Alarme** | Heads-up-Benachrichtigung in riesiger Schrift, optional Vollbild-Alarm auf dem Sperrbildschirm, Schnelleinstellungs-Kachel |

## Mehr Zugriff, mehr Messwerte

Tempest funktioniert sofort. Mit [Shizuku](https://shizuku.rikka.app/) oder Root sieht es mehr.

| | Ohne | Mit Shizuku | Mit Root |
|---|:---:|:---:|:---:|
| Akkutemperatur und -werte | ✓ | ✓ | ✓ |
| Thermal Zones, die Apps lesen dürfen | ✓ | ✓ | ✓ |
| **Alle** Thermal Zones | | ✓ | ✓ |
| CPU- und GPU-Last | | ✓ | ✓ |
| Sensoren des Thermal-HAL (Skin, Modem, NPU …), hwmon, Ladegerät-Temperaturen | | ✓ | ✓ |
| Akku-Kapazitätszustand und Ladelimits | | ✓ | ✓ |
| Verlauf bei geschlossener App | | ✓ | |

Tempest führt nur lesende Befehle aus. Sind beide an, hat Shizuku Vorrang.

## Installieren

Lade `Tempest-….apk` aus dem [neuesten Release](https://github.com/david-x3d/tempest/releases/latest) und öffne sie auf dem Handy. Ab Android 12.

Releases sind mit demselben Schlüssel signiert, neue Versionen installieren sich also über die alte. Jeder Push auf `main` baut außerdem eine Debug-APK – du findest sie im Bereich **Artifacts** des jeweiligen Laufs auf [GitHub Actions](https://github.com/david-x3d/tempest/actions/workflows/build.yml).

<details>
<summary><b>Shizuku einrichten</b></summary>

1. [Shizuku](https://shizuku.rikka.app/) installieren und starten.
2. In Tempest **Einstellungen → Shizuku-Zugriff** einschalten und in der Anfrage erlauben.

Die **Hintergrund-Aufzeichnung** (Einstellungen → Hintergrund-Aufzeichnung) startet einen zweiten, sehr kleinen Shizuku-Dienst, der alle 30 s bis 5 min Akku- und SoC-Temperatur misst und die letzten 24 Stunden in `/data/local/tmp/tempest/history.csv` hält. Beim Öffnen übernimmt Tempest sie in die Kurve. Ohne Benachrichtigung, ohne Wakelocks – sie pausiert, solange das Handy schläft. Ohne Root endet Shizuku beim Neustart; danach Shizuku wieder starten, dann läuft die Aufzeichnung weiter.

</details>

<details>
<summary><b>Root nutzen</b></summary>

Root ist nie nötig. Schalte **Einstellungen → Root-Zugriff** ein und bestätige die Anfrage von KernelSU, Magisk oder deinem `su`. Tempest führt dann pro Aktualisierung eine einzige lesende Shell-Schleife aus (`cat /sys/class/thermal/thermal_zone*/type` und `.../temp`) und sonst nichts. Wird Root verweigert, zeigt Tempest die ohne Root lesbaren Zonen.

</details>

## Nach deinem Geschmack

**SoC-Profile** legen fest, ab wann der Chip als heiß gilt. Sie sind Richtwerte, keine vom Hersteller bestätigten Grenzwerte; die Akku-Grenzen bleiben getrennt (Standard: heiß ab 40 °C).

| Profil | Für | Heiß ab |
|---|---|:---:|
| **Alltag** | Alltägliche Nutzung | 60 °C |
| **Aktive Nutzung** | Schwere Apps, Navigation, Kamera | Normal unter Last + 20 °C |
| **Gaming** | Lange Spielesessions | Normal unter Last + 25 °C |
| **Eigene Werte** | Deine eigene Grenze | frei wählbar |

„Normal unter Last" ist einstellbar (Standard 45 °C).

Außerdem kannst du:

- eigene Stufen für **kalt, kühl, warm, heiß und ultraheiß** festlegen – Farben, Formen und Wellen richten sich danach – oder mit einer Vorlage starten (Standard, Gaming, Vorsichtig)
- **Warnschwellen**, einen Test-Alarm und **Erinnerungen** alle 2, 5 oder 10 Minuten einstellen, solange es heiß bleibt
- **Start-Tab**, **Hell / Dunkel / System** und **dynamische Farben** wählen sowie **Wellen, Animationen und Haptik** ein- oder ausschalten
- **°C oder °F** anzeigen und den **Verlauf als CSV exportieren**

## So funktioniert's

- **Durchgehend Material 3 Expressive.** `MaterialShapes` verwandeln sich mit der Hitze zwischen Cookie, Sunny, Pill und Burst, `CircularWavyProgressIndicator` und `LinearWavyProgressIndicator` tragen die Messwerte, und Federn aus `MotionScheme.expressive()` sorgen für die Bewegung.
- **Hitze, die man sieht.** Eine Farbskala von kühlem Blau bis heißem Rot, Wellen, deren Amplitude und Wellenlänge der Temperatur folgen, und die variable Schrift Roboto Flex, die mit der Hitze kräftiger wird.
- **Direkt an der Quelle.** Akkudaten kommen aus `ACTION_BATTERY_CHANGED`, Zonen aus `/sys/class/thermal/thermal_zone*`, Sensoren aus Androids `SensorManager`. Akkus mit zwei Zellen werden an der Spannung erkannt.
- **Kennt deinen Chip.** Eine große eingebaute Liste ordnet Modellnummern Namen zu (Snapdragon, Dimensity, Exynos, Tensor, Kirin, Unisoc – bis zum Dimensity 9600 Pro, `MT6995`). Für unbekannte Chips gibt es eine optionale Wikipedia-Suche.

## Privatsphäre

Nichts über dein Gerät verlässt es – keine Telemetrie, keine Werbung, keine Konten. Die Internetberechtigung nutzt nur die optionale Chip-Suche, die **standardmäßig aus** ist und eine öffentliche Chipliste lädt, um sie lokal abzugleichen.

## Sprachen

Englisch, Deutsch, Französisch und Italienisch. Tempest folgt der Systemsprache; eine andere wählst du in den App-Einstellungen oder im App-Sprachmenü von Android (ab Android 13).

## Entwicklung

Voraussetzungen: JDK 17 und das Android SDK (Compile SDK 37).

```bash
git clone https://github.com/david-x3d/tempest.git
cd tempest
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # Unit-Tests
```

MVVM mit Kotlin Coroutines: Repositories liefern `StateFlow`s, je Screen kombiniert ein `ViewModel` sie zu einem UI-State, und Compose stellt ihn dar.

```
data/       battery · thermal · device · sensors · history · settings · privilege (Shizuku, Root)
alerts/     Hintergrund-Überwachung, Benachrichtigungen, Vollbild-Alarm, Schnelleinstellungs-Kachel
ui/         overview · battery · thermal · cpu · sensors · settings · components · theme
branding/   Logo-Quellen und Icon-Generator
```

## Lizenz

[MIT](LICENSE). Die enthaltene Schrift Roboto Flex steht unter der [SIL Open Font License 1.1](third_party/roboto-flex/OFL.txt).
