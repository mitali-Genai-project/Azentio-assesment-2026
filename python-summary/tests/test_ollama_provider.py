"""OllamaProvider without a running Ollama server: the HTTP call is replaced by a fake."""
import io
import json

from app.providers import OllamaProvider, create_provider
from app.services.summary_service import FALLBACK_WARNING, SummaryService


class FakeHttpResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def test_selected_by_configuration_name():
    assert isinstance(create_provider("ollama"), OllamaProvider)
    assert isinstance(create_provider("OLLAMA "), OllamaProvider)


def test_sends_prompt_to_configured_server_and_returns_text(monkeypatch):
    monkeypatch.setenv("OLLAMA_BASE_URL", "http://ollama.test:11434/")
    monkeypatch.setenv("OLLAMA_MODEL", "test-model")
    captured = {}

    def fake_urlopen(request, timeout):
        captured.update(url=request.full_url, body=json.loads(request.data), timeout=timeout)
        return FakeHttpResponse(json.dumps({"response": "A neutral summary."}).encode())

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)

    text = OllamaProvider().generate("the prompt")

    assert text == "A neutral summary."
    assert captured["url"] == "http://ollama.test:11434/api/generate"
    assert captured["body"]["model"] == "test-model"
    assert captured["body"]["prompt"] == "the prompt"
    assert captured["body"]["stream"] is False


def test_unreachable_server_falls_back(monkeypatch, summary_request):
    def refuse(request, timeout):
        raise ConnectionRefusedError("no ollama running")

    monkeypatch.setattr("urllib.request.urlopen", refuse)

    response = SummaryService(OllamaProvider()).summarise(summary_request)

    assert response.provider == "ollama"
    assert response.warnings == [FALLBACK_WARNING, "Summary provider failed"]
