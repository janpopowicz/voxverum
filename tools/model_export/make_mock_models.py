"""Generuje modele-atrapy ONNX dla obu zadań, żeby aplikacja działała przed dostarczeniem prawdziwych modeli.

Atrapy NIE wykrywają niczego. Liczą prostą funkcję głośności okna, więc wynik rośnie,
gdy mówisz głośniej. To wystarcza do testowania UI, alertów i progów.

Użycie (z katalogu repo):
    tools/model_export/.venv/Scripts/python tools/model_export/make_mock_models.py
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper

REPO = Path(__file__).resolve().parents[2]
DEFAULT_OUT = REPO / "android" / "app" / "src" / "main" / "assets" / "models"
SAMPLE_RATE = 16_000
WINDOW_SAMPLES = 4 * SAMPLE_RATE
OPSET = 17


def _scalar(name: str, value: float) -> onnx.TensorProto:
    return helper.make_tensor(name, TensorProto.FLOAT, [], [value])


def build_mean_abs_model(scale: float, bias: float) -> onnx.ModelProto:
    """sigmoid(scale * mean(|x|) + bias), wyjście [1, 1]."""
    nodes = [
        helper.make_node("Abs", ["audio"], ["abs"]),
        helper.make_node("ReduceMean", ["abs"], ["mean"], axes=[1], keepdims=1),
        helper.make_node("Mul", ["mean", "scale"], ["scaled"]),
        helper.make_node("Add", ["scaled", "bias"], ["logit"]),
        helper.make_node("Sigmoid", ["logit"], ["score"]),
    ]
    return _model(nodes, [_scalar("scale", scale), _scalar("bias", bias)], "mock_mean_abs")


def build_rms_model(scale: float, bias: float) -> onnx.ModelProto:
    """sigmoid(scale * rms(x) + bias), wyjście [1, 1]."""
    nodes = [
        helper.make_node("Mul", ["audio", "audio"], ["sq"]),
        helper.make_node("ReduceMean", ["sq"], ["mean"], axes=[1], keepdims=1),
        helper.make_node("Sqrt", ["mean"], ["rms"]),
        helper.make_node("Mul", ["rms", "scale"], ["scaled"]),
        helper.make_node("Add", ["scaled", "bias"], ["logit"]),
        helper.make_node("Sigmoid", ["logit"], ["score"]),
    ]
    return _model(nodes, [_scalar("scale", scale), _scalar("bias", bias)], "mock_rms")


def _model(nodes, initializers, name: str) -> onnx.ModelProto:
    graph = helper.make_graph(
        nodes,
        name,
        [helper.make_tensor_value_info("audio", TensorProto.FLOAT, [1, WINDOW_SAMPLES])],
        [helper.make_tensor_value_info("score", TensorProto.FLOAT, [1, 1])],
        initializer=initializers,
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", OPSET)], producer_name="voxverum-mock")
    model.ir_version = 8  # zgodne z ONNX Runtime >= 1.14
    onnx.checker.check_model(model)
    return model


def spec(task: str, file: str, threshold: float) -> dict:
    return {
        "task": task,
        "file": file,
        "version": "mock-0.1",
        "sample_rate": SAMPLE_RATE,
        "window_samples": WINDOW_SAMPLES,
        "input_name": "audio",
        "output_name": "score",
        "output_type": "probability",
        "positive_index": 0,
        "threshold": threshold,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)

    models = {
        "voice_clone": build_mean_abs_model(scale=25.0, bias=-3.0),
        "scam": build_rms_model(scale=15.0, bias=-3.5),
    }
    rng = np.random.default_rng(0)
    for task, model in models.items():
        onnx_path = args.out_dir / f"{task}.onnx"
        onnx.save(model, onnx_path)
        (args.out_dir / f"{task}.json").write_text(json.dumps(spec(task, onnx_path.name, 0.8), indent=2) + "\n")

        session = ort.InferenceSession(str(onnx_path))
        quiet = session.run(None, {"audio": (rng.standard_normal((1, WINDOW_SAMPLES)) * 0.01).astype(np.float32)})[0]
        loud = session.run(None, {"audio": (rng.standard_normal((1, WINDOW_SAMPLES)) * 0.3).astype(np.float32)})[0]
        print(f"{task}: {onnx_path} quiet={quiet.item():.3f} loud={loud.item():.3f}")


if __name__ == "__main__":
    main()
