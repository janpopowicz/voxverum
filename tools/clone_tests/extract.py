"""Wyciąga ścieżkę audio z pliku wideo/audio do WAV 24 kHz mono i wypisuje parametry (bez treści).

Użycie: python extract.py nagranie.mp4 oryginal_24k.wav
"""
import av, numpy as np, soundfile as sf, sys
src, out = sys.argv[1], sys.argv[2]
c = av.open(src)
a = c.streams.audio[0]
print("kodek", a.codec_context.name, "Hz", a.rate, "kanały", a.channels)
res = av.AudioResampler(format="flt", layout="mono", rate=24000)
chunks = []
for frame in c.decode(a):
    for f in res.resample(frame): chunks.append(f.to_ndarray().reshape(-1))
for f in res.resample(None): chunks.append(f.to_ndarray().reshape(-1))
x = np.concatenate(chunks).astype(np.float32)
sf.write(out, x, 24000, subtype="PCM_16")
fr = x[: len(x)//480*480].reshape(-1, 480)
db = 10*np.log10(np.maximum((fr**2).mean(1), 1e-12))
print(f"długość {len(x)/24000:.1f} s, szczyt {np.abs(x).max():.2f}, ramki > -45 dBFS: {(db>-45).mean():.0%}, mediana głośnych {np.median(db[db>-45]):.1f} dBFS")
