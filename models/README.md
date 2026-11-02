# Modele VoxVerum: kontrakt dla zespołu ML

Aplikacja Android uruchamia **dwa modele na telefonie** przez ONNX Runtime:

| Zadanie (`task`) | Co zwraca |
|---|---|
| `voice_clone` | prawdopodobieństwo, że głos w oknie jest syntetyczny / sklonowany |
| `scam` | prawdopodobieństwo, że rozmowa to próba wyłudzenia |

Modele trenujecie w PyTorch, a do aplikacji trafiają jako `.onnx` + `.json`
w `android/app/src/main/assets/models/`. Stan:

| Zadanie | Model w aplikacji |
|---|---|
| `voice_clone` | **Mother Voice v1** (`models/mother-voice-v1/best.pt`), okno 3 s, wyjście: 1 logit, próg 0,572 z dev |
| `scam` | brak; aplikacja używa wtedy `MissingModelClassifier`, który nigdy nie alarmuje |

## Wejście
- `float32 [1, N]`, surowy waveform **16 kHz mono**, wartości w zakresie −1…1.
- `N` = `window_samples` z `.json`. Domyślnie 64000 (4 s). Aplikacja co 1 s podaje ostatnie N próbek.
- **Cały preprocessing (STFT/mel/LFCC, normalizacja, pre-emphasis) ma być w `forward()` modelu.**
  Używajcie operacji torcha (`torch.stft`, `torchaudio.transforms.MelSpectrogram` itp.), żeby trafiły do grafu ONNX.
- Okna z samą ciszą są odrzucane przed modelem (prosty VAD), więc nie trzeba ich obsługiwać.
- Dźwięk to nagranie z mikrofonu telefonu w trakcie rozmowy na głośniku (kodek WhatsApp, pogłos pokoju,
  także głos użytkownika). Warto augmentować dane treningowe w tę stronę.

## Wyjście
Jedno z dwóch:
- `[1, 2]` logity → `output_type: "logits"`, `positive_index` = indeks klasy pozytywnej (klon / wyłudzenie),
- `[1, 1]` prawdopodobieństwo po sigmoidzie → `output_type: "probability"`.

## Plik `.json`
```json
{
  "task": "voice_clone",
  "file": "voice_clone.onnx",
  "version": "2026.11.1",
  "sample_rate": 16000,
  "window_samples": 64000,
  "input_name": "audio",
  "output_name": "score",
  "output_type": "logits",
  "positive_index": 1,
  "threshold": 0.7
}
```
`threshold` to próg, od którego okno liczy się jako podejrzane. Aplikacja dodatkowo wygładza wyniki
(alarm dopiero, gdy kilka kolejnych okien przekracza próg).

## Jak wpiąć model
```bash
python -m venv tools/model_export/.venv
tools/model_export/.venv/Scripts/python -m pip install -r tools/model_export/requirements.txt
tools/model_export/.venv/Scripts/python tools/model_export/export_onnx.py \
    --task voice_clone --model-class detector.model:AntiSpoofNet --state-dict best.pt --code-dir ../detector \
    --output-type logits --positive-index 1 --threshold 0.7 --version 2026.11.1 \
    --wav probki/prawdziwy.wav probki/klon.wav
```
Skrypt eksportuje model (opset 17), porównuje wyniki PyTorch i ONNX Runtime (cichy szum, głośny szum, podane WAV)
i dopiero wtedy zapisuje `.onnx` + `.json` do assets. Jeśli wyniki się rozjeżdżają, kończy się błędem i niczego nie podmienia.
Przy checkpoincie z treningu (słownik z kluczem `model`) próg bierze z `thresholds.eer`, chyba że podasz `--threshold`.
Potem przebudowujecie aplikację i sprawdzacie model w **trybie symulacji** (ekran Debug → wybierz WAV).

### Mother Voice v1
```bash
tools/model_export/.venv/Scripts/python tools/model_export/export_onnx.py     --task voice_clone --code-dir scripts --model-class mother_voice_onnx:MotherVoiceOnnx     --state-dict models/mother-voice-v1/best.pt --window-seconds 3 --output-type logits --version mother-voice-v1
tools/model_export/.venv/Scripts/python -m unittest tools/model_export/test_mother_voice_onnx.py
```
`torch.stft` na liczbach zespolonych nie przechodzi eksportu do ONNX, dlatego `mother_voice_onnx.py` liczy to samo widmo
splotem. Wagi są te same, a test sprawdza zgodność z oryginałem. Po zmianie architektury w `scripts/mother_voice.py` trzeba
poprawić też `mother_voice_onnx.py`. Gdy będzie nowy checkpoint tej samej sieci, wystarczy powtórzyć powyższe polecenia.
Na urządzeniu zgodność z PyTorch sprawdza `ModelRegistryTest` (`./gradlew connectedDebugAndroidTest`).

Atrapę dla zadania bez modelu można wygenerować przez `make_mock_models.py --task scam`.
Nie generuj atrapy `voice_clone`, bo nadpiszesz Mother Voice.

## Ograniczenia na telefonie
- Rozmiar: celujcie w < 50 MB na model (APK + RAM). Większe modele można kwantyzować (`onnxruntime.quantization`).
- Czas: inferencja obu modeli na 4 s audio powinna trwać < 300 ms na średnim telefonie. Ekran Debug pokazuje czas.
- Operacje: opset 17. Unikajcie własnych operatorów CUDA i nietypowych opów spoza standardu ONNX.

## Otwarte pytanie
Jeśli model `scam` będzie przyjmował **tekst** (transkrypcję), a nie audio, dokładamy w aplikacji
etap ASR (Whisper przez sherpa-onnx, polski) i interfejs `TextClassifier`. Dajcie znać, zanim go wytrenujecie.
