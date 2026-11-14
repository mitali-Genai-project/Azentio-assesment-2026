"""Checks a generated summary against the supplied facts.

Flags anything the provider could not have learned from the facts:
  * a year other than the two parties' birth years (or the list version printed as text)
  * any full calendar date (the prompt only ever contains years)
  * a list type other than the one supplied, or a named list programme (OFAC, UN, ...)
  * language that concludes the hit is (or is not) the same person
"""
from __future__ import annotations

import re

from app.models import SummaryRequest

_YEAR = re.compile(r"\b(1[89]\d{2}|2\d{3})\b")
_FULL_DATE = re.compile(
    r"\b\d{4}-\d{1,2}-\d{1,2}\b"                                   # 1980-04-17
    r"|\b\d{1,2}[/.]\d{1,2}[/.]\d{2,4}\b"                          # 17/04/1980
    r"|\b\d{1,2}(st|nd|rd|th)?\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\b"
    r"|\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}(st|nd|rd|th)?\b",
    re.IGNORECASE)
_LIST_TYPE_MENTIONS = {
    "SANCTIONS": re.compile(r"\bsanction(s|ed)?\b", re.IGNORECASE),
    "PEP": re.compile(r"\bPEPs?\b|\bpolitically exposed\b", re.IGNORECASE),
}
_LIST_PROGRAMMES = re.compile(r"\b(OFAC|OFSI|HMT|UN|EU|INTERPOL|SDN)\b")
_CONCLUSIONS = re.compile(
    r"\b(is|are) (definitely |clearly |likely |probably )?(the same (person|individual)|a (true|confirmed) match)\b"
    r"|\bconfirmed match\b|\bnot the same person\b|\bfalse positive\b",
    re.IGNORECASE)


def find_problems(summary: str, request: SummaryRequest) -> list[str]:
    problems: list[str] = []

    allowed_years = {str(d.year) for d in (request.customer_dob, request.matched_dob) if d is not None}
    for year in sorted(set(_YEAR.findall(summary)) - allowed_years):
        problems.append(f"summary mentions unsupported year {year}")

    if _FULL_DATE.search(summary):
        problems.append("summary mentions a full date, but only birth years were supplied")

    for list_type, pattern in _LIST_TYPE_MENTIONS.items():
        if list_type != request.list_type and pattern.search(summary):
            problems.append(f"summary mentions list type {list_type}, but the hit is on {request.list_type}")

    for programme in sorted(set(_LIST_PROGRAMMES.findall(summary))):
        problems.append(f"summary names list programme {programme}, which was not supplied")

    if _CONCLUSIONS.search(summary):
        problems.append("summary draws a match conclusion; that decision belongs to the analyst")

    return problems
