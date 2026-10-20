#!/usr/bin/env python3
"""Runs the Spring Boot backend under the OpenTelemetry Java agent and checks
that flag evaluations still reach the plugin's OTLP exporter.

The agent owns GlobalOpenTelemetry and ignores the plugin's attempt to
register its SDK as the global. A local collector stands in for LaunchDarkly; the agent's own
exporters are switched off so everything it receives came from the plugin.

Usage: javaagent_smoke.py <app.jar> <opentelemetry-javaagent.jar>
"""

import gzip
import http.server
import os
import signal
import socket
import struct
import subprocess
import sys
import threading
import time
import urllib.request

LOOPBACK = socket.inet_ntoa(struct.pack("!I", socket.INADDR_LOOPBACK))
TRACES = []
SAMPLING_QUERIES = []


class Collector(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if "gzip" in (self.headers.get("Content-Encoding") or ""):
            body = gzip.decompress(body)
        if self.path.startswith("/v1/traces"):
            TRACES.append(body)
        elif self.path.startswith("/sampling"):
            SAMPLING_QUERIES.append(body)
        reply = b'{"data":{"sampling":{"spans":[],"logs":[]}}}' if self.path.startswith("/sampling") else b""
        self.send_response(200)
        self.send_header("Content-Length", str(len(reply)))
        self.end_headers()
        self.wfile.write(reply)

    def log_message(self, *args):
        pass


def free_port():
    with socket.socket() as s:
        s.bind((LOOPBACK, 0))
        return s.getsockname()[1]


def get(url):
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            return response.status
    except urllib.error.HTTPError as e:
        return e.code
    except OSError:
        return None


def traces_contain(*needles):
    return any(all(n in payload for n in needles) for payload in list(TRACES))


def main():
    app_jar, agent_jar = sys.argv[1], sys.argv[2]
    collector = http.server.ThreadingHTTPServer((LOOPBACK, 0), Collector)
    threading.Thread(target=collector.serve_forever, daemon=True).start()
    collector_url = "http://%s:%d" % (LOOPBACK, collector.server_address[1])
    app_url = "http://%s:%d" % (LOOPBACK, free_port())

    env = dict(os.environ)
    env.pop("LAUNCHDARKLY_SDK_KEY", None)
    env.update({
        "PORT": app_url.rsplit(":", 1)[1],
        "OTEL_EXPORTER_OTLP_ENDPOINT": collector_url,
        "LD_OBSERVABILITY_BACKEND_URL": collector_url + "/sampling",
    })
    command = [
        "java",
        "-javaagent:" + agent_jar,
        # The agent also reads OTEL_EXPORTER_OTLP_ENDPOINT; keep its own
        # exporters off so only the plugin's data reaches the collector.
        "-Dotel.traces.exporter=none",
        "-Dotel.metrics.exporter=none",
        "-Dotel.logs.exporter=none",
        "-jar", app_jar,
    ]
    log_path = "app-javaagent.log"
    failures = []
    with open(log_path, "wb") as log:
        app = subprocess.Popen(command, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(120):
                if get(app_url + "/actuator/health") == 200:
                    break
                if app.poll() is not None:
                    failures.append("app exited early with %s" % app.returncode)
                    break
                time.sleep(1)
            else:
                failures.append("app did not become healthy")

            if not failures:
                for path in ("/", "/rolldice", "/flags/example-greeting"):
                    if get(app_url + path) != 200:
                        failures.append("GET %s did not answer 200" % path)
                if get(app_url + "/error") != 500:
                    failures.append("GET /error did not answer 500")

                # BatchSpanProcessor exports every second.
                deadline = time.time() + 20
                while time.time() < deadline and not (
                    traces_contain(b"LDClient.boolVariation", b"example-greeting")
                    and traces_contain(b"LDClient.intVariation", b"dice-sides")
                    and traces_contain(b"LDClient.jsonValueVariation", b"example-greeting")
                ):
                    time.sleep(0.5)
                for method, flag in (
                    (b"LDClient.boolVariation", b"example-greeting"),
                    (b"LDClient.intVariation", b"dice-sides"),
                    (b"LDClient.jsonValueVariation", b"example-greeting"),
                ):
                    if not traces_contain(method, flag):
                        failures.append("evaluation span %s for %s did not reach the plugin exporter"
                                        % (method.decode(), flag.decode()))
                if not traces_contain(b"feature_flag", b"GET /rolldice"):
                    failures.append("request span with a feature_flag event did not reach the plugin exporter")
                if not SAMPLING_QUERIES:
                    failures.append("plugin did not fetch sampling config")
        finally:
            if app.poll() is None:
                app.send_signal(signal.SIGTERM)
                try:
                    app.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    app.kill()
                    failures.append("app did not stop on SIGTERM")
            collector.shutdown()

    with open(log_path, "rb") as log:
        output = log.read()
    # The agent logs its version on startup. It also turns the plugin's
    # GlobalOpenTelemetry.set() into a silent no-op, so the plugin never
    # becomes the global in this run.
    if b"opentelemetry-javaagent - version" not in output:
        failures.append("the OpenTelemetry Java agent did not start")
    if b"Closing LaunchDarkly Client" not in output:
        failures.append("client did not close on shutdown")

    if failures:
        sys.stdout.write(output.decode("utf-8", "replace"))
        for failure in failures:
            print("FAIL " + failure)
        return 1
    print("Spring Boot backend under the Java agent OK: %d trace exports" % len(TRACES))
    return 0


if __name__ == "__main__":
    sys.exit(main())
