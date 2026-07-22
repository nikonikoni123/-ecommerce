FROM python:3.11-slim

WORKDIR /app

# torch en CPU: la imagen oficial de PyPI arrastra las librerias de CUDA y multiplica el tamano.
ENV PIP_EXTRA_INDEX_URL=https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Cache del modelo. En docker-compose se monta como volumen para descargarlo una sola vez.
ENV HF_HOME=/models
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
