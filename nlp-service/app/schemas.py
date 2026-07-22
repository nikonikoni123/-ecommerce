"""Contrato del servicio de priorizacion, compartido con el backend de Spring Boot."""

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class Priority(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class Sentiment(str, Enum):
    NEGATIVE = "NEGATIVE"
    NEUTRAL = "NEUTRAL"
    POSITIVE = "POSITIVE"


class CaseItem(BaseModel):
    """Un caso de atencion pendiente de clasificar."""

    id: str
    text: str = Field(description="Asunto y cuerpo del caso, concatenados")
    created_at: datetime | None = None
    due_date: datetime | None = Field(
        default=None, description="Fecha de vencimiento del SLA, si la hay"
    )


class PrioritizeRequest(BaseModel):
    items: list[CaseItem]


class PrioritizedCase(BaseModel):
    id: str
    priority: Priority
    score: float = Field(description="Puntuacion de 0 a 1: cuanto mayor, mas urgente")
    sentiment: Sentiment
    reason: str = Field(description="Explicacion breve de por que se asigno esa prioridad")


class PrioritizeResponse(BaseModel):
    items: list[PrioritizedCase]
    model: str
    degraded: bool = Field(
        default=False,
        description="Verdadero si el modelo no estaba disponible y se uso solo la heuristica",
    )


class HealthResponse(BaseModel):
    status: str
    model: str
    model_loaded: bool
