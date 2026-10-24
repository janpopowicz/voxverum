"""Eksportuje model PyTorch do ONNX dla aplikacji VoxVerum i sprawdza, czy wyniki się zgadzają.

Kontrakt modelu opisuje models/README.md. W skrócie: wejście float32 [1, N] (16 kHz mono, -1..1),
preprocessing (mel/STFT/normalizacja) wewnątrz modelu, wyjście [1, 2] logity albo [1, 1] prawdopodobieństwo.

Jak wczytać model (jedna z opcji):
  --torchscript model.pt               model zapisany przez torch.jit.save
  --pickle model.pt                    cały model zapisany przez torch.save(model)
  --model-class pakiet.modul:Klasa --state-dict wagi.pt [--model-kwargs '{"n_mels": 80}']

Przykład:
  tools/model_export/.venv/Scripts/python tools/model_export/export_onnx.py \
      --task voice_clone --model-class detector.model:AntiSpoofNet --state-dict best.pt \
      --output-type logits --positive-index 1 --threshold 0.7 --version 2026.11.1 \
      --wav probki/prawdziwy.wav probki/klon.wav

Bez --out-dir pliki trafiają od razu do android/app/src/main/assets/models/.
Do sprawdzenia samego skryptu bez prawdziwego modelu: --self-test.
"""

from __future__ import annotations

import argparse
import importlib
import json
import sys
import wave
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch

REPO = Path(__file__).resolve().parents[2]
DEFAULT_OUT = REPO / "android" / "app" / "src" / "main" / "assets" / "models"
SAMPLE_RATE = 16_000
OPSET = 17


def load_model(args: argparse.Namespace) -> torch.nn.Module:
    if args.self_test:
        return _SelfTestNet()
    if args.torchscript:
        return torch.jit.load(args.torchscript, map_location="cpu")
    if args.pickle:
        return torch.load(args.pickle, map_location="cpu", weights_only=False)
    if args.model_class:
        if args.code_dir:
            sys.path.insert(0, str(args.code_dir))
        module_name, class_name = args.model_class.split(":")
        cls = getattr(importlib.import_module(module_name), class_name)
        model = cls(**json.loads(args.model_kwargs))
        if args.state_dict:
            state = torch.load(args.state_dict, map_location="cpu", weights_only=True)
            state = state.get("state_dict", state) if isinstance(state, dict) else state
            model.load_state_dict(state)
        return model
    raise SystemExit("Podaj --torchscript, --pickle, --model-class albo --self-test")


class _SelfTestNet(torch.nn.Module):
    """Mała sieć na surowym audio, tylko do sprawdzenia eksportu."""

    def __init__(self) -> None:
        super().__init__()
        self.conv = torch.nn.Sequential(
            torch.nn.Conv1d(1, 8, kernel_size=400, stride=160),
            torch.nn.ReLU(),
            torch.nn.Conv1d(8, 16, kernel_size=3, stride=2),
            torch.nn.ReLU(),
            torch.nn.AdaptiveAvgPool1d(1),
        )
        self.head = torch.nn.Linear(16, 2)

    def forward(self, audio: torch.Tensor) -> torch.Tensor:
        return self.head(self.conv(audio.unsqueeze(1)).squeeze(-1))


def read_wav_16k(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as w:
        if w.getsampwidth() != 2:
            raise SystemExit(f"{path}: obsługiwany jest tylko PCM 16-bit")
        rate, channels = w.getframerate(), w.getnchannels()
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32767
    if channels > 1:
        data = data.reshape(-1, channels).mean(axis=1)
    if rate != SAMPLE_RATE:
        t_out = np.arange(int(len(data) * SAMPLE_RATE / rate)) * rate / SAMPLE_RATE
        data = np.interp(t_out, np.arange(len(data)), data).astype(np.float32)
    return data


def last_window(audio: np.ndarray, n: int) -> np.ndarray:
    """Ostatnie n próbek pliku, dopełnione zerami z przodu tak samo jak w aplikacji (ScoreMath.tail)."""
    out = np.zeros(n, dtype=np.float32)
    tail = audio[-n:]
    out[n - len(tail):] = tail
    return out


def to_probability(raw: np.ndarray, output_type: str, positive_index: int) -> float:
    flat = raw.reshape(-1).astype(np.float64)
    if output_type == "probability":
        return float(flat[0] if flat.size == 1 else flat[positive_index])
    if flat.size == 1:
        return float(1 / (1 + np.exp(-flat[0])))
    e = np.exp(flat - flat.max())
    return float((e / e.sum())[positive_index])


def export(model: torch.nn.Module, out_path: Path, n: int, input_name: str, output_name: str) -> None:
    model.eval()
    dummy = torch.zeros(1, n)
    kwargs = dict(input_names=[input_name], output_names=[output_name], opset_version=OPSET)
    try:
        # Klasyczny eksporter: działa też dla modeli TorchScript.
        torch.onnx.export(model, (dummy,), str(out_path), dynamo=False, **kwargs)
    except TypeError:  # starszy torch bez argumentu dynamo
        torch.onnx.export(model, (dummy,), str(out_path), **kwargs)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--task", required=True, choices=["voice_clone", "scam"])
    src = p.add_argument_group("źródło modelu")
    src.add_argument("--torchscript", type=Path)
    src.add_argument("--pickle", type=Path)
    src.add_argument("--model-class", help="moduł:Klasa, np. detector.model:AntiSpoofNet")
    src.add_argument("--state-dict", type=Path)
    src.add_argument("--model-kwargs", default="{}", help="JSON z argumentami konstruktora")
    src.add_argument("--code-dir", type=Path, help="katalog z kodem modelu (dodawany do sys.path)")
    src.add_argument("--self-test", action="store_true", help="eksport małej sieci testowej zamiast prawdziwego modelu")
    p.add_argument("--window-seconds", type=float, default=4.0)
    p.add_argument("--output-type", choices=["logits", "probability"], default="logits")
    p.add_argument("--positive-index", type=int, default=1, help="indeks klasy 'klon'/'wyłudzenie' w wyjściu")
    p.add_argument("--threshold", type=float, default=0.7)
    p.add_argument("--version", default="dev")
    p.add_argument("--input-name", default="audio")
    p.add_argument("--output-name", default="score")
    p.add_argument("--wav", type=Path, nargs="*", default=[], help="pliki WAV do porównania PyTorch vs ONNX")
    p.add_argument("--atol", type=float, default=1e-4)
    p.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    args = p.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")  # polskie znaki w konsoli Windows

    n = int(args.window_seconds * SAMPLE_RATE)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    onnx_path = args.out_dir / f"{args.task}.onnx"

    model = load_model(args)
    model.eval()
    export(model, onnx_path, n, args.input_name, args.output_name)
    print(f"Wyeksportowano {onnx_path} ({onnx_path.stat().st_size / 1e6:.1f} MB)")

    # Porównanie PyTorch vs ONNX Runtime: szum, cisza i podane pliki WAV.
    rng = np.random.default_rng(0)
    inputs = {"cisza": np.zeros(n, np.float32), "szum": (rng.standard_normal(n) * 0.1).astype(np.float32)}
    for wav in args.wav:
        inputs[wav.name] = last_window(read_wav_16k(wav), n)

    session = ort.InferenceSession(str(onnx_path))
    worst = 0.0
    for name, x in inputs.items():
        with torch.no_grad():
            expected = model(torch.from_numpy(x[None, :])).numpy()
        got = session.run([args.output_name], {args.input_name: x[None, :]})[0]
        if expected.shape != got.shape:
            raise SystemExit(f"Różny kształt wyjścia dla {name}: torch {expected.shape}, onnx {got.shape}")
        diff = float(np.abs(expected - got).max())
        worst = max(worst, diff)
        prob = to_probability(got, args.output_type, args.positive_index)
        print(f"  {name:>24}: p={prob:.3f}  max|torch-onnx|={diff:.2e}")
    if worst > args.atol:
        raise SystemExit(f"Wyniki PyTorch i ONNX różnią się o {worst:.2e} > {args.atol:.0e}")

    spec = {
        "task": args.task,
        "file": onnx_path.name,
        "version": args.version,
        "sample_rate": SAMPLE_RATE,
        "window_samples": n,
        "input_name": args.input_name,
        "output_name": args.output_name,
        "output_type": args.output_type,
        "positive_index": args.positive_index,
        "threshold": args.threshold,
    }
    spec_path = args.out_dir / f"{args.task}.json"
    spec_path.write_text(json.dumps(spec, indent=2) + "\n")
    print(f"Zapisano {spec_path}. Zgodność OK (max różnica {worst:.2e}).")


if __name__ == "__main__":
    main()
