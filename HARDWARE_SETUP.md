# Infinity — Hardware Setup Guide
## EMG + Arduino Uno + HC-05 + Buzzer + LED

---

## 1. Components Required

| Component | Quantity | Notes |
|---|---|---|
| Arduino Uno | 1 | Any revision |
| EMG Sensor Module | 1 | MyoWare / generic 3-pin module |
| HC-05 Bluetooth Module | 1 | NOT HC-06 — HC-05 supports full SPP |
| LED (any color) | 1 | 5mm standard |
| Active Buzzer | 1 | Active = buzzes with just HIGH signal |
| Resistor 220Ω | 1 | For LED current limiting |
| Resistor 1kΩ | 1 | For HC-05 RX voltage divider |
| Resistor 2kΩ | 1 | For HC-05 RX voltage divider |
| 9V Battery | 1 | Alkaline recommended |
| 9V Battery Snap Connector | 1 | Connects battery to Arduino VIN |
| Jumper Wires | ~15 | Male-to-male |
| Breadboard | 1 | Half-size or full |
| Electrode Pads | 3 | Disposable ECG/EMG gel electrodes |
| 3.5mm Electrode Cable | 1 | Comes with most EMG modules |

---

## 2. Pin Reference

```
Arduino Uno Pin    →    Connected To
─────────────────────────────────────────
A0                 →    EMG Sensor OUT
5V                 →    EMG Sensor VCC
GND                →    EMG Sensor GND

Pin 10 (RX)        →    HC-05 TXD
Pin 11 (TX)        →    HC-05 RXD  (via 1kΩ/2kΩ voltage divider)
5V                 →    HC-05 VCC
GND                →    HC-05 GND

Pin 7              →    LED Anode (+)  via 220Ω resistor
GND                →    LED Cathode (-)

Pin 8              →    Buzzer (+)
GND                →    Buzzer (-)

VIN                →    9V Battery (+)  via snap connector
GND                →    9V Battery (-)  via snap connector
─────────────────────────────────────────
```

---

## 3. Full Wiring Diagram (Text)

```
                        ARDUINO UNO
                   ┌─────────────────────┐
    9V Battery (+) │ VIN                 │
    9V Battery (-) │ GND ────────────────┼──── Common GND rail (breadboard)
                   │                     │
  EMG Sensor OUT ──│ A0                  │
                   │ 5V  ────────────────┼──── EMG VCC
                   │                     │
                   │ Pin 7 ──[220Ω]──────┼──── LED (+)
                   │                     │     LED (-) → GND
                   │                     │
                   │ Pin 8 ──────────────┼──── Buzzer (+)
                   │                     │     Buzzer (-) → GND
                   │                     │
                   │ Pin 10 (RX) ────────┼──── HC-05 TXD
                   │ Pin 11 (TX) ──[1kΩ]─┼──── HC-05 RXD
                   │                     │         │
                   │                     │        [2kΩ]
                   │                     │         │
                   │                     │        GND
                   │ 5V  ────────────────┼──── HC-05 VCC
                   └─────────────────────┘
```

### Why the voltage divider on HC-05 RXD?
Arduino TX outputs 5V logic. HC-05 RXD expects max 3.3V.
The 1kΩ + 2kΩ divider brings 5V → 3.3V.
**Skipping this will permanently damage the HC-05.**

---

## 4. EMG Electrode Placement

```
Forearm (Flexor Digitorum — recommended for beginners)

        ELBOW                              WRIST
          │                                  │
    ──────┼──────────────────────────────────┼──────
          │                                  │
         [REF]        [SIG 1]    [SIG 2]
       (bony area)   (muscle)   (muscle)
       elbow bump    3cm apart along forearm
```

- **SIG 1 & SIG 2** — place along the muscle belly, ~3cm apart
- **REF (reference)** — place on the bony part of the elbow
- Clean skin with alcohol wipe and let dry before sticking electrodes
- Press firmly for 10 seconds to ensure good contact
- Connect electrode cable from EMG module to the pads

---

## 5. Arduino Sketch

Copy this exactly into Arduino IDE and upload to your Uno.

```cpp
#include <SoftwareSerial.h>

// ── Pin definitions ────────────────────────────────────────────────────────────
#define EMG_PIN       A0   // EMG sensor analog output
#define LED_PIN        7   // Alert LED
#define BUZZER_PIN     8   // Active buzzer
#define BT_RX         10   // SoftwareSerial RX ← HC-05 TX
#define BT_TX         11   // SoftwareSerial TX → HC-05 RX (via voltage divider)

// ── Tunable parameters ─────────────────────────────────────────────────────────
#define SEND_INTERVAL_MS   500   // Send packet every 500ms
#define EMG_THRESHOLD      600   // ADC value (0–1023) above = alert
                                 // Calibrate: watch Serial Monitor at rest vs flex
#define SAMPLES_PER_READ     5   // Average N samples to reduce noise

// ── Globals ────────────────────────────────────────────────────────────────────
SoftwareSerial bt(BT_RX, BT_TX);
unsigned long lastSendTime = 0;

// ── Setup ──────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(9600);        // USB serial for debugging
  bt.begin(9600);            // HC-05 default baud rate

  pinMode(LED_PIN,    OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(LED_PIN,    LOW);
  digitalWrite(BUZZER_PIN, LOW);

  Serial.println("Infinity EMG Node — Ready");
}

// ── Main loop ──────────────────────────────────────────────────────────────────
void loop() {
  // Read EMG — average multiple samples to reduce noise
  int emgSum = 0;
  for (int i = 0; i < SAMPLES_PER_READ; i++) {
    emgSum += analogRead(EMG_PIN);
    delay(2);
  }
  int emg = emgSum / SAMPLES_PER_READ;

  // Determine alert state
  bool alert = (emg > EMG_THRESHOLD);

  // Drive LED and buzzer
  digitalWrite(LED_PIN,    alert ? HIGH : LOW);
  digitalWrite(BUZZER_PIN, alert ? HIGH : LOW);

  // Send packet at fixed interval
  unsigned long now = millis();
  if (now - lastSendTime >= SEND_INTERVAL_MS) {
    lastSendTime = now;
    sendPacket(emg, alert);
  }
}

// ── Packet sender ──────────────────────────────────────────────────────────────
// Format: EMG:312,FALL:0\n
// This is exactly what VitalsPacketParser.kt expects.
void sendPacket(int emg, bool fall) {
  // Send over Bluetooth
  bt.print("EMG:");
  bt.print(emg);
  bt.print(",FALL:");
  bt.print(fall ? 1 : 0);
  bt.println();   // \n — required line terminator for the Android parser

  // Mirror to USB serial for debugging
  Serial.print("EMG:");
  Serial.print(emg);
  Serial.print(",FALL:");
  Serial.println(fall ? 1 : 0);
}
```

---

## 6. HC-05 Pairing Steps

### One-time setup (do this before first use)

1. Power on the Arduino — HC-05 LED blinks **fast** (not paired)
2. On your Android phone:
   - Go to **Settings → Bluetooth**
   - Turn Bluetooth ON
   - Tap **Scan / Find new devices**
   - Select **HC-05** from the list
   - Enter PIN: **1234** (or **0000** if 1234 fails)
   - Tap **Pair**
3. HC-05 LED now blinks **slow** when connected, fast when waiting

### Every session

1. Open the **Infinity app**
2. Tap **Health** tab → **Device** (Bluetooth icon top-right)
3. Your paired HC-05 appears in the list
4. Tap **Connect**
5. HC-05 LED blinks slow = connected
6. Go back to **Health Monitor** — EMG data starts flowing

---

## 7. Calibration

Open Arduino IDE → **Tools → Serial Monitor** (baud 9600) while wearing electrodes.

| State | Expected EMG ADC Value |
|---|---|
| Arm fully relaxed | 50 – 200 |
| Light finger movement | 200 – 400 |
| Moderate grip | 400 – 650 |
| Strong grip / flex | 650 – 900 |
| Maximum contraction | 900 – 1023 |

Adjust `EMG_THRESHOLD` in the sketch to match your sensor and muscle.
After changing, re-upload the sketch.

App thresholds (in `AnomalyThresholds.kt`) map to:

| App Threshold | Default Value | Meaning |
|---|---|---|
| `EMG_ACTIVE_THRESHOLD` | 400 | Contraction detected |
| `EMG_HIGH_WARNING` | 700 | Muscle fatigue warning |
| `EMG_HIGH_CRITICAL` | 900 | Spasm / critical alert |

---

## 8. Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| HC-05 not found in Bluetooth scan | Not powered or too far | Check 5V/GND wiring, stay within 10m |
| App connects but no data | Wrong baud rate | Confirm HC-05 is set to 9600 baud |
| EMG always reads 0 | Bad electrode contact or wrong pin | Check A0 wiring, re-seat electrodes |
| EMG always reads 1023 | Sensor saturated / no reference electrode | Attach REF electrode to elbow |
| Buzzer won't stop | Threshold too low | Raise `EMG_THRESHOLD` in sketch |
| App shows "Disconnected — retrying" | Socket dropped | Normal — app auto-reconnects in 5s |
| No data after reconnect | SoftwareSerial buffer overflow | Reset Arduino, reconnect from app |

---

## 9. Data Flow Summary

```
EMG Sensor
    │  analog voltage (0–5V)
    ▼
Arduino Uno (A0)
    │  analogRead() → 0–1023
    │  average 5 samples
    │  compare to threshold
    │  drive LED + Buzzer
    ▼
HC-05 (Serial @ 9600 baud)
    │  "EMG:312,FALL:0\n"
    ▼  (Bluetooth Classic SPP)
Android — BluetoothManager.kt
    │  readLine()
    ▼
VitalsPacketParser.kt
    │  parse → VitalsReading(emgRaw=312, fallDetected=false)
    ▼
HealthRepository.kt
    │  insert to Room DB
    │  feed AnomalyDetectionEngine
    ▼
AnomalyDetectionEngine.kt
    │  checkEmg() — 3-reading window
    │  if anomaly → AnomalyEvent
    ▼
AIRepository.kt (Qwen)
    │  generateHealthExplanation()
    ▼
HealthMonitorScreen.kt
    │  EMG card + progress bar
    │  EMG waveform graph
    │  AI explanation card
    ▼
User sees live EMG + alerts
```

---

**Version:** 1.0  
**Hardware:** Arduino Uno + EMG + HC-05 + LED + Buzzer + 9V  
**Packet format:** `EMG:<0-1023>,FALL:<0|1>\n`
