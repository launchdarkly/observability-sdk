# Highlight OpenTelemetry Python Example

This directory contains a **native** OpenTelemetry tracing example that sends spans
via OTLP/gRPC to LaunchDarkly Observability.

The exporter attaches an `x-highlight-project` header so the backend can
associate traces with your Highlight project.

## Prerequisites

* Python ≥ 3.10
* A Highlight project ID. Export it as an environment variable:

```bash
export HIGHLIGHT_PROJECT_ID="<your-project-id>"
```

## Install dependencies

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Run the example

```bash
python main.py
```

After the script finishes, you should see a span named `example-operation`
in your Highlight dashboard. 