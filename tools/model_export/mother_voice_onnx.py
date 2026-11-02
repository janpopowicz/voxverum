"""Mother Voice v1 w wersji, którą da się wyeksportować do ONNX.

Eksporter ONNX nie obsługuje torch.stft z liczbami zespolonymi, więc widmo mocy liczymy splotem
z jądrami cos/sin. Dla center=True, pad_mode='reflect' daje to te same wartości co oryginał.
Wagi i klucze state_dict są identyczne jak w scripts/mother_voice.py, więc checkpoint
models/mother-voice-v1/best.pt ładuje się bez zmian. Zgodność z oryginałem sprawdza
test_mother_voice_onnx.py.

Wymaga, żeby katalog scripts/ był w sys.path (export_onnx.py: --code-dir scripts).
"""

from __future__ import annotations

import math

import torch
from torch.nn import functional as F

from mother_voice import MotherVoice

N_FFT = 512
HOP = 160


class MotherVoiceOnnx(MotherVoice):
    def power_spectrum(self, waveform: torch.Tensor) -> torch.Tensor:
        """|STFT|^2 jak torch.stft(n_fft=512, hop_length=160, window=hann, center=True): [B, 257, ramki]."""
        n = torch.arange(N_FFT, dtype=torch.float64)
        k = torch.arange(N_FFT // 2 + 1, dtype=torch.float64)
        angle = 2 * math.pi * k[:, None] * n[None, :] / N_FFT
        window = self.window.double()
        kernels = torch.cat((torch.cos(angle) * window, -torch.sin(angle) * window)).float().unsqueeze(1)
        padded = F.pad(waveform.float().unsqueeze(1), (N_FFT // 2, N_FFT // 2), mode="reflect")
        parts = F.conv1d(padded, kernels, stride=HOP)
        real, imag = parts[:, : N_FFT // 2 + 1], parts[:, N_FFT // 2 + 1 :]
        return real.square() + imag.square()

    def forward(self, waveform: torch.Tensor) -> torch.Tensor:
        features = self.power_spectrum(waveform).clamp_min(1e-10).log()
        mean = features.mean(dim=(1, 2), keepdim=True)
        std = features.std(dim=(1, 2), keepdim=True).clamp_min(1e-5)
        features = ((features - mean) / std).unsqueeze(1)
        x = self.encoder(features)
        x = _pool_frequency(x, 4)
        pooled = torch.cat((x.mean(dim=-1), x.std(dim=-1, unbiased=False)), dim=1)
        return self.classifier(pooled.flatten(1)).squeeze(1)


def _pool_frequency(x: torch.Tensor, bins: int) -> torch.Tensor:
    """adaptive_avg_pool2d(x, (bins, czas)) bez operatora, którego ONNX nie ma dla nierównych przedziałów."""
    size = x.shape[2]
    parts = []
    for i in range(bins):
        # Przedziały jak w PyTorch: [floor(i*n/b), ceil((i+1)*n/b)). Tylko dodatnie liczby, bo
        # dzielenie całkowite w ONNX obcina do zera i trik -(-a // b) dawałby inny wynik.
        start, end = (i * size) // bins, ((i + 1) * size + bins - 1) // bins
        parts.append(x[:, :, start:end].mean(dim=2, keepdim=True))
    return torch.cat(parts, dim=2)
