"""Scenariusz rozmowy: sygnał wołania -> prawdziwy głos -> klon przejmuje rozmowę.

Całość idzie przez kanał 'glosnik' (WhatsApp + głośnik + pokój + mikrofon) i pipeline aplikacji.
Wypisuje oś czasu: co sekundę stan VAD, wynik modelu i poziom ryzyka.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
import onnxruntime as ort
from scipy.signal import sosfilt

from ocena import SR, Risk, channel, load, model_lowpass, vad


def ringback(seconds: float) -> np.ndarray:
    # Europejski sygnał zwrotny wołania: 425 Hz, 1 s tonu, 4 s przerwy.
    t = np.arange(int(seconds * SR)) / SR
    gate = ((t % 5.0) < 1.0).astype(np.float32)
    return (0.2 * np.sin(2 * np.pi * 425 * t) * gate).astype(np.float32)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--model", type=Path, required=True)
    p.add_argument("--prawdziwy", type=Path, required=True)
    p.add_argument("--klon", type=Path, nargs="+", required=True)
    p.add_argument("--kanal", default="glosnik")
    args = p.parse_args()
    spec = json.loads(args.model.with_suffix(".json").read_text())
    sos = model_lowpass(spec.get("lowpass_hz"))
    so = ort.SessionOptions()
    so.intra_op_num_threads = 2
    sess = ort.InferenceSession(str(args.model), so)
    n, thr = spec["window_samples"], spec["threshold"]

    gap = np.zeros(int(0.4 * SR), np.float32)
    parts = [("wołanie", ringback(5.0)), ("prawdziwy", load(args.prawdziwy))]
    for k in args.klon:
        parts += [("cisza", gap), ("klon", load(k))]
    labels = np.concatenate([np.full(len(x), i) for i, (_, x) in enumerate(parts)])
    x = channel(np.concatenate([x for _, x in parts]), args.kanal)

    risk = Risk(thr)
    for end in range(4 * SR, len(x) + 1, SR):
        w = x[end - n:end]
        seg = parts[int(np.bincount(labels[end - n:end]).argmax())][0]
        ok, sp, tn = vad(w)
        if ok:
            if sos is not None:
                w = sosfilt(sos, w)
            logit = float(sess.run(None, {"audio": w[None, :].astype(np.float32)})[0].reshape(-1)[0])
            s = 1 / (1 + math.exp(-logit))
            level = risk.update(s)
            print(f"{end / SR:5.0f} s  {seg:9s}  wynik={s:.3f}  -> {level}")
        else:
            print(f"{end / SR:5.0f} s  {seg:9s}  pominięte (mowa={sp:.2f}, ton={tn:.2f})  -> {risk.level}")


if __name__ == "__main__":
    main()
