import logging
import os
from ldobserve import ObservabilityConfig, ObservabilityPlugin, observe
import ldclient
from ldclient.context import Context
from ldclient.config import Config
from loguru import logger
from opentelemetry import trace

sdk_key = os.getenv("LAUNCHDARKLY_SDK_KEY")

if not sdk_key:
    raise ValueError("LAUNCHDARKLY_SDK_KEY is not set")


ldclient.set_config(Config(sdk_key,
plugins=[
    ObservabilityPlugin(
        ObservabilityConfig(
            service_name="ryan-flask-app",
            service_version="1.0.0",
        )
    )]))

# Import flask after instantiating the plugin.
from flask import Flask

app = Flask(__name__)

client = ldclient.get()

@app.route('/')
def hello():
    return "Hello, World!"

@app.route('/crash')
def crash():
    raise Exception("crash")

@app.route('/manual-span')
def manual_span():
    with observe.start_span("manual-span", attributes={"custom": "value"}) as span:
        client.variation("test", Context.create("bob"), default=False)
        span.set_attribute("my-attribute", "my-value")
        # Any user defined code I want to capture.
        return 'Manual span'

@app.route('/manual-record-exception')
def manual_record_exception():
    try:
        raise Exception("manual-record-exception")
    except Exception as e:
        observe.record_exception(e)
        return 'Manual record exception'

@app.route('/raise-exception')
def raise_exception():
    raise Exception("uncaught exception")

@app.route('/manual-record-log')
def manual_record_log():
    observe.record_log("manual-record-log", logging.INFO, {"custom": "value"})
    return "Manually recorded log"

@app.route('/record-metrics')
def record_metrics():
    observe.record_metric("manual-record-metric", 1)
    observe.record_count("manual-record-counter", 1)
    observe.record_histogram("manual-record-histogram", 1)
    observe.record_up_down_counter("manual-record-uptime", 1)
    return "Manually recorded metric"
