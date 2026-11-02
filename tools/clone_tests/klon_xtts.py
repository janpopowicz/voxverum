"""Klonuje głos z nagrania referencyjnego przez XTTS-v2 (Coqui), lokalnie na CPU.

Użycie: python klon_xtts.py oryginal.wav katalog_wyjsciowy
Licencja modelu: CPML (niekomercyjna); COQUI_TOS_AGREED=1 oznacza jej akceptację.
"""
import os, sys, time
os.environ["COQUI_TOS_AGREED"] = "1"
import torch, torchaudio, soundfile as sf

def _load(path, *a, **k):
    # torchaudio >= 2.9 ładuje przez torchcodec, który na Windows wymaga osobnego FFmpeg.
    x, sr = sf.read(path, dtype="float32", always_2d=True)
    return torch.from_numpy(x.T.copy()), sr

torchaudio.load = _load
from TTS.api import TTS
torch.set_num_threads(os.cpu_count() or 4)
ref, out_dir = sys.argv[1], sys.argv[2]
TEXTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "teksty.txt")
lines = [l.strip() for l in open(TEXTS, encoding="utf-8") if l.strip()]
tts = TTS("tts_models/multilingual/multi-dataset/xtts_v2").to("cpu")
os.makedirs(out_dir, exist_ok=True)
for i, text in enumerate(lines):
    t = time.time()
    tts.tts_to_file(text=text, speaker_wav=ref, language="pl", file_path=f"{out_dir}/xtts_{i}.wav")
    print(f"xtts_{i}: {time.time()-t:.0f} s", flush=True)
