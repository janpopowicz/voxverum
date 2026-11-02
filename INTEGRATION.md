# Model dla aplikacji VoxVerum — przekazanie dla Ignacego Ozgi

Repozytorium zawiera wytrenowany **Mother Voice v1**, wagi, kod predykcji
i raporty ewaluacji. Do uruchomienia na CPU nie potrzeba lokalnych dysków
autora, datasetu ani GPU. Aplikacja powinna traktować model jako detektor
syntetycznego głosu; model nie analizuje treści rozmowy.

## Start po sklonowaniu

Python 3.11–3.13, polecenia z katalogu repozytorium:

```bash
git clone https://github.com/janpopowicz/voxverum.git
cd voxverum
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows PowerShell zamiast powyższego: .venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python scripts/predict_mother.py /sciezka/do/nagrania.wav
python scripts/test_mother.py
```

Domyślne urządzenie to CPU. `--device cuda` wymaga odpowiedniego PyTorch
i GPU. Zależności treningu i generatorów są odrębne; `requirements.txt`
obejmuje uruchomienie detektora i jego kontrole numeryczne.

## Pliki do integracji

| Plik | Rola |
| --- | --- |
| `models/mother-voice-v1/best.pt` | Wagi, konfiguracja, etykiety i progi z dev, epoka 20 |
| `models/mother-voice-v1/SHA256SUMS` | Suma kontrolna checkpointu |
| `scripts/mother_voice.py` | Architektura `MotherVoice` w PyTorch |
| `scripts/predict_mother.py` | Przetwarzanie audio i wynik JSON |
| `models/mother-voice-v1/dev-report.json` | Wyniki i progi dobrane na dev |
| `models/mother-voice-v1/test-report.json` | Wyniki końcowego testu |

Wagi są bezpośrednio w Git (około 1,8 MB), bez Git LFS i pobierania
zewnętrznych modeli. Plików w `data-red`, `data-blue` i `runs` nie potrzeba
do predykcji. Odtworzenie treningu wymaga osobnego dostępu do datasetu
i środowiska treningowego; ten pakiet służy integracji gotowego detektora.

## Kontrakt wejścia i wyjścia

Skrypt przyjmuje WAV/FLAC i inne formaty obsługiwane przez SoundFile.
Stereo zamienia na mono i resampluje do 16 kHz. Plik musi mieć co najmniej
3 sekundy. Nagrania z telefonu w formatach, których SoundFile nie obsługuje,
aplikacja powinna najpierw zdekodować do PCM WAV.

Dla dokładnie 3 s otrzymasz jedno okno i `decision` równe `bonafide` albo
`spoof`. Dla dłuższych plików skrypt liczy okna 3 s co 1,5 s oraz ostatnie
okno przy końcu pliku; `decision` wynosi wtedy `null`. Pola JSON:

- `windows`: początek, `score` i status każdego okna.
- `mean_window_score`: średnia ocen okien, a przy braku sygnału `null`.
- `threshold_from_dev`: domyślny próg 0.572267472743988.
- `score_direction`: `higher=spoof`; większy wynik oznacza silniejszą
  przesłankę głosu syntetycznego.
- `score_is_calibrated_probability`: `false`; wynik nie jest procentowym
  prawdopodobieństwem oszustwa.

Okna o RMS poniżej 1e-4 mają `status: insufficient_signal` i `score: null`.
Nie traktuj ciszy jako głosu naturalnego. Błędy wejścia kończą CLI kodem
niezerowym; aplikacja powinna zamienić je na czytelny komunikat.

Najprostsza integracja backendu może uruchamiać CLI przez `subprocess.run`
z listą argumentów, bez `shell=True`, i odczytywać JSON ze stdout. Przy
większym ruchu załaduj `MotherVoice` i checkpoint raz przy starcie procesu,
wywołuj `eval()` oraz `torch.inference_mode()` i zachowaj przetwarzanie
audio ze skryptu. Wejście samej sieci to tensor float32 `[batch, 48000]`
mono 16 kHz, a wyjście to logit; `torch.sigmoid` daje score.

## Wyniki i ograniczenia

Trening zakończono 29.10.2026 po 20 epokach. Na polskim teście 7560
plików (3780 w każdej klasie) dokładność przy progu z dev wynosi 99,46%,
ROC-AUC 0,99982, EER 0,582%. Przy tym progu były 24 fałszywe alarmy
i 17 przeoczonych syntez. Warianty kodeków tej samej wypowiedzi są
skorelowane; nie są niezależnymi rozmowami.

Test obejmuje generatory obecne w treningu. Ocena na niewidzianych
generatorach, rzeczywistych rozmowach i nowych urządzeniach oraz kalibracja
agregacji długich rozmów pozostają do wykonania. Wynik nie uprawnia do
pokazywania użytkownikowi „99% pewności oszustwa”. Szczegóły wyników
zawierają dołączone raporty JSON.
