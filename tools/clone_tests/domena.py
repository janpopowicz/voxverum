"""Skąd fałszywe alarmy na prawdziwych głosach? Te same pliki po jednej zmianie naraz.

Dla każdego wariantu (bez zmian, górnoprzepustowy 300 Hz, dolnoprzepustowy 7 kHz, oba, głośniej, ciszej)
liczy odsetek okien z wynikiem ≥ progu. Wariant, który usuwa alarmy, wskazuje, na co model jest czuły.
Okna jak w aplikacji, z tym samym VAD.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
import onnxruntime as ort
from scipy.signal import butter, sosfilt

from ocena import SR, load, vad


def bandpass(x: np.ndarray, low: float | None, high: float | None) -> np.ndarray:
    if low:
        x = sosfilt(butter(4, low, "highpass", fs=SR, output="sos"), x)
    if high:
        x = sosfilt(butter(4, high, "lowpass", fs=SR, output="sos"), x)
    return x.astype(np.float32)


def gain_db(x: np.ndarray, db: float) -> np.ndarray:
    return np.clip(x * 10 ** (db / 20), -1, 1).astype(np.float32)


VARIANTS = {
    "bez_zmian": lambda x: x,
    "gorno_300": lambda x: bandpass(x, 300, None),
    "dolno_7k": lambda x: bandpass(x, None, 7000),
    "pasmo_300_7k": lambda x: bandpass(x, 300, 7000),
    "plus_12dB": lambda x: gain_db(x, 12),
    "minus_12dB": lambda x: gain_db(x, -12),
}


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--model", type=Path, required=True)
    p.add_argument("pliki", type=Path, nargs="+")
    args = p.parse_args()
    spec = json.loads(args.model.with_suffix(".json").read_text())
    so = ort.SessionOptions()
    so.intra_op_num_threads = 2
    sess = ort.InferenceSession(str(args.model), so)
    n, thr = spec["window_samples"], spec["threshold"]
    hits = {k: [0, 0] for k in VARIANTS}
    for path in args.pliki:
        x = load(path)
        for name, f in VARIANTS.items():
            y = f(x)
            for end in range(4 * SR, len(y) + 1, SR):
                w = y[end - n:end]
                # VAD na wersji bez zmian, żeby każdy wariant oceniał te same okna.
                if not vad(x[end - n:end])[0]:
                    continue
                logit = float(sess.run(None, {"audio": w[None, :]})[0].reshape(-1)[0])
                hits[name][0] += (1 / (1 + math.exp(-logit))) >= thr
                hits[name][1] += 1
    for name, (h, total) in hits.items():
        print(json.dumps({"wariant": name, "okna": total, "powyzej_progu": round(h / max(total, 1), 3)}))


if __name__ == "__main__":
    main()
