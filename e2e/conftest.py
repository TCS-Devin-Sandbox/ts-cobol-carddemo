"""Fixtures for the rules-INTCALC.html board suite (see ui-test-plan.md)."""
import functools
import http.server
import threading
from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parents[1]
RULES_DIR = ROOT / "modernization" / "rules" / "interest"


@pytest.fixture(scope="session")
def rules():
    return yaml.safe_load((RULES_DIR / "rules.yaml").read_text())["rules"]


@pytest.fixture(scope="session")
def board_url():
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(RULES_DIR))
    handler.log_message = lambda *a, **k: None
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{srv.server_address[1]}/rules-INTCALC.html"
    srv.shutdown()


@pytest.fixture
def browser_context_args(browser_context_args):
    return {**browser_context_args, "viewport": {"width": 1280, "height": 720}, "accept_downloads": True}


@pytest.fixture
def board(page, board_url):
    errors = []
    page.on("console", lambda m: errors.append(m.text) if m.type in ("error", "warning") else None)
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(board_url)
    page.errors = errors
    return page
