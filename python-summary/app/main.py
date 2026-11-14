"""FastAPI entry point: POST /summaries.  Run: uvicorn app.main:app --port 8000"""
from __future__ import annotations

import logging
import os
from functools import lru_cache

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.models import SummaryRequest, SummaryResponse
from app.providers import create_provider
from app.services.summary_service import SummaryService

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

app = FastAPI(title="Clearlist hit summary service", version="0.1.0")


@lru_cache
def get_summary_service() -> SummaryService:
    return SummaryService(create_provider(os.environ.get("SUMMARY_PROVIDER", "fake")))


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    # FastAPI's default 422 body echoes the submitted values (which may include a full DOB); return only
    # the location and message of each problem.
    details = [{"location": ".".join(str(p) for p in err["loc"][1:]), "message": err["msg"]}
               for err in exc.errors()]
    return JSONResponse(status_code=422, content={"error": "VALIDATION_FAILED", "details": details})


@app.post("/summaries", response_model=SummaryResponse, response_model_by_alias=True)
def create_summary(request: SummaryRequest,
                   service: SummaryService = Depends(get_summary_service)) -> SummaryResponse:
    return service.summarise(request)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
