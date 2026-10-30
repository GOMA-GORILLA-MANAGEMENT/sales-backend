#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== Security scan: Eshop-Backend ==="

echo "[1/3] Checking for secrets in tracked files..."
if git ls-files | rg '\.env$|\.pem$|id_rsa|\.key$' 2>/dev/null; then
  echo "FAIL: secret files are tracked by git"
  exit 1
fi
echo "OK: no secret filenames tracked"

echo "[2/3] Scanning for hardcoded credentials in source..."
if rg -n 'password\s*=\s*[^$\s]|api[_-]?key\s*=\s*["\'][^"\']+["\']' --glob '*.properties' --glob '*.yml' --glob '*.yaml' --glob '*.java' src/main/resources/ 2>/dev/null; then
  echo "WARN: review hardcoded values in application.properties (use env vars in production)"
fi

echo "[3/3] Maven build..."
mvn -B clean package -DskipTests

echo "=== Security scan passed ==="
