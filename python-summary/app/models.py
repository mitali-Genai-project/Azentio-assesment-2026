"""Request/response models. JSON uses camelCase to match the Java API."""
from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

COUNTRY_PATTERN = r"^[A-Z]{2}$"


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class SummaryRequest(CamelModel):
    customer_name: str = Field(min_length=1, max_length=255)
    customer_dob: date | None = None
    customer_nationality: str | None = Field(default=None, pattern=COUNTRY_PATTERN)

    matched_name: str = Field(min_length=1, max_length=255)
    matched_dob: date | None = None
    matched_nationality: str | None = Field(default=None, pattern=COUNTRY_PATTERN)

    matched_on: Literal["primary", "alias"]
    score: float = Field(ge=0.0, le=1.0)
    list_type: Literal["SANCTIONS", "PEP"]
    list_version: int = Field(ge=1)


class SummaryResponse(CamelModel):
    summary: str
    provider: str
    warnings: list[str] = Field(default_factory=list)
