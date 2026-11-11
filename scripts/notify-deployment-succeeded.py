"""Submit one successful deployment alert over the private Compose network."""

import http.client
import json
import re
import sys


def main(sha):
    token = sys.stdin.read(1024).strip()
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not token or "\n" in token or "\r" in token:
        return 1
    body = {
        "alerts": [
            {
                "status": "succeeded",
                "labels": {"alertname": "DeploymentSucceeded", "severity": "info"},
                "annotations": {"description": f"Версия {sha}"},
            }
        ],
    }
    connection = http.client.HTTPConnection("notification-web", 9094, timeout=5)
    try:
        connection.request(
            "POST",
            "/internal/alert",
            body=json.dumps(body),
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        )
        response = connection.getresponse()
        return 0 if response.status == 200 and json.loads(response.read(1024)) == {"published": 1} else 1
    except (OSError, ValueError, http.client.HTTPException):
        return 1
    finally:
        connection.close()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
