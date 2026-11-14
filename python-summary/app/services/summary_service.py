"""Builds the prompt, calls the provider, validates the output and falls back when it is not supported."""
from __future__ import annotations

import logging

from app.models import SummaryRequest, SummaryResponse
from app.providers.base import LLMProvider
from app.services.prompt_builder import PromptBuilder
from app.services.summary_validator import find_problems

log = logging.getLogger(__name__)

FALLBACK_WARNING = "Generated summary failed validation; a fact-only fallback summary was returned"


class SummaryService:
    def __init__(self, provider: LLMProvider, prompt_builder: PromptBuilder | None = None) -> None:
        self._provider = provider
        self._prompts = prompt_builder or PromptBuilder()

    def summarise(self, request: SummaryRequest) -> SummaryResponse:
        prompt = self._prompts.build(request)
        # Only non-identifying metadata is logged: never names or dates of birth.
        log.info("Summarising hit: listType=%s listVersion=%s matchedOn=%s provider=%s",
                 request.list_type, request.list_version, request.matched_on, self._provider.name)

        try:
            generated = self._provider.generate(prompt).strip()
        except Exception as exc:  # provider outage must not break the analyst's workflow
            log.warning("Provider %s failed: %s", self._provider.name, type(exc).__name__)
            return self._fallback(request, ["Summary provider failed"])

        problems = find_problems(generated, request) if generated else ["summary was empty"]
        if problems:
            log.warning("Rejected generated summary: %s", "; ".join(problems))
            return self._fallback(request, problems)
        return SummaryResponse(summary=generated, provider=self._provider.name, warnings=[])

    def _fallback(self, request: SummaryRequest, problems: list[str]) -> SummaryResponse:
        return SummaryResponse(summary=fallback_summary(request), provider=self._provider.name,
                               warnings=[FALLBACK_WARNING, *problems])


def fallback_summary(request: SummaryRequest) -> str:
    """Deterministic, fact-only text built without any provider."""
    list_label = "sanctions" if request.list_type == "SANCTIONS" else "PEP"
    customer_year = str(request.customer_dob.year) if request.customer_dob else "not provided"
    listed_year = str(request.matched_dob.year) if request.matched_dob else "not provided"
    return (
        f"Potential {list_label} list match (list version {request.list_version}): customer "
        f"'{request.customer_name}' vs listed {request.matched_on} name '{request.matched_name}', "
        f"score {request.score:.2f}. Birth year: customer {customer_year}, listed party {listed_year}. "
        f"Nationality: customer {request.customer_nationality or 'not provided'}, "
        f"listed party {request.matched_nationality or 'not provided'}. Analyst review required."
    )
