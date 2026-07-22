"""
Clasificacion de casos de atencion con un modelo BERT multilingue.

El modelo aporta la lectura del tono del mensaje. La prioridad final combina ese tono con senales
objetivas que el texto no contiene: cuanto falta para el vencimiento del SLA y la antiguedad del
caso. Un cliente educado con el SLA a punto de vencer debe subir igual que uno enfadado.
"""

from __future__ import annotations

import logging
import os
import re
import threading
from datetime import datetime, timezone

from .schemas import CaseItem, PrioritizedCase, Priority, Sentiment

logger = logging.getLogger(__name__)

MODEL_NAME = os.getenv(
    "NLP_MODEL_NAME", "nlptown/bert-base-multilingual-uncased-sentiment"
)

# Terminos que denotan urgencia real en un caso de soporte, en espanol e ingles.
URGENCY_TERMS = re.compile(
    r"\b("
    r"urgent\w*|urgente\w*|inmediat\w*|ya|cuanto antes|"
    r"reembols\w*|devoluc\w*|refund\w*|"
    r"cancel\w*|"
    r"fraud\w*|estaf\w*|rob\w*|"
    r"roto|rota|dana\w*|danad\w*|defectuos\w*|broken|damaged|"
    r"no funciona|no llego|no lleg\w*|nunca lleg\w*|perdid\w*|extravi\w*|"
    r"demanda|abogad\w*|legal|"
    r"queja|reclam\w*|denunc\w*"
    r")\b",
    re.IGNORECASE,
)

_pipeline = None
_load_failed = False
_lock = threading.Lock()


def get_pipeline():
    """
    Carga el modelo la primera vez que se necesita.

    La carga es perezosa para que el contenedor responda al healthcheck sin esperar a la descarga
    del modelo, que la primera vez son varios cientos de megabytes.
    """
    global _pipeline, _load_failed

    if _pipeline is not None or _load_failed:
        return _pipeline

    with _lock:
        if _pipeline is not None or _load_failed:
            return _pipeline
        try:
            from transformers import pipeline

            logger.info("Cargando el modelo %s", MODEL_NAME)
            _pipeline = pipeline(
                "sentiment-analysis", model=MODEL_NAME, truncation=True, max_length=512
            )
            logger.info("Modelo cargado")
        except Exception:
            # Sin modelo el servicio sigue respondiendo, solo que con la heuristica.
            logger.exception("No se pudo cargar el modelo, se usara solo la heuristica")
            _load_failed = True

    return _pipeline


def is_loaded() -> bool:
    return _pipeline is not None


def _sentiment_of(label: str) -> tuple[Sentiment, float]:
    """
    Traduce la etiqueta del modelo a un sentimiento y a una urgencia de 0 a 1.

    El modelo por defecto devuelve estrellas ("1 star".."5 stars"). Tambien se aceptan las
    etiquetas POSITIVE/NEGATIVE por si se configura otro modelo.
    """
    normalized = label.strip().upper()

    stars = re.match(r"(\d)\s*STAR", normalized)
    if stars:
        value = int(stars.group(1))
        # 1 estrella es el cliente mas descontento y por tanto el mas urgente.
        urgency = (5 - value) / 4
        if value <= 2:
            return Sentiment.NEGATIVE, urgency
        if value == 3:
            return Sentiment.NEUTRAL, urgency
        return Sentiment.POSITIVE, urgency

    if "NEG" in normalized:
        return Sentiment.NEGATIVE, 1.0
    if "POS" in normalized:
        return Sentiment.POSITIVE, 0.0
    return Sentiment.NEUTRAL, 0.5


def _deadline_pressure(item: CaseItem, now: datetime) -> float:
    """Presion por vencimiento: 0 si falta mucho, 1 si ya vencio."""
    if not item.due_date:
        return 0.0

    due = item.due_date
    if due.tzinfo is None:
        due = due.replace(tzinfo=timezone.utc)

    hours_left = (due - now).total_seconds() / 3600
    if hours_left <= 0:
        return 1.0
    if hours_left >= 72:
        return 0.0
    return 1.0 - (hours_left / 72)


def _age_pressure(item: CaseItem, now: datetime) -> float:
    """Presion por antiguedad: un caso sin responder se vuelve urgente por si solo."""
    if not item.created_at:
        return 0.0

    created = item.created_at
    if created.tzinfo is None:
        created = created.replace(tzinfo=timezone.utc)

    days_open = (now - created).total_seconds() / 86400
    return min(max(days_open, 0) / 7, 1.0)


def prioritize(items: list[CaseItem]) -> tuple[list[PrioritizedCase], bool]:
    """Devuelve los casos clasificados y si hubo que degradar a la heuristica."""
    if not items:
        return [], False

    now = datetime.now(timezone.utc)
    pipe = get_pipeline()
    degraded = pipe is None

    sentiments: list[tuple[Sentiment, float]] = []
    if pipe is not None:
        try:
            predictions = pipe([item.text[:512] for item in items])
            sentiments = [_sentiment_of(p["label"]) for p in predictions]
        except Exception:
            logger.exception("Fallo la inferencia, se continua con la heuristica")
            degraded = True

    if not sentiments:
        sentiments = [(Sentiment.NEUTRAL, 0.5)] * len(items)

    results: list[PrioritizedCase] = []
    for item, (sentiment, tone_urgency) in zip(items, sentiments):
        keyword_hit = bool(URGENCY_TERMS.search(item.text))
        deadline = _deadline_pressure(item, now)
        age = _age_pressure(item, now)

        score = (
            0.40 * tone_urgency
            + 0.30 * deadline
            + 0.15 * age
            + 0.15 * (1.0 if keyword_hit else 0.0)
        )
        score = round(min(max(score, 0.0), 1.0), 4)

        if score >= 0.65:
            priority = Priority.HIGH
        elif score >= 0.35:
            priority = Priority.MEDIUM
        else:
            priority = Priority.LOW

        reasons = []
        if sentiment == Sentiment.NEGATIVE:
            reasons.append("tono negativo")
        if keyword_hit:
            reasons.append("menciona un asunto critico")
        if deadline >= 0.8:
            reasons.append("vencimiento inminente")
        elif deadline > 0:
            reasons.append("con fecha de vencimiento proxima")
        if age >= 0.7:
            reasons.append("lleva varios dias abierto")
        if not reasons:
            reasons.append("sin senales de urgencia")

        results.append(
            PrioritizedCase(
                id=item.id,
                priority=priority,
                score=score,
                sentiment=sentiment,
                reason=", ".join(reasons).capitalize(),
            )
        )

    results.sort(key=lambda case: case.score, reverse=True)
    return results, degraded
