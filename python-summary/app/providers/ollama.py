"""Optional real provider: a local Ollama server (no API key, standard library HTTP only).

Enable with SUMMARY_PROVIDER=ollama. Settings (environment):
  OLLAMA_BASE_URL         default http://localhost:11434
  OLLAMA_MODEL            default llama3.1
  OLLAMA_TIMEOUT_SECONDS  default 30
Errors propagate to SummaryService, which returns the fact-only fallback summary with a warning.
"""
from __future__ import annotations

import json
import os
import urllib.request

from app.providers.base import LLMProvider


class OllamaProvider(LLMProvider):
    name = "ollama"

    def __init__(self) -> None:
        self._url = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/") + "/api/generate"
        self._model = os.environ.get("OLLAMA_MODEL", "llama3.1")
        self._timeout = float(os.environ.get("OLLAMA_TIMEOUT_SECONDS", "30"))

    def generate(self, prompt: str) -> str:
        body = json.dumps({
            "model": self._model,
            "prompt": prompt,
            "stream": False,
            "options": {"temperature": 0},   # as deterministic as the model allows
        }).encode("utf-8")
        request = urllib.request.Request(self._url, data=body, method="POST",
                                         headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=self._timeout) as response:
            return json.loads(response.read())["response"]
