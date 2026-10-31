set -euo pipefail
umask 077
cd services/notification-bot
ruff check .
ruff format --check .
pytest tests/ -v --junitxml=junit.xml
