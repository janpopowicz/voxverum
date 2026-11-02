"""MotherVoiceOnnx musi dawać te same wyniki co oryginalny MotherVoice z checkpointu.

Uruchomienie (z katalogu repo):
    tools/model_export/.venv/Scripts/python -m unittest tools/model_export/test_mother_voice_onnx.py
"""

import sys
import unittest
from pathlib import Path

import torch

REPO = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(REPO / "scripts"), str(Path(__file__).resolve().parent)]

from mother_voice import MotherVoice  # noqa: E402
from mother_voice_onnx import MotherVoiceOnnx  # noqa: E402

CHECKPOINT = REPO / "models" / "mother-voice-v1" / "best.pt"


class MotherVoiceOnnxParity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        state = torch.load(CHECKPOINT, map_location="cpu", weights_only=True)["model"]
        cls.original = MotherVoice().eval()
        cls.original.load_state_dict(state)
        cls.onnx_ready = MotherVoiceOnnx().eval()
        cls.onnx_ready.load_state_dict(state)

    def assert_same(self, waveform):
        with torch.inference_mode():
            expected = self.original(waveform)
            got = self.onnx_ready(waveform)
        self.assertEqual(expected.shape, got.shape)
        torch.testing.assert_close(torch.sigmoid(got), torch.sigmoid(expected), atol=1e-4, rtol=0)

    def test_noise_and_tone(self):
        gen = torch.Generator().manual_seed(0)
        t = torch.arange(48000) / 16000
        batch = torch.stack([
            torch.randn(48000, generator=gen) * 0.05,
            0.3 * torch.sin(2 * torch.pi * 220 * t) + torch.randn(48000, generator=gen) * 0.01,
            torch.randn(48000, generator=gen) * 0.5,
        ])
        self.assert_same(batch)

    def test_power_spectrum_matches_stft(self):
        x = torch.randn(2, 48000, generator=torch.Generator().manual_seed(1)) * 0.1
        expected = torch.stft(x, n_fft=512, hop_length=160, window=self.original.window,
                              return_complex=True).abs().square()
        torch.testing.assert_close(self.onnx_ready.power_spectrum(x), expected, atol=1e-3, rtol=1e-4)


if __name__ == "__main__":
    unittest.main()
