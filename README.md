# Sustenance

A private, local [Health Connect](https://developer.android.com/health-connect)-powered
nutrition tracker.

By default, Sustenance is read-only and beautifully displays your Health Connect data with
customizable goals.

And if you supply an AI Studio API key, Sustenance can talk to gemini-3.1-flash-lite to log data, too.

---

## Gallery

|             Today Screen             |          Insights View          |         Food Details          |
|:-----------------------------------:|:-------------------------------:|:-----------------------------:|
|  ![Home](img/sust-today.jpg)  | ![Search](img/sust-summary.jpg) | ![List](img/sust-food.jpg) |
|           *Today Screen*            |         *Insights View*         |        *Food Details*         |

---


## Features
- **Home**: view your daily progress with several helpful chips.
- **Summary**, daily averages vs. goals you set, with progress rings and
  comparison to yesterday.

## Food logging

By adding a [Google AI Studio-formatted API key](https://aistudio.google.com/app/api-keys), Sustenance is
able to analyze photos of food items and write
relevant nutritional information to Health Connect.

|           Scan Item            |            Log Item             |
|:------------------------------:|:-------------------------------:|
| ![Home](img/sust-itemscan.jpg) | ![Search](img/sust-itemlog.jpg) |
|          *Scan Item*           |           *Log Item*            |

It talks to [gemini-3.1-flash-lite](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/gemini/3-1-flash-lite) for speed and efficiency.

## Tech

- Kotlin, Jetpack Compose, Material 3 (dynamic color)
- `androidx.health.connect:connect-client` for all nutrition and energy reads
- `androidx.glance` widgets, WorkManager for periodic refresh
- DataStore for goals
- `minSdk 30`, `targetSdk 36`

## Privacy

Sustenance requests only Health Connect **read/write** permissions for Nutrition and Energy.
- By default, AI logging features are not used and must be explicitly enabled.

## Credits
Special thanks to **GuyOnWifi** for the [heartwood](https://github.com/GuyOnWifi/heartwood) project, from which Sustenance was forked.

## License

[GPL-3.0-or-later](LICENSE) © Eason Huang & draumaz
