FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && \
    useradd -r -s /bin/false app

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN chown -R app:app /app

USER app

CMD ["python", "-m", "bot"]
# Note: python -m bot invokes bot/__main__.py as asyncio entry point
