"""
Microservicio de priorizacion de casos de atencion.

Lo consume el panel de casos de la empresa (etapa 4). Se despliega desde la etapa 1 para fijar el
contrato: el backend ya sabe llamarlo y sabe degradarse si no responde.
"""

import logging

from fastapi import FastAPI

from . import model
from .schemas import HealthResponse, PrioritizeRequest, PrioritizeResponse

logging.basicConfig(level=logging.INFO)

app = FastAPI(
    title="Servicio de priorizacion de casos",
    description="Clasifica los casos de atencion por prioridad usando un modelo BERT multilingue.",
    version="0.1.0",
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    """No fuerza la carga del modelo: debe responder en cuanto el proceso esta vivo."""
    return HealthResponse(
        status="ok", model=model.MODEL_NAME, model_loaded=model.is_loaded()
    )


@app.post("/prioritize", response_model=PrioritizeResponse)
def prioritize(request: PrioritizeRequest) -> PrioritizeResponse:
    """Devuelve los casos ordenados de mas a menos urgente."""
    items, degraded = model.prioritize(request.items)
    return PrioritizeResponse(items=items, model=model.MODEL_NAME, degraded=degraded)


@app.post("/warmup")
def warmup() -> dict:
    """Fuerza la carga del modelo, para no pagar la primera inferencia en una peticion real."""
    model.get_pipeline()
    return {"model_loaded": model.is_loaded()}
