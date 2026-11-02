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

## Kontrakt modelu w aplikacji
`android/app/src/main/assets/models/<task>.onnx` + `<task>.json`, gdzie task to `voice_clone` albo `scam`.
JSON: `task, file, version, sample_rate(16000), window_samples, input_name, output_name,
output_type(logits|probability), positive_index, threshold` (opcjonalnie `mock`).
Wejście `float32 [1, N]`, preprocessing w grafie. Jedna wartość wyjścia przy `logits` przechodzi przez sigmoid, dwie przez softmax.
Teraz w assets są atrapy z `tools/model_export/make_mock_models.py`, które reagują tylko na głośność.

## Stan na 2026-11-02
- `feat/android-app` (HEAD `e97afef`) jest wypchnięty. 35 testów przechodzi, lint bez błędów.
  Na emulatorze z Androidem 16 przeszedł test E2E z atrapą komunikatora: oba nagrania działają, baner się pokazuje, mikrofon jest zwalniany.
- **PR jeszcze nie istnieje.** Strona compare z wypełnionym tytułem i opisem była otwarta w przeglądarce
  (opis: `C:\Users\Ignacy\AppData\Local\Temp\vox_pr_body.md`). PR ma być ready for review, nie draft.
- **Jan wypchnął do `main` model `b9aa4c9`** („Mother Voice v1”). Szczegóły w `INTEGRATION.md` na main.
  Pliki: `models/mother-voice-v1/best.pt`, `scripts/mother_voice.py`, `scripts/predict_mother.py`,
  `scripts/test_mother.py`, `requirements.txt`, raporty dev/test, a także root `.gitignore` i zmieniony `README.md`.
- Niesprawdzone: prawdziwa rozmowa WhatsApp na prawdziwym telefonie. Do sprawdzenia też, czy odtwarzanie
  wiadomości głosowej w WhatsApp nie włącza `MODE_IN_COMMUNICATION`.

## Następny krok: wpięcie Mother Voice v1 jako `voice_clone`
Fakty o modelu (z kodu Jana):
- `MotherVoice()` bez argumentów przyjmuje wejście `[batch, 48000]` (3 s, 16 kHz). W środku: STFT
  (n_fft 512, hop 160, hann) → log-power → normalizacja per klip → CNN.
  Wyjście to **jeden logit o kształcie `[batch]`** (po `squeeze(1)`), więc 1-D, a nie `[1, 1]`.
- Checkpoint to `torch.load(..., weights_only=True)` → dict z kluczami `model` (state_dict), `config`
  (`crop_seconds`=3, `name`) i `thresholds` (`eer`≈0.5723, `low_fpr`).
- Wynik nie jest skalibrowanym prawdopodobieństwem. Jan wprost zastrzega, że nie wolno pokazywać „99% pewności”.
  Okna z RMS < 1e-4 to brak sygnału, a nie „prawdziwy głos”.

Plan:
1. Zmergować `origin/main` do `feat/android-app`. Konflikt będzie w `README.md` (połączyć: nasza treść
   plus sekcja o modelu i link do `INTEGRATION.md`). Root `.gitignore` od Jana nie koliduje z naszymi.
2. `export_onnx.py`: przy `--state-dict` czytać klucz `model` z checkpointu (dziś szuka tylko `state_dict`),
   a próg brać z `thresholds.eer`, jeśli nie podano `--threshold`. Uruchomić z
   `--task voice_clone --code-dir scripts --model-class mother_voice:MotherVoice
   --state-dict models/mother-voice-v1/best.pt --window-seconds 3 --output-type logits --version mother-voice-v1`.
3. Ryzyko: `torch.stft(return_complex=True)` + `.abs()` może nie przejść klasycznego eksportera ONNX
   (liczby zespolone). Wtedy spróbować `dynamo=True` albo opakować model i policzyć STFT jako `conv1d`
   ze stałymi jądrami sin/cos (to samo co `torch.stft` z `center=True`, padding reflect). Zgodność z
   PyTorch musi przejść test w skrypcie (`--atol`). Sprawdzić też, czy ORT Android 1.22 obsługuje użyte operatory.
4. W aplikacji okno modelu wynosi 3 s z bufora 4 s (`ScoreMath.tail`), więc pipeline nie wymaga zmian. Usunąć atrapę
   `voice_clone.onnx/json` i wrzucić nowy model. Sprawdzić rozmiar APK, czas inferencji na ekranie Debug
   i `ModelRegistryTest`.
5. Teksty banera/UI: nie pokazywać wyniku jako procentu pewności. Zostawić poziomy OK/WARN/DANGER.
6. `scam` zostaje atrapą, dopóki nie będzie modelu. Otwarte pytanie z planu: czy będzie na audio, czy na tekście (wtedy potrzebne ASR).
7. Po każdym kroku: testy, lint, commit, push. Potem zaktualizować opis PR albo stworzyć PR, jeśli go nie ma.
