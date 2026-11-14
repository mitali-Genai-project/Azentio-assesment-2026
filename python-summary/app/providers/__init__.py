"""LLM provider abstraction and configuration-based selection."""
from __future__ import annotations

from app.providers.base import LLMProvider
from app.providers.fake import FakeProvider
from app.providers.ollama import OllamaProvider

_PROVIDERS: dict[str, type[LLMProvider]] = {
    FakeProvider.name: FakeProvider,
    OllamaProvider.name: OllamaProvider,
}


def create_provider(name: str) -> LLMProvider:
    try:
        return _PROVIDERS[name.strip().lower()]()
    except KeyError:
        raise ValueError(f"Unknown SUMMARY_PROVIDER '{name}'; available: {sorted(_PROVIDERS)}") from None


__all__ = ["LLMProvider", "FakeProvider", "OllamaProvider", "create_provider"]
