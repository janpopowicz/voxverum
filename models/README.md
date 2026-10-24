# Modele VoxVerum: kontrakt dla zespołu ML

Aplikacja Android uruchamia **dwa modele na telefonie** przez ONNX Runtime:

| Zadanie (`task`) | Co zwraca |
|---|---|
| `voice_clone` | prawdopodobieństwo, że głos w oknie jest syntetyczny / sklonowany |
| `scam` | prawdopodobieństwo, że rozmowa to próba wyłudzenia |

Modele trenujecie w PyTorch, a do aplikacji trafiają jako `.onnx` + `.json`
w `android/app/src/main/assets/models/`. Teraz leżą tam **atrapy** (`version: mock-0.1`),
które reagują tylko na głośność.

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
Skrypt eksportuje model (opset 17), porównuje wyniki PyTorch i ONNX Runtime (cisza, szum, podane WAV),
zapisuje `.onnx` + `.json` do assets i kończy się błędem, jeśli wyniki się rozjeżdżają.
Potem przebudowujecie aplikację i sprawdzacie model w **trybie symulacji** (ekran Debug → wybierz WAV).

Atrapy można odtworzyć przez `tools/model_export/make_mock_models.py`.

## Ograniczenia na telefonie
- Rozmiar: celujcie w < 50 MB na model (APK + RAM). Większe modele można kwantyzować (`onnxruntime.quantization`).
- Czas: inferencja obu modeli na 4 s audio powinna trwać < 300 ms na średnim telefonie. Ekran Debug pokazuje czas.
- Operacje: opset 17. Unikajcie własnych operatorów CUDA i nietypowych opów spoza standardu ONNX.

## Otwarte pytanie
Jeśli model `scam` będzie przyjmował **tekst** (transkrypcję), a nie audio, dokładamy w aplikacji
etap ASR (Whisper przez sherpa-onnx, polski) i interfejs `TextClassifier`. Dajcie znać, zanim go wytrenujecie.
