import logging

import pytest
from fastapi.testclient import TestClient

from app.main import app, get_summary_service
from app.models import SummaryRequest
from app.providers import FakeProvider, LLMProvider, create_provider
from app.services.prompt_builder import PromptBuilder
from app.services.summary_service import FALLBACK_WARNING, SummaryService


class StubProvider(LLMProvider):
    """Returns a fixed text, simulating an LLM that ignores the instructions."""
    name = "stub"

    def __init__(self, text: str) -> None:
        self.text = text
        self.prompts: list[str] = []

    def generate(self, prompt: str) -> str:
        self.prompts.append(prompt)
        return self.text


class FailingProvider(LLMProvider):
    name = "failing"

    def generate(self, prompt: str) -> str:
        raise TimeoutError("provider timed out")


@pytest.fixture
def client():
    yield TestClient(app)
    app.dependency_overrides.clear()


# --- FakeProvider round trip -------------------------------------------------------------------

def test_fake_provider_round_trip_through_api(client, request_data):
    response = client.post("/summaries", json=request_data)

    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "fake"
    assert body["warnings"] == []
    summary = body["summary"]
    assert "Aleksandr Petrov" in summary and "Aleksandr Ivanovich Petrov" in summary
    assert "sanctions" in summary and "version 3" in summary and "0.93" in summary
    assert "Birth year is the same for both (1980)" in summary
    assert "Nationality differs: customer RU, listed party UA" in summary


def test_fake_provider_is_deterministic(summary_request):
    service = SummaryService(FakeProvider())
    assert service.summarise(summary_request) == service.summarise(summary_request)


def test_fake_provider_reports_missing_facts(request_data):
    request_data.update(customerDob=None, matchedNationality=None, listType="PEP", matchedOn="alias")
    response = SummaryService(FakeProvider()).summarise(SummaryRequest.model_validate(request_data))

    assert response.warnings == []
    assert "Birth year is not available for the customer (listed party: 1980)" in response.summary
    assert "Nationality is not available for the listed party (customer: RU)" in response.summary
    assert "politically exposed persons (PEP)" in response.summary


# --- Validation and fallback -------------------------------------------------------------------

def test_invented_year_triggers_fallback_and_warning(summary_request):
    service = SummaryService(StubProvider("The listed party was designated in 2014 and was born in 1980."))

    response = service.summarise(summary_request)

    assert response.warnings[0] == FALLBACK_WARNING
    assert "summary mentions unsupported year 2014" in response.warnings
    assert "2014" not in response.summary
    assert response.summary.startswith("Potential sanctions list match (list version 3)")


def test_invented_full_date_triggers_fallback(summary_request):
    response = SummaryService(StubProvider("Both were born on 17 April 1980.")).summarise(summary_request)

    assert FALLBACK_WARNING in response.warnings
    assert any("full date" in w for w in response.warnings)


def test_iso_date_triggers_fallback(summary_request):
    response = SummaryService(StubProvider("Customer DOB 1980-04-17 matches.")).summarise(summary_request)

    assert any("full date" in w for w in response.warnings)


def test_unsupported_list_type_triggers_fallback(summary_request):
    response = SummaryService(StubProvider("The person is a PEP on the OFAC list.")).summarise(summary_request)

    assert "summary mentions list type PEP, but the hit is on SANCTIONS" in response.warnings
    assert "summary names list programme OFAC, which was not supplied" in response.warnings


def test_match_conclusion_triggers_fallback(summary_request):
    response = SummaryService(StubProvider("They are clearly the same person.")).summarise(summary_request)

    assert any("match conclusion" in w for w in response.warnings)


def test_supported_years_are_allowed(summary_request):
    text = "Customer birth year 1980 and listed birth year 1980 are the same."
    response = SummaryService(StubProvider(text)).summarise(summary_request)

    assert response.warnings == []
    assert response.summary == text


def test_provider_failure_returns_fallback(summary_request):
    response = SummaryService(FailingProvider()).summarise(summary_request)

    assert response.warnings == [FALLBACK_WARNING, "Summary provider failed"]


def test_fallback_summary_passes_its_own_validation(summary_request):
    from app.services.summary_service import fallback_summary
    from app.services.summary_validator import find_problems

    assert find_problems(fallback_summary(summary_request), summary_request) == []


# --- DOB protection ----------------------------------------------------------------------------

def test_prompt_never_contains_full_customer_dob(summary_request):
    prompt = PromptBuilder().build(summary_request)

    assert "1980-04-17" not in prompt
    assert "04-17" not in prompt
    assert '"dob": "1980-**-**"' in prompt


def test_provider_receives_only_masked_dob(summary_request):
    stub = StubProvider("ok")
    SummaryService(stub).summarise(summary_request)

    assert "1980-04-17" not in stub.prompts[0]


def test_full_dob_is_never_logged(client, request_data, caplog):
    stub = StubProvider("Born 1975.")  # forces the warning/fallback log path too
    app.dependency_overrides[get_summary_service] = lambda: SummaryService(stub)

    with caplog.at_level(logging.DEBUG):
        client.post("/summaries", json=request_data)
        client.post("/summaries", json={**request_data, "score": 5})  # validation error path

    assert caplog.records, "expected log output to inspect"
    assert "1980-04-17" not in caplog.text
    assert "Aleksandr" not in caplog.text


def test_validation_error_does_not_echo_dob(client, request_data):
    response = client.post("/summaries", json={**request_data, "score": 5})

    assert response.status_code == 422
    assert "1980-04-17" not in response.text
    assert response.json()["details"][0]["location"] == "score"


# --- Configuration -----------------------------------------------------------------------------

def test_prompt_is_built_from_template_file(summary_request):
    from app.services.prompt_builder import TEMPLATE_PATH

    prompt = PromptBuilder().build(summary_request)

    assert TEMPLATE_PATH.name == "hit_summary.txt" and TEMPLATE_PATH.is_file()
    assert "Use ONLY the facts provided" in prompt
    assert "Do NOT conclude whether they are the same person" in prompt
    assert "Mention only the list type given in the facts (SANCTIONS)" in prompt


def test_unknown_provider_is_rejected():
    with pytest.raises(ValueError, match="Unknown SUMMARY_PROVIDER"):
        create_provider("does-not-exist")
