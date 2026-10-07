"""Stand-in for the OpenRouter chat completions API, used by scripts/smoke-test.sh.

One process serves every case; the behaviour is picked by a marker in the prompt:
  "smoke-error"  responds with HTTP 500
  "smoke-slow"   responds after SLOW_SECONDS
  anything else  responds at once with 15 cards

POST requests are counted. GET on any path returns the count, so the smoke test
can tell whether the application called the model or answered from its database.
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SLOW_SECONDS = 6
CARDS = "\n".join(f"Q: Question {i}?\nA: Answer {i}." for i in range(1, 16))

completions_requested = 0


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self._send(200, {"requests": completions_requested})

    def do_POST(self):
        global completions_requested
        completions_requested += 1
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        prompt = body["messages"][-1]["content"]
        if "smoke-error" in prompt:
            self._send(500, {"error": {"message": "simulated upstream failure"}})
            return
        if "smoke-slow" in prompt:
            time.sleep(SLOW_SECONDS)
        self._send(200, {"choices": [{"message": {"role": "assistant", "content": CARDS}}]})

    def _send(self, status, payload):
        data = json.dumps(payload).encode()
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        except ConnectionError:
            pass  # the application gave up waiting, which is what the slow case is for

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
