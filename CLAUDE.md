# VoxVerum: notatki dla Claude

Prototyp aplikacji na Androida (APK, bez Google Play), która w trakcie rozmowy WhatsApp słucha mikrofonu
i na telefonie puszcza audio przez dwa modele: **voice_clone** (syntetyczny głos) i **scam** (wyłudzenie).
Ostrzega banerem nad rozmową, wibracją i powiadomieniem. Biznesplan: `VoxVerum_biznesplan.pdf`.
Instrukcja dla ludzi: `README.md`. Kontrakt modeli: `models/README.md`.

## Zespół i repo
- Remote: `github.com/janpopowicz/voxverum`. Użytkownik to Ignacy Ozga (GitHub `IgnacyO1`, collaborator).
  Jan Popowicz (właściciel repo) trenuje modele.
- Branch z aplikacją: `feat/android-app`. Base: `main`.
- Rozmawiamy po polsku. Commity też po polsku, krótki tytuł mówiący, co się zmieniło dla użytkownika.
- Po każdym zakończonym kroku: commit i `git push`. Stopka commita: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Nie czytaj ani nie wypisuj poświadczeń Git/GCM. Gdy push zwraca 403, problemem są uprawnienia w repo, nie kod.
- `gh` nie jest zainstalowane. Stan PR sprawdzaj przez
  `curl -sg "https://api.github.com/repos/janpopowicz/voxverum/pulls?state=all"` (`-g`, bo `&` i `?` w URL).

## Budowanie i testy (Windows, Git Bash)
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export MSYS_NO_PATHCONV=1          # inaczej Git Bash przerabia /sdcard na C:/...
cd android
./gradlew testDebugUnitTest         # testy JVM
./gradlew lintDebug                 # lint, ma być 0 błędów
./gradlew assembleDebug             # APK: app/build/outputs/apk/debug/
./gradlew connectedDebugAndroidTest # ModelRegistryTest, wymaga urządzenia/emulatora
```
- `adb`: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. Logi: `adb logcat -s VoxVerum`.
- `android/local.properties` (git-ignored): `sdk.dir=C\:/Users/Ignacy/AppData/Local/Android/Sdk`.
- Nie dodawaj `jvmToolchain` (brak JDK 17 do auto-provisioningu), zostaje `compilerOptions.jvmTarget = 17`.
- Ścieżka scratchpada przekracza 260 znaków. Do emulatora/AVD i plików tymczasowych używaj krótkich katalogów.
- Python do eksportu: `tools/model_export/.venv/Scripts/python` (torch 2.14 CPU, onnx, onnxruntime).

## Architektura (`android/app/src/main/java/pl/voxverum/app/`)
Kotlin, Compose, minSdk 29, target/compileSdk 36, AGP 8.13, Kotlin 2.2.20, ONNX Runtime Android 1.22.

| Pakiet | Co robi |
|---|---|
| `call/` | `VoxAccessibilityService` łapie zdarzenia z `com.whatsapp` i `com.whatsapp.w4b`. `CallDetector` rozpoznaje rozmowę po `MODE_IN_COMMUNICATION` i kończy ją dopiero po 2 s ciszy w trybie audio. |
| `overlay/` | Baner `TYPE_ACCESSIBILITY_OVERLAY`. Jest też warunkiem, żeby system dał nam mikrofon równolegle z rozmową. |
| `audio/` | `MicAudioSource` (16 kHz mono float, `VOICE_RECOGNITION` z fallbackiem `MIC`), `AudioWindower`, `Vad`, `WavIo`. |
| `session/` | `ProtectionSession`: okna 4 s co 1 s → `conflate()` → VAD → modele → `RiskEngine`. `CaptureForegroundService` (typ microphone). `DebugDumper` tylko w debug. |
| `ml/` | `ModelSpec` (JSON), `OnnxClassifier`, `ModelRegistry` (sprawdza model próbnym uruchomieniem, a przy błędzie daje `MissingModelClassifier`), `ScoreMath`. Model dostaje ostatnie `window_samples` z okna 4 s (`ScoreMath.tail`). |
| `risk/` | `RiskEngine`: EMA, DANGER przy 2 z 3 okien ≥ progu (zatrzaskuje się do końca rozmowy), WARN z histerezą. |
| `alert/` | `AlertTexts` (teksty banera), `Alerter` (wibracja i powiadomienie tylko przy eskalacji). |
| `history/` | Tylko metadane rozmów w `filesDir/call_history.json`, maks. 50. Nigdy audio. |
| `sim/` | `WavAudioSource`: symulacja z pliku WAV tym samym pipeline'em. |
| `ui/` | Zgoda, Ochrona, Historia, Debug. `MainActivity` obsługuje uprawnienia. |

Prywatność: audio jest tylko w RAM. `allowBackup=false`, a `data_extraction_rules.xml` wyklucza wszystko.

## Modele w aplikacji
`android/app/src/main/assets/models/<task>.onnx` + `<task>.json`, gdzie task to `voice_clone` albo `scam`.
JSON: `task, file, version, sample_rate(16000), window_samples, input_name, output_name,
output_type(logits|probability), positive_index, threshold`.
Wejście to `float32 [1, N]`, a preprocessing jest w grafie modelu. Model bierze ostatnie `window_samples` z okna 4 s (`ScoreMath.tail`).
Gdy wyjście ma jedną wartość, przy `logits` aplikacja liczy z niej sigmoid. Gdy ma dwie, liczy softmax.

- **voice_clone = Mother Voice v1** od Jana (`models/mother-voice-v1/best.pt`, kod w `scripts/mother_voice.py`,
  opis w `INTEGRATION.md`). Okno 48000 próbek (3 s), wyjście to jeden logit `[1]`, próg 0,572 (`thresholds.eer` z dev).
  Wynik **nie jest skalibrowanym prawdopodobieństwem**, więc baner nie pokazuje procentów.
- **scam: brak modelu.** Atrapę usunęliśmy, bo reagowała na głośność i obok prawdziwego detektora dawała fałszywe alarmy.
  Brakujący model to `MissingModelClassifier`: zwraca zawsze 0, `isMock=false`, `version="missing"`.
  Ekran Ochrona pokazuje „Wykrywanie prób wyłudzenia jeszcze nie działa”.
- Eksport: `torch.stft` na liczbach zespolonych nie przechodzi eksportu ONNX, dlatego `tools/model_export/mother_voice_onnx.py`
  (`MotherVoiceOnnx`, podklasa z tymi samymi wagami) liczy widmo splotem cos/sin, a pooling częstotliwości robi ręcznie.
  Pułapka: dzielenie całkowite w ONNX obcina do zera, więc w śledzonym kodzie nie używaj `-(-a // b)`.
  Polecenie eksportu i testy są w `models/README.md`.
- Na samych zerach Mother Voice daje szum zaokrągleń (normalizacja per klip). Dlatego próbne uruchomienie w `ModelRegistry`
  i test zgodności w `export_onnx.py` używają cichego szumu. Do modelu cisza i tak nie trafia, bo odcina ją VAD.

## Stan na 2026-11-02
- Gałąź `feat/android-app` ma zmergowany `main` (model Jana) i wpięty Mother Voice. Testy:
  - 35 testów JVM przechodzi, lint pokazuje 0 błędów;
  - `test_mother_voice_onnx.py` (PyTorch kontra wersja eksportowalna) przechodzi;
  - `ModelRegistryTest` na emulatorze z Androidem 16 daje te same wyniki co PyTorch (0,8213 i 0,0517, tolerancja 1e-3);
  - symulacja WAV w aplikacji: model się ładuje, ryzyko eskaluje.
- Czas inferencji to 14 ms na okno na PC. Na emulatorze wychodziło 450 ms przy lekkim obciążeniu i 1,9 s przy swapowaniu (AVD ma 2 GB RAM).
  **Do zmierzenia na prawdziwym telefonie**: ekran Debug pokazuje `czas`. Budżet to krok okna, czyli 1 s.
- **PR jeszcze nie istnieje.** `gh` nie jest zainstalowane, więc PR tworzy się przez stronę compare na GitHubie.
  Opis PR trzeba uzupełnić o Mother Voice.
- Niesprawdzone:
  - prawdziwa rozmowa WhatsApp na telefonie (test wykonalności z README);
  - czy notatka głosowa WhatsApp włącza `MODE_IN_COMMUNICATION`;
  - jak model radzi sobie z dźwiękiem z mikrofonu przy rozmowie na głośniku. Jan trenował na kodekach,
    ale nie na pogłosie pokoju, i sam pisze, że ocena na prawdziwych rozmowach jest jeszcze do zrobienia.

## Co dalej
1. Test na prawdziwym telefonie: rozmowa WhatsApp na głośniku, a potem nagranie syntetyczne puszczone z drugiego telefonu.
   Patrzymy na wyniki na ekranie Debug i na liczbę fałszywych alarmów przy prawdziwym głosie.
2. Model `scam`: ustalić z Janem, czy będzie działał na audio, czy na tekście (wtedy potrzebne ASR: Whisper przez sherpa-onnx).
3. Po nowym checkpoincie powtórzyć eksport (`models/README.md`), `connectedDebugAndroidTest`, commit i push.
