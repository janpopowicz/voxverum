#!/usr/bin/env python3
"""Score one recording with Mother Voice. No ASR or content classification."""
import argparse
import json
import math
from pathlib import Path

import numpy as np
import soundfile as sf
import torch

from mother_voice import MotherVoice


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('audio', type=Path)
    parser.add_argument('--checkpoint', type=Path, default=Path(__file__).resolve().parents[1] / 'models/mother-voice-v1/best.pt')
    parser.add_argument('--device', choices=['cpu', 'cuda'], default='cpu')
    parser.add_argument('--hop-seconds', type=float, default=1.5)
    args = parser.parse_args()
    if args.hop_seconds <= 0:
        parser.error('--hop-seconds must be positive')
    torch.set_num_threads(4)
    saved = torch.load(args.checkpoint, map_location='cpu', weights_only=True)
    model = MotherVoice().to(args.device).eval()
    model.load_state_dict(saved['model'])
    audio, sample_rate = sf.read(args.audio, dtype='float32', always_2d=True)
    if not np.isfinite(audio).all():
        raise ValueError('Non-finite audio')
    audio = audio.mean(axis=1)
    if sample_rate != 16000:
        from scipy.signal import resample_poly
        divisor = math.gcd(sample_rate, 16000)
        audio = resample_poly(audio, 16000 // divisor, sample_rate // divisor).astype(np.float32)
    size = int(saved['config']['crop_seconds'] * 16000)
    if len(audio) < size:
        raise ValueError('At least 3 seconds of audio required; short clips are not padded')
    hop = max(1, int(args.hop_seconds * 16000))
    starts = list(range(0, len(audio) - size + 1, hop))
    if starts[-1] != len(audio) - size:
        starts.append(len(audio) - size)
    windows = []
    with torch.inference_mode():
        for start in starts:
            waveform = audio[start:start + size]
            rms = float(np.sqrt(np.mean(waveform ** 2)))
            # This gate is an explicit heuristic, not a trained speech detector.
            if rms < 1e-4:
                windows.append({'start_seconds': start / 16000, 'score': None,
                                'status': 'insufficient_signal'})
                continue
            score = torch.sigmoid(model(torch.from_numpy(waveform.copy()).unsqueeze(0).to(args.device))).item()
            windows.append({'start_seconds': start / 16000, 'score': score, 'status': 'scored'})
    scores = [w['score'] for w in windows if w['score'] is not None]
    threshold = saved['thresholds']['eer']
    # Only a single 3s window matches the validated dev/test decision rule.
    single = len(windows) == 1 and len(scores) == 1
    result = {'model': saved['config']['name'], 'task': 'synthetic_voice_detection',
              'score_direction': 'higher=spoof', 'score_is_calibrated_probability': False,
              'threshold_from_dev': threshold,
              'decision': ('spoof' if scores[0] >= threshold else 'bonafide') if single else None,
              'mean_window_score': float(np.mean(scores)) if scores else None,
              'aggregation': 'mean of non-silent window scores; multi-window decision is not calibrated',
              'silence_gate': 'RMS < 1e-4; heuristic, not speech activity detection',
              'windows': windows}
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
