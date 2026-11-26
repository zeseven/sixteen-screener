FROM python:3.14-slim AS build

COPY --from=ghcr.io/astral-sh/uv:0.13.10 /uv /bin/uv

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never

WORKDIR /app

COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --frozen --no-dev --no-install-project

COPY src ./src
RUN uv sync --frozen --no-dev --no-editable


FROM python:3.14-slim

RUN useradd --system --uid 10001 --home-dir /app app

COPY --from=build --chown=app:app /app/.venv /app/.venv

ENV PATH="/app/.venv/bin:${PATH}" \
    PYTHONUNBUFFERED=1 \
    YFINANCE_CACHE_DIR=/tmp/yfinance

USER app
WORKDIR /app
EXPOSE 8080

CMD ["python", "-m", "yahoo_proxy"]
