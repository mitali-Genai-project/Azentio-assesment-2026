"""Builds the provider prompt from the template file and a minimised, DOB-masked set of facts."""
from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from string import Template

from app.models import SummaryRequest

TEMPLATE_PATH = Path(__file__).resolve().parent.parent / "prompts" / "hit_summary.txt"
FACTS_MARKER = "FACTS (JSON):"


def mask_dob(dob: date | None) -> str | None:
    """Keep only the year: 1980-04-17 -> '1980-**-**'. The full DOB never reaches a provider."""
    return None if dob is None else f"{dob.year:04d}-**-**"


def build_facts(request: SummaryRequest) -> dict:
    # Both DOBs are reduced to the year: the comparison only needs years (data minimisation), and the
    # customer's full DOB must never be sent to a provider.
    return {
        "customer": {
            "name": request.customer_name,
            "dob": mask_dob(request.customer_dob),
            "nationality": request.customer_nationality,
        },
        "listedParty": {
            "name": request.matched_name,
            "dob": mask_dob(request.matched_dob),
            "nationality": request.matched_nationality,
        },
        "matchedOn": request.matched_on,
        "score": round(request.score, 4),
        "listType": request.list_type,
        "listVersion": request.list_version,
    }


class PromptBuilder:
    def __init__(self, template_path: Path = TEMPLATE_PATH) -> None:
        self._template = Template(template_path.read_text(encoding="utf-8"))

    def build(self, request: SummaryRequest) -> str:
        facts = json.dumps(build_facts(request), indent=2, ensure_ascii=False)
        return self._template.substitute(facts=facts, list_type=request.list_type)
