"""Numerical/contract checks for Mother; no dataset generation or test-set scoring."""
import unittest

import numpy as np
import torch

from mother_voice import MotherVoice, binary_metrics, evaluation_report, select_thresholds


class MotherChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_waveform_backward_and_silence_are_finite(self):
        model = MotherVoice()
        waveform = torch.stack([torch.randn(48000) * 0.03, torch.zeros(48000)])
        logits = model(waveform)
        self.assertEqual(tuple(logits.shape), (2,))
        loss = torch.nn.functional.binary_cross_entropy_with_logits(logits, torch.tensor([0., 1.]))
        loss.backward()
        self.assertTrue(torch.isfinite(loss))
        self.assertTrue(all(torch.isfinite(p.grad).all() for p in model.parameters()))

    def test_metrics_direction_and_operating_point(self):
        labels, scores = [0, 0, 1, 1], [0.1, 0.2, 0.8, 0.9]
        thresholds = select_thresholds(labels, scores)
        metrics = binary_metrics(labels, scores, thresholds['eer'])
        self.assertEqual(metrics['roc_auc'], 1)
        self.assertEqual(metrics['eer'], 0)
        self.assertEqual((metrics['fp'], metrics['fn']), (0, 0))
        low = binary_metrics(labels, scores, thresholds['low_fpr'])
        self.assertEqual(low['fpr'], 0)
        self.assertEqual(low['fnr'], 0)

    def test_ties_do_not_break_low_fpr_threshold(self):
        thresholds = select_thresholds([0, 0, 1, 1], [0.5] * 4)
        metrics = binary_metrics([0, 0, 1, 1], [0.5] * 4, thresholds['low_fpr'])
        self.assertEqual(metrics['roc_auc'], 0.5)
        self.assertEqual(metrics['fpr'], 0)
        self.assertEqual(metrics['fnr'], 1)

    def test_pair_generator_groups_have_both_classes(self):
        rows = [{'pair_id': 'one', 'generator': None},
                {'pair_id': 'one', 'generator': 'voxcpm2'}]
        report = evaluation_report(rows, [0, 1], [0.1, 0.9], {'eer': 0.5, 'low_fpr': 0.5})
        group = report['groups']['generator']['voxcpm2']
        self.assertEqual((group['bonafide'], group['spoof']), (1, 1))

    def test_single_class_group_has_no_roc_claim(self):
        metrics = binary_metrics([0, 0], [0.1, 0.9])
        self.assertIsNone(metrics['roc_auc'])
        self.assertIsNone(metrics['eer'])
        self.assertIsNone(metrics['fnr'])

    def test_nonfinite_scores_rejected(self):
        with self.assertRaises(ValueError):
            binary_metrics([0, 1], [0.2, np.nan])



if __name__ == '__main__':
    unittest.main()
