import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.models import SummaryRequest  # noqa: E402


@pytest.fixture
def request_data() -> dict:
    return {
        "customerName": "Aleksandr Petrov",
        "customerDob": "1980-04-17",
        "customerNationality": "RU",
        "matchedName": "Aleksandr Ivanovich Petrov",
        "matchedDob": "1980-01-01",
        "matchedNationality": "UA",
        "matchedOn": "primary",
        "score": 0.9302,
        "listType": "SANCTIONS",
        "listVersion": 3,
    }


@pytest.fixture
def summary_request(request_data) -> SummaryRequest:
    return SummaryRequest.model_validate(request_data)
