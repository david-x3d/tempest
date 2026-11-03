<p align="center">
  <img src="branding/logo-round.svg" width="128" alt="Tempest-Logo">
</p>

<h1 align="center">Tempest</h1>

<p align="center">
  Ein welliges, expressives Thermometer für Akku und SoC deines Android-Handys.<br>
  <a href="README.md">English</a> · <b>Deutsch</b>
</p>

---

Tempest zeigt, wie warm dein Handy wirklich ist. Die Akkutemperatur sitzt in einer sich
verwandelnden Material-Form in einem welligen Ring, jede Thermal Zone des Kernels bekommt
ihren eigenen Wellenbalken, und die letzten 30 Minuten erscheinen als lebendige Welle, die
umso stärker schwingt, je heißer es wird. Gebaut mit Jetpack Compose und Material 3 Expressive.

## Screenshots

| Akku | Sensoren | Einstellungen |
|:---:|:---:|:---:|
| <img src="docs/screenshots/battery.png" width="200"> | <img src="docs/screenshots/sensors.png" width="200"> | <img src="docs/screenshots/settings.png" width="200"> |

<sub>Im Android-Emulator aufgenommen, deshalb zeigt die Thermik-Seite nur dessen einzige Zone.</sub>

## Funktionen

- **Akkutemperatur live** über `ACTION_BATTERY_CHANGED`, dazu Spannung, Strom, Ladestand,
  Ladestatus, Stromquelle und Zustand
- **Thermal Zones** aus `/sys/class/thermal/thermal_zone*`, die heißeste zuerst, jede mit
  einem Wellenbalken, dessen Amplitude und Wellenlänge der Temperatur folgen
- **Root optional**: Mit KernelSU, Magisk oder einem anderen `su` werden alle Zonen per
  `su -c` gelesen; ohne Root zeigt Tempest die Zonen, die Apps lesen dürfen
- **Verlauf über 30 Minuten** für Akku und heißeste SoC-Zone als animierte Sinuswelle, deren
  Amplitude und Frequenz mit der Temperatur steigen
- **Mehr Messwerte**: Leistung, Restladung, Ladezyklen (ab Android 14), Akkutechnologie,
  Zeit bis voll sowie ein Trend in °C pro Minute
- **Mehr Sensoren**: thermischer Spielraum, CPU-Takt je Kern, Kühlgeräte und die
  Umgebungssensoren des Handys (Umgebungstemperatur, Luftfeuchtigkeit, Luftdruck, Licht)
- **Eigene Temperaturstufen**: Lege fest, was kalt, kühl, warm, heiß und ultraheiß ist;
  Farben, Formen und Wellen richten sich danach
- **Einstellbare Warnschwellen** (Standard: 40 °C Akku, 45 °C SoC) mit Hinweis und
  haptischem Feedback beim Überschreiten
- **Große Hintergrund-Alarme**: optionale Überwachung mit Heads-up-Benachrichtigung in
  riesiger Schrift und, wenn erlaubt, Vollbild-Alarm auch auf dem Sperrbildschirm
- **CPU-Tab** im Stil von CPU-Z: Chipname mit flachem Emblem je Hersteller (Snapdragon,
  Dimensity, Exynos, Tensor, Kirin, Unisoc), Live-Takt jedes Kerns, Cluster mit Kerntypen
  (Cortex-X4, Cortex-A720 …), Governor, GPU-Renderer mit OpenGL-ES- und Vulkan-Version,
  GPU-Auslastung und -Takt wo der Treiber es erlaubt, RAM, Swap, Speicherplatz, Display und System
- **Sensoren-Tab**: alle Sensoren des Handys mit Live-Werten, kleinem Live-Graphen, Filtern
  (Bewegung, Position, Umgebung) und komplettem Datenblatt per Antippen
- **Mehr bei Alarmen**: Test-Alarm, Erinnerung alle 2, 5 oder 10 Minuten solange es heiß bleibt,
  und Vorlagen (Standard, Gaming, Vorsichtig) für die Temperaturstufen
- **Schnelleinstellungs-Kachel**, **°C oder °F** und **CSV-Export** des Verlaufs
- **Material 3 Expressive**: `MaterialShapes` mit Shape-Morphing (Cookie, Sunny, Pill, Burst),
  `CircularWavyProgressIndicator` und `LinearWavyProgressIndicator`, Federanimationen mit
  `MotionScheme.expressive()`, variable Schrift Roboto Flex, die mit der Hitze kräftiger wird,
  Farbskala von kühlem Blau bis heißem Rot, Dynamic Color, dunkles Design, Edge-to-Edge
- **Privat**: keine Internetberechtigung, nichts verlässt das Gerät

## Sprachen

Englisch (Standard), Deutsch, Französisch und Italienisch. Tempest folgt der Systemsprache.
Eine andere Sprache wählst du in den App-Einstellungen oder im App-Sprachmenü von Android
(ab Android 13).

## Bauen

Voraussetzungen: JDK 17 und das Android SDK (Compile SDK 37).

```bash
git clone https://github.com/david-x3d/tempest.git
cd tempest
./gradlew assembleDebug
```

Die APK liegt danach in `app/build/outputs/apk/debug/`. Jeder Push auf `main` baut sie auch
per GitHub Actions. Du findest sie im Bereich **Artifacts** des jeweiligen Laufs.

Mindestens Android 12 (API 31).

## Root (optional)

Root ist nie nötig. Schalte **Einstellungen → Root-Zugriff** ein und bestätige die Anfrage
deines Root-Managers. Tempest führt dann pro Aktualisierung eine einzige lesende
Shell-Schleife aus (`cat /sys/class/thermal/thermal_zone*/type` und `.../temp`) und sonst
nichts. Wird Root verweigert, zeigt Tempest die ohne Root lesbaren Zonen.

## Architektur

MVVM mit Kotlin Coroutines: Repositories liefern `StateFlow`s (Akku, Thermal Zones,
Einstellungen in DataStore, Verlauf im Speicher), je Screen kombiniert ein `ViewModel` sie
zu einem UI-State, und Compose stellt ihn dar.

```
data/      battery · thermal (inkl. RootShell) · device · sensors · history · settings
ui/        battery · thermal · cpu · sensors · settings · components · theme
```

## Lizenz

[MIT](LICENSE). Die enthaltene Schrift Roboto Flex steht unter der
[SIL Open Font License 1.1](third_party/roboto-flex/OFL.txt).
