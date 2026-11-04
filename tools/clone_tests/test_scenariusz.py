"""Scenariusz rozmowy musi oceniać te same wejścia modelu co ocena.py i aplikacja."""
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np

import scenariusz
from ocena import SR, model_lowpass, run_app


class RecordingSession:
    def __init__(self):
        self.inputs = []

    def run(self, outputs, inputs):
        self.inputs.append(inputs["audio"].copy())
        return [np.array([0.0], dtype=np.float32)]


class ScenarioTest(unittest.TestCase):
    def test_scenario_matches_app_evaluation_with_and_without_filter(self):
        speech = np.random.default_rng(7).normal(0, 0.1, 4 * SR).astype(np.float32)
        audio = np.concatenate([scenariusz.ringback(5), speech, np.zeros(int(0.4 * SR), np.float32), speech])
        for cutoff in (None, 7000):
            with self.subTest(cutoff=cutoff), tempfile.TemporaryDirectory() as tmp:
                model = Path(tmp) / "voice_clone.onnx"
                spec = {"window_samples": 3 * SR, "threshold": 0.572}
                if cutoff is not None:
                    spec["lowpass_hz"] = cutoff
                model.with_suffix(".json").write_text(json.dumps(spec))
                expected, actual = RecordingSession(), RecordingSession()
                run_app(audio, expected, 3 * SR, 0.572, model_lowpass(cutoff))
                argv = ["scenariusz.py", "--model", str(model), "--prawdziwy", "real.wav", "--klon", "clone.wav"]
                with patch("sys.argv", argv), patch.object(scenariusz, "load", return_value=speech), \
                     patch.object(scenariusz, "channel", side_effect=lambda x, _: x), \
                     patch.object(scenariusz.ort, "InferenceSession", return_value=actual), \
                     contextlib.redirect_stdout(io.StringIO()):
                    scenariusz.main()
                self.assertGreater(len(expected.inputs), 0)
                self.assertEqual(len(expected.inputs), len(actual.inputs))
                np.testing.assert_array_equal(expected.inputs, actual.inputs)


if __name__ == "__main__":
    unittest.main()
