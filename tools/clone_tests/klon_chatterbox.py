"""Klonuje głos z nagrania referencyjnego przez Chatterbox Multilingual (MIT), lokalnie na CPU.

Użycie: python klon_chatterbox.py oryginal.wav katalog_wyjsciowy
Chatterbox dodaje do nagrań niesłyszalny znak wodny (Perth).
"""
import os, sys, time
import torch, torchaudio
torch.set_num_threads(6)
from chatterbox.mtl_tts import ChatterboxMultilingualTTS
ref, out_dir = sys.argv[1], sys.argv[2]
TEXTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "teksty.txt")
lines = [l.strip() for l in open(TEXTS, encoding="utf-8") if l.strip()]
model = ChatterboxMultilingualTTS.from_pretrained(device="cpu")
os.makedirs(out_dir, exist_ok=True)
for i, text in enumerate(lines):
    t = time.time()
    wav = model.generate(text, language_id="pl", audio_prompt_path=ref)
    torchaudio.save(f"{out_dir}/chatterbox_{i}.wav", wav, model.sr)
    print(f"chatterbox_{i}: {time.time()-t:.0f} s", flush=True)
