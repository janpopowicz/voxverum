"""Podobieństwo głosu klonów do oryginału: cosinus embeddingów mówcy z enkodera Chatterbox.

Punkt odniesienia: dwie połowy oryginału (ten sam człowiek). Klon z wynikiem zbliżonym do tej wartości
brzmi jak oryginał dla systemu rozpoznawania mówcy, więc jest przekonujący.

Użycie (w środowisku z chatterbox-tts): python podobienstwo.py oryginal.wav klon1.wav klon2.wav ...
"""
import sys

import librosa
import numpy as np
import torch
from chatterbox.models.voice_encoder import VoiceEncoder
from huggingface_hub import hf_hub_download

torch.set_num_threads(4)
ve = VoiceEncoder()
ve.load_state_dict(torch.load(hf_hub_download("ResembleAI/chatterbox", "ve.pt"), map_location="cpu"))
ve.eval()


def emb(x):
    return ve.embeds_from_wavs([x], sample_rate=16000)[0]


def cos(u, v):
    return float(np.dot(u, v) / np.linalg.norm(u) / np.linalg.norm(v))


ref = librosa.load(sys.argv[1], sr=16000)[0]
half = len(ref) // 2
print(f"oryginał: 1. połowa vs 2. połowa  {cos(emb(ref[:half]), emb(ref[half:])):.3f}  (punkt odniesienia)")
r = emb(ref)
for path in sys.argv[2:]:
    print(f"{path}: {cos(r, emb(librosa.load(path, sr=16000)[0])):.3f}")
