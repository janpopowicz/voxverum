"""Ocena nagrań tak, jak zrobiłaby to aplikacja, po przepuszczeniu przez symulowany kanał rozmowy.

Kanały:
  czysty     - nagranie jak jest (16 kHz)
  whatsapp   - kodek Opus 16 kb/s (WhatsApp)
  glosnik    - Opus + głośnik telefonu (pasmo 300-7000 Hz, lekkie przesterowanie) + pogłos pokoju
               + szum mikrofonu + AGC: tak aplikacja słyszy rozmówcę na głośniku
  przy_mik   - pogłos + szum + AGC, bez kodeka: tak aplikacja słyszy właściciela telefonu

Pipeline jak w aplikacji: okna 4 s co 1 s, VAD (głośność + odrzucanie stałych tonów) na ostatnich 3 s,
model na ostatnich 3 s, RiskEngine (EMA 0.4, DANGER przy 2 z 3 okien >= progu, zatrzask).
"""
from __future__ import annotations

import argparse
import json
import math
from fractions import Fraction
from pathlib import Path

import av
import numpy as np
import onnxruntime as ort
import soundfile as sf
from scipy.signal import butter, fftconvolve, resample_poly, sosfilt

SR = 16_000


def load(path: Path) -> np.ndarray:
    x, sr = sf.read(path, dtype="float32", always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        g = math.gcd(sr, SR)
        x = resample_poly(x, SR // g, sr // g).astype(np.float32)
    return x


# ---------- kanał ----------

def opus(x: np.ndarray, bitrate: int = 16_000) -> np.ndarray:
    enc = av.CodecContext.create("libopus", "w")
    enc.sample_rate, enc.layout, enc.format, enc.bit_rate = 48_000, "mono", "s16", bitrate
    enc.options = {"application": "voip"}
    dec = av.CodecContext.create("libopus", "r")
    dec.sample_rate, dec.layout = 48_000, "mono"
    up = resample_poly(x, 3, 1)
    pcm = (np.clip(up, -1, 1) * 32767).astype(np.int16)
    out = []
    step = 960  # 20 ms przy 48 kHz
    for i in range(0, len(pcm), step):
        chunk = pcm[i:i + step]
        if len(chunk) < step:
            chunk = np.pad(chunk, (0, step - len(chunk)))
        frame = av.AudioFrame.from_ndarray(chunk[None, :], format="s16", layout="mono")
        frame.sample_rate, frame.pts, frame.time_base = 48_000, i, Fraction(1, 48_000)
        for pkt in enc.encode(frame):
            for f in dec.decode(pkt):
                out.append(f.to_ndarray().reshape(-1))
    for pkt in enc.encode(None):
        for f in dec.decode(pkt):
            out.append(f.to_ndarray().reshape(-1))
    y = np.concatenate(out).astype(np.float32)
    if y.dtype.kind == "f" and np.abs(y).max() > 2:
        y /= 32768
    y = resample_poly(y, 1, 3).astype(np.float32)
    return y[: len(x)]


def room(x: np.ndarray, rng: np.random.Generator, rt60: float = 0.4, drr_db: float = 6.0) -> np.ndarray:
    n = int(rt60 * SR)
    t = np.arange(n) / SR
    tail = rng.standard_normal(n) * np.exp(-6.91 * t / rt60)
    tail[: int(0.003 * SR)] = 0  # pierwsze odbicia po ~3 ms
    tail *= 10 ** (-drr_db / 20) / np.sqrt((tail ** 2).sum())
    rir = tail
    rir[0] = 1.0
    return fftconvolve(x, rir)[: len(x)].astype(np.float32)


def speaker(x: np.ndarray) -> np.ndarray:
    sos = butter(4, [300, 7000], btype="band", fs=SR, output="sos")
    y = sosfilt(sos, x)
    return (np.tanh(1.5 * y / max(np.abs(y).max(), 1e-6)) / np.tanh(1.5) * np.abs(y).max()).astype(np.float32)


def agc(x: np.ndarray, target_db: float = -22.0) -> np.ndarray:
    fr = x[: len(x) // 320 * 320].reshape(-1, 320)
    db = 10 * np.log10(np.maximum((fr ** 2).mean(1), 1e-12))
    loud = db > db.max() - 30
    level = 10 * np.log10(np.mean(fr[loud] ** 2)) if loud.any() else -60
    return np.clip(x * 10 ** ((target_db - level) / 20), -1, 1).astype(np.float32)


def channel(x: np.ndarray, name: str, seed: int = 0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    if name == "czysty":
        return x
    if name == "whatsapp":
        return opus(x)
    if name == "glosnik":
        y = room(speaker(opus(x)), rng)
        return agc(y + rng.standard_normal(len(y)).astype(np.float32) * 10 ** (-65 / 20))
    if name == "przy_mik":
        y = room(x, rng, rt60=0.3, drr_db=12)
        return agc(y + rng.standard_normal(len(y)).astype(np.float32) * 10 ** (-65 / 20))
    raise ValueError(name)


# ---------- VAD jak w Vad.kt ----------
FR, NFFT, LO, PEAK_SHARE, RUN = 320, 512, 3, 0.5, 25
HANN = np.hanning(FR)


def vad(w: np.ndarray) -> tuple[bool, float, float]:
    count = len(w) // FR
    loud = np.zeros(count, bool)
    peaky = np.zeros(count, bool)
    peak = np.zeros(count, int)
    for f in range(count):
        fr = w[f * FR:(f + 1) * FR]
        db = 10 * math.log10(max(float(np.mean(fr.astype(np.float64) ** 2)), 1e-20))
        loud[f] = db > -45
        if not loud[f]:
            continue
        band = (np.abs(np.fft.rfft(fr * HANN, NFFT)) ** 2)[LO:]
        k = int(np.argmax(band))
        tot = band.sum()
        share = band[max(0, k - 1):k + 2].sum() / tot if tot > 0 else 0
        peak[f], peaky[f] = k, share > PEAK_SHARE
    tone = np.zeros(count, bool)
    start = 0
    for f in range(1, count + 1):
        cont = f < count and peaky[f] and peaky[f - 1] and abs(peak[f] - peak[f - 1]) <= 1
        if cont:
            continue
        if peaky[start] and f - start >= RUN:
            tone[start:f] = True
        start = f
    sp = float((loud & ~tone).sum()) / count
    tn = float((loud & tone).sum()) / count
    return sp >= 0.2 and tn <= 0.1, sp, tn


# ---------- RiskEngine jak w RiskEngine.kt ----------
class Risk:
    def __init__(self, t: float, alpha=0.4, hist=3, hits=2, warn=0.7, release=0.5):
        self.t, self.a, self.hist, self.hits, self.warn, self.release = t, alpha, hist, hits, warn, release
        self.h: list[bool] = []
        self.ema = None
        self.level = "OK?"

    def update(self, s: float) -> str:
        self.ema = s if self.ema is None else self.a * s + (1 - self.a) * self.ema
        self.h = (self.h + [s >= self.t])[-self.hist:]
        hits = sum(self.h)
        if self.level == "DANGER" or hits >= self.hits:
            self.level = "DANGER"
        elif hits >= 1 or self.ema >= self.warn * self.t:
            self.level = "WARN"
        elif self.level == "WARN" and self.ema >= self.release * self.t:
            self.level = "WARN"
        else:
            self.level = "OK"
        return self.level


def run_app(x: np.ndarray, sess: ort.InferenceSession, n: int, threshold: float) -> dict:
    risk = Risk(threshold)
    scores, danger_at = [], None
    for end in range(4 * SR, len(x) + 1, SR):
        w = x[end - n:end]
        ok, _, _ = vad(w)
        if not ok:
            continue
        logit = float(sess.run(None, {"audio": w[None, :].astype(np.float32)})[0].reshape(-1)[0])
        s = 1 / (1 + math.exp(-logit))
        scores.append(s)
        if risk.update(s) == "DANGER" and danger_at is None:
            danger_at = end / SR
    a = np.array(scores) if scores else np.array([np.nan])
    return {
        "okna": len(scores),
        "mediana": float(np.median(a)),
        "max": float(np.max(a)),
        "powyzej_progu": float(np.mean(a >= threshold)) if scores else float("nan"),
        "wynik_aplikacji": risk.level if scores else "brak mowy",
        "czerwony_po_s": danger_at,
    }


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--model", type=Path, required=True, help="voice_clone.onnx z aplikacji")
    p.add_argument("--etykieta", choices=["prawdziwy", "klon"], required=True)
    p.add_argument("--kanaly", type=lambda v: v.split(","), default=["czysty", "whatsapp", "glosnik", "przy_mik"],
                   help="lista po przecinku, np. czysty,glosnik")
    p.add_argument("--zapisz-kanaly", type=Path, help="katalog na WAV po kanale (do symulacji w aplikacji)")
    p.add_argument("pliki", type=Path, nargs="+")
    args = p.parse_args()
    spec = json.loads(args.model.with_suffix(".json").read_text())
    so = ort.SessionOptions()
    so.intra_op_num_threads = 2  # nie zajmujemy całego procesora
    sess = ort.InferenceSession(str(args.model), so)
    n, thr = spec["window_samples"], spec["threshold"]
    for path in args.pliki:
        x = load(path)
        for ch in args.kanaly:
            y = channel(x, ch)
            if args.zapisz_kanaly:
                args.zapisz_kanaly.mkdir(parents=True, exist_ok=True)
                sf.write(args.zapisz_kanaly / f"{path.stem}__{ch}.wav", y, SR, subtype="PCM_16")
            r = run_app(y, sess, n, thr)
            print(json.dumps({"plik": path.name, "etykieta": args.etykieta, "kanal": ch, **r}, ensure_ascii=False))


if __name__ == "__main__":
    main()
