"""Deterministic stand-in for an LLM: reads the FACTS JSON from the prompt and writes a templated summary.

Same prompt in -> same text out, no network, so tests and offline runs are reproducible. It obeys the prompt's
rules (only supplied facts, differences not conclusions), which makes it a useful reference for what a real
provider's output should look like.
"""
from __future__ import annotations

import json

from app.providers.base import LLMProvider
from app.services.prompt_builder import FACTS_MARKER

_LIST_LABELS = {"SANCTIONS": "sanctions", "PEP": "politically exposed persons (PEP)"}


class FakeProvider(LLMProvider):
    name = "fake"

    def generate(self, prompt: str) -> str:
        facts = json.loads(prompt.split(FACTS_MARKER, 1)[1])
        customer, listed = facts["customer"], facts["listedParty"]
        sentences = [
            f"Customer '{customer['name']}' was flagged against the {_LIST_LABELS[facts['listType']]} list "
            f"(version {facts['listVersion']}) on the listed party's {facts['matchedOn']} name "
            f"'{listed['name']}' with a similarity score of {facts['score']:.2f}.",
            _compare("Birth year", _year(customer["dob"]), _year(listed["dob"])),
            _compare("Nationality", customer["nationality"], listed["nationality"]),
            "This summary makes no determination; analyst review is required.",
        ]
        return " ".join(sentences)


def _year(masked_dob: str | None) -> str | None:
    return None if masked_dob is None else masked_dob[:4]


def _compare(label: str, customer_value: str | None, listed_value: str | None) -> str:
    if customer_value is None and listed_value is None:
        return f"{label} is not available for either party."
    if customer_value is None:
        return f"{label} is not available for the customer (listed party: {listed_value})."
    if listed_value is None:
        return f"{label} is not available for the listed party (customer: {customer_value})."
    if customer_value == listed_value:
        return f"{label} is the same for both ({customer_value})."
    return f"{label} differs: customer {customer_value}, listed party {listed_value}."
