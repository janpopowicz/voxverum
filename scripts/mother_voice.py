"""Mother's first audio-only synthetic speech detector and evaluation helpers."""
from collections import defaultdict

import numpy as np
import torch
from torch import nn


class ResidualBlock(nn.Module):
    def __init__(self, incoming, outgoing):
        super().__init__()
        self.main = nn.Sequential(
            nn.Conv2d(incoming, outgoing, 3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(outgoing), nn.SiLU(),
            nn.Conv2d(outgoing, outgoing, 3, padding=1, bias=False),
            nn.BatchNorm2d(outgoing),
        )
        self.skip = nn.Sequential(nn.Conv2d(incoming, outgoing, 1, stride=2, bias=False),
                                  nn.BatchNorm2d(outgoing))
        self.activation = nn.SiLU()

    def forward(self, x):
        return self.activation(self.main(x) + self.skip(x))


class MotherVoice(nn.Module):
    """Raw mono 16 kHz audio -> one spoof logit. No metadata inputs.

    Log-power STFT retains the full 0--8 kHz band. Per-clip normalization
    removes absolute recording gain. Frequency bands are retained in pooling;
    temporal mean/std summarize each band. This is a from-scratch baseline.
    """
    def __init__(self):
        super().__init__()
        self.register_buffer('window', torch.hann_window(512))
        self.encoder = nn.Sequential(ResidualBlock(1, 16), ResidualBlock(16, 32),
                                     ResidualBlock(32, 64), ResidualBlock(64, 128))
        self.classifier = nn.Sequential(nn.Linear(1024, 128), nn.SiLU(),
                                        nn.Dropout(0.25), nn.Linear(128, 1))

    def forward(self, waveform):
        spectrum = torch.stft(waveform.float(), n_fft=512, hop_length=160,
                              window=self.window, return_complex=True)
        features = spectrum.abs().square().clamp_min(1e-10).log()
        mean = features.mean(dim=(1, 2), keepdim=True)
        std = features.std(dim=(1, 2), keepdim=True).clamp_min(1e-5)
        features = ((features - mean) / std).unsqueeze(1)
        x = self.encoder(features)
        # Retain four frequency regions, then pool only along time.
        x = nn.functional.adaptive_avg_pool2d(x, (4, x.shape[-1]))
        pooled = torch.cat((x.mean(dim=-1), x.std(dim=-1, unbiased=False)), dim=1)
        return self.classifier(pooled.flatten(1)).squeeze(1)


def binary_metrics(labels, scores, threshold=0.5):
    """FPR means human audio flagged as synthetic; higher score = spoof."""
    from sklearn.metrics import roc_auc_score, roc_curve
    labels, scores = np.asarray(labels), np.asarray(scores)
    if len(labels) == 0 or not np.isfinite(scores).all():
        raise ValueError('Empty or non-finite evaluation scores')
    predicted = scores >= threshold
    real, fake = labels == 0, labels == 1
    fp, fn = int((predicted & real).sum()), int((~predicted & fake).sum())
    result = {'n': len(labels), 'bonafide': int(real.sum()), 'spoof': int(fake.sum()),
              'threshold': float(threshold), 'fp': fp, 'fn': fn,
              'fpr': fp / int(real.sum()) if real.any() else None,
              'fnr': fn / int(fake.sum()) if fake.any() else None,
              'accuracy': float((predicted == labels).mean()), 'roc_auc': None, 'eer': None}
    if real.any() and fake.any():
        fpr, tpr, _ = roc_curve(labels, scores, drop_intermediate=False)
        index = int(np.argmin(np.abs(fpr - (1 - tpr))))
        result.update(roc_auc=float(roc_auc_score(labels, scores)),
                      eer=float((fpr[index] + 1 - tpr[index]) / 2))
    return result


def select_thresholds(labels, scores, target_fpr=0.01):
    """Select empirical EER and low-FPR operating thresholds on DEV ONLY."""
    from sklearn.metrics import roc_curve
    fpr, tpr, thresholds = roc_curve(labels, scores, drop_intermediate=False)
    thresholds[~np.isfinite(thresholds)] = np.nextafter(float(max(scores)), np.inf)
    eer_index = int(np.argmin(np.abs(fpr - (1 - tpr))))
    low_index = int(np.flatnonzero(fpr <= target_fpr)[-1])
    return {'eer': float(thresholds[eer_index]), 'low_fpr': float(thresholds[low_index]),
            'target_fpr': target_fpr}


def evaluation_report(rows, labels, scores, thresholds):
    result = {'score_direction': 'higher=spoof', 'threshold_source': 'Polish dev',
              'eer_method': 'nearest empirical ROC point, mean(FPR,FNR)',
              'overall': binary_metrics(labels, scores, thresholds['eer']),
              'low_fpr_operating_point': binary_metrics(labels, scores, thresholds['low_fpr']),
              'groups': {}}
    # Pair generator is also assigned to bonafide for an honest paired comparison.
    pair_generator = {r['pair_id']: r['generator'] for r in rows
                      if r.get('pair_id') and r.get('generator')}
    for field in ('channel', 'corpus', 'generator', 'speaker_id'):
        groups = defaultdict(list)
        for index, row in enumerate(rows):
            key = (pair_generator.get(row.get('pair_id'), row.get(field))
                   if field == 'generator' else row.get(field))
            groups[str(key or 'unknown')].append(index)
        result['groups'][field] = {
            key: binary_metrics(np.asarray(labels)[indices], np.asarray(scores)[indices], thresholds['eer'])
            for key, indices in sorted(groups.items())}
    return result
