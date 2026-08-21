from fastapi import APIRouter
from fastapi.responses import Response

from ..core.metrics import render_metrics

router = APIRouter()


@router.get("/health")
def health():
    return {"status": "ok"}


@router.get("/metrics")
def metrics():
    body, content_type = render_metrics()
    return Response(content=body, media_type=content_type)
