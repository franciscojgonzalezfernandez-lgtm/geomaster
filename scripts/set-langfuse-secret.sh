#!/usr/bin/env bash
# Stores the Langfuse OTLP auth header as a Fly secret for geomaster-core (ADR-0012, scan-engine T11).
# Keys are read without echo and never printed; the header value is percent-encoded because the
# OpenTelemetry SDK URL-decodes OTEL_EXPORTER_OTLP_HEADERS (a raw "+" in base64 would become a space).
# The secret is staged: it applies on the next deploy (the one that sets OTEL_SDK_DISABLED=false).
set -euo pipefail

read -rsp "Langfuse public key (pk-lf-…): " PK; echo
read -rsp "Langfuse secret key (sk-lf-…): " SK; echo
[[ "$PK" == pk-lf-* && "$SK" == sk-lf-* ]] || { echo "Keys must start with pk-lf- and sk-lf-." >&2; exit 1; }

HEADER=$(PK="$PK" SK="$SK" python3 -c '
import base64, os, urllib.parse
token = base64.b64encode((os.environ.get("PK") + ":" + os.environ.get("SK")).encode()).decode()
print("Authorization=" + urllib.parse.quote("Basic " + token, safe=""))
')
unset PK SK

fly secrets set --stage -a geomaster-core "OTEL_EXPORTER_OTLP_HEADERS=$HEADER" >/dev/null
unset HEADER
echo "OTEL_EXPORTER_OTLP_HEADERS staged on geomaster-core. It applies on the next deploy."
