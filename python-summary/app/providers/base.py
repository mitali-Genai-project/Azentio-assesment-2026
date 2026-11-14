from __future__ import annotations

from abc import ABC, abstractmethod


class LLMProvider(ABC):
    """Turns a fully-built prompt into text. Providers see only the prompt, never the raw request."""

    name: str

    @abstractmethod
    def generate(self, prompt: str) -> str:
        ...
