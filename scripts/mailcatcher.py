"""
Capturador de correos para desarrollo, sin dependencias externas.

Alternativa a Mailpit cuando no se puede levantar Docker. Hace dos cosas:

  * SMTP en el 1025: acepta los correos del backend y NO los reenvia a internet.
  * Bandeja web en el 8025: permite leerlos, con el enlace de verificacion destacado.

En desarrollo los correos nunca deben salir a servidores reales, pero si tienen que ser
legibles: sin la bandeja, el registro se queda bloqueado esperando un correo que nadie puede abrir.

Uso:  python scripts/mailcatcher.py
      Luego abre http://localhost:8025
"""

from __future__ import annotations

import asyncio
import email
import html
import re
import threading
from datetime import datetime
from email.policy import default as default_policy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

SMTP_HOST, SMTP_PORT = "127.0.0.1", 1025
WEB_HOST, WEB_PORT = "127.0.0.1", 8025
OUTPUT_DIR = Path(__file__).parent / "mails"

LINK_PATTERN = re.compile(r'https?://[^\s"\'<>)]+')

# Mensajes en memoria, del mas reciente al mas antiguo. Se rellena al arrancar con lo que
# ya hubiera en disco, para no perder los correos de una ejecucion anterior.
messages: list[dict] = []
messages_lock = threading.Lock()


# --------------------------------------------------------------------------- almacenamiento


def parse_message(raw: bytes, path: Path, recipients: list[str] | None = None) -> dict:
    parsed = email.message_from_bytes(raw, policy=default_policy)
    body = parsed.get_body(preferencelist=("html", "plain"))
    content = body.get_content() if body else ""

    return {
        "id": path.stem,
        "to": ", ".join(recipients) if recipients else (parsed.get("To") or "(desconocido)"),
        "subject": parsed.get("Subject") or "(sin asunto)",
        "date": datetime.fromtimestamp(path.stat().st_mtime),
        "html": content if body and body.get_content_type() == "text/html" else None,
        "text": None if body and body.get_content_type() == "text/html" else content,
        "links": sorted(set(LINK_PATTERN.findall(content))),
        "path": path,
    }


def load_existing() -> None:
    OUTPUT_DIR.mkdir(exist_ok=True)
    for path in sorted(OUTPUT_DIR.glob("*.eml")):
        try:
            messages.insert(0, parse_message(path.read_bytes(), path))
        except Exception:
            continue


def store(raw: bytes, recipients: list[str]) -> dict:
    OUTPUT_DIR.mkdir(exist_ok=True)
    path = OUTPUT_DIR / f"{datetime.now().strftime('%Y%m%d-%H%M%S-%f')}.eml"
    path.write_bytes(raw)

    record = parse_message(raw, path, recipients)
    with messages_lock:
        messages.insert(0, record)
    return record


# --------------------------------------------------------------------------- SMTP


class SmtpSession(asyncio.Protocol):
    """Implementacion minima de SMTP: solo lo necesario para aceptar y volcar un mensaje."""

    def __init__(self) -> None:
        self.transport: asyncio.Transport | None = None
        self.buffer = b""
        self.in_data = False
        self.data = b""
        self.recipients: list[str] = []

    def connection_made(self, transport: asyncio.BaseTransport) -> None:
        self.transport = transport  # type: ignore[assignment]
        self._send("220 mailcatcher listo")

    def _send(self, line: str) -> None:
        if self.transport:
            self.transport.write(line.encode() + b"\r\n")

    def data_received(self, chunk: bytes) -> None:
        self.buffer += chunk

        while b"\r\n" in self.buffer:
            line, self.buffer = self.buffer.split(b"\r\n", 1)

            if self.in_data:
                if line == b".":
                    self.in_data = False
                    self._finish()
                    self._send("250 Mensaje aceptado")
                else:
                    # Deshacer el punto inicial duplicado que exige el protocolo.
                    self.data += (line[1:] if line.startswith(b"..") else line) + b"\r\n"
                continue

            self._handle_command(line.decode("utf-8", errors="replace"))

    def _handle_command(self, line: str) -> None:
        command = line.split(" ", 1)[0].upper()

        if command in ("EHLO", "HELO"):
            self._send("250-mailcatcher")
            self._send("250 8BITMIME")
        elif command == "MAIL":
            self._send("250 Remitente aceptado")
        elif command == "RCPT":
            match = re.search(r"<([^>]*)>", line)
            if match:
                self.recipients.append(match.group(1))
            self._send("250 Destinatario aceptado")
        elif command == "DATA":
            self.in_data = True
            self.data = b""
            self._send("354 Escribe el mensaje y termina con un punto")
        elif command == "RSET":
            self.data = b""
            self.recipients = []
            self._send("250 Reiniciado")
        elif command == "QUIT":
            self._send("221 Adios")
            if self.transport:
                self.transport.close()
        else:
            self._send("250 Ok")

    def _finish(self) -> None:
        record = store(self.data, self.recipients)

        print("\n" + "=" * 78)
        print(f"Para    : {record['to']}")
        print(f"Asunto  : {record['subject']}")
        for link in record["links"]:
            print(f"Enlace  : {link}")
        print(f"Bandeja : http://{WEB_HOST}:{WEB_PORT}")
        print("=" * 78, flush=True)


# --------------------------------------------------------------------------- bandeja web

PAGE = """<!doctype html>
<html lang="es"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Bandeja de desarrollo</title>
<style>
  :root {{
    --bg:#f7f4ef; --surface:#fff; --ink:#1a1a1a; --muted:#6b6862;
    --line:#e3ddd4; --accent:#a8b5a0; --soft:#e8ded3;
  }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--ink);
         font:16px/1.6 Inter,'Helvetica Neue',Helvetica,Arial,sans-serif; }}
  header {{ border-bottom:1px solid var(--line); padding:20px 32px;
            display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:12px; }}
  .eyebrow {{ font-size:12px; letter-spacing:.18em; text-transform:uppercase; color:var(--muted); }}
  .wrap {{ display:grid; grid-template-columns:340px 1fr; min-height:calc(100vh - 65px); }}
  @media (max-width:820px) {{ .wrap {{ grid-template-columns:1fr; }} }}
  .list {{ border-right:1px solid var(--line); overflow-y:auto; }}
  .item {{ display:block; padding:16px 24px; border-bottom:1px solid var(--line); text-decoration:none; color:inherit; }}
  .item:hover {{ background:var(--soft); }}
  .item.active {{ background:var(--surface); border-left:3px solid var(--accent); }}
  .item .to {{ font-size:12px; color:var(--muted); }}
  .item .subj {{ font-size:14px; font-weight:500; margin-top:4px; }}
  .item .date {{ font-size:12px; color:var(--muted); margin-top:4px; }}
  .view {{ padding:32px; overflow-y:auto; }}
  .cta {{ background:var(--soft); border:1px solid var(--accent); padding:20px 24px; margin-bottom:24px; }}
  .cta a {{ display:inline-block; margin-top:10px; padding:12px 24px; background:var(--ink);
            color:#fff; text-decoration:none; font-size:12px; letter-spacing:.14em; text-transform:uppercase; }}
  .cta code {{ display:block; margin-top:10px; font-size:12px; color:var(--muted); word-break:break-all; }}
  iframe {{ width:100%; height:640px; border:1px solid var(--line); background:#fff; }}
  pre {{ white-space:pre-wrap; background:var(--surface); border:1px solid var(--line); padding:20px; }}
  .empty {{ color:var(--muted); font-size:14px; padding:32px; }}
</style></head><body>
<header>
  <div>
    <div class="eyebrow">Capturador de correos &middot; desarrollo</div>
    <div style="font-size:13px;color:var(--muted);margin-top:4px;">
      SMTP {smtp} &middot; ningun correo sale a internet
    </div>
  </div>
  <div class="eyebrow">{count} mensaje(s)</div>
</header>
<div class="wrap">
  <nav class="list">{list_html}</nav>
  <section class="view">{view_html}</section>
</div>
<script>
  // Refresco discreto para ver llegar los correos sin recargar a mano.
  setTimeout(() => location.reload(), 5000);
</script>
</body></html>"""


class InboxHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802  (nombre impuesto por BaseHTTPRequestHandler)
        with messages_lock:
            snapshot = list(messages)

        if self.path.startswith("/raw/"):
            self._serve_raw(self.path[5:], snapshot)
            return

        selected = None
        if self.path.startswith("/m/"):
            wanted = self.path[3:]
            selected = next((m for m in snapshot if m["id"] == wanted), None)
        if selected is None and snapshot:
            selected = snapshot[0]

        self._respond(200, "text/html", self._render(snapshot, selected).encode("utf-8"))

    def _serve_raw(self, message_id: str, snapshot: list[dict]) -> None:
        record = next((m for m in snapshot if m["id"] == message_id), None)
        if record is None:
            self._respond(404, "text/plain", b"No encontrado")
            return
        body = record["html"] or f"<pre>{html.escape(record['text'] or '')}</pre>"
        self._respond(200, "text/html", body.encode("utf-8"))

    def _render(self, snapshot: list[dict], selected: dict | None) -> str:
        if not snapshot:
            return PAGE.format(
                smtp=f"{SMTP_HOST}:{SMTP_PORT}",
                count=0,
                list_html='<p class="empty">Sin correos todavia.</p>',
                view_html='<p class="empty">Registra un usuario en la aplicacion y el correo '
                          "de verificacion aparecera aqui.</p>",
            )

        items = []
        for record in snapshot:
            active = " active" if selected and record["id"] == selected["id"] else ""
            items.append(
                f'<a class="item{active}" href="/m/{record["id"]}">'
                f'<div class="to">{html.escape(record["to"])}</div>'
                f'<div class="subj">{html.escape(record["subject"])}</div>'
                f'<div class="date">{record["date"].strftime("%d/%m/%Y %H:%M:%S")}</div>'
                f"</a>"
            )

        return PAGE.format(
            smtp=f"{SMTP_HOST}:{SMTP_PORT}",
            count=len(snapshot),
            list_html="".join(items),
            view_html=self._render_message(selected) if selected else "",
        )

    def _render_message(self, record: dict) -> str:
        parts = [
            f'<div class="eyebrow">Para {html.escape(record["to"])}</div>',
            f'<h1 style="font-weight:400;margin:12px 0 24px;">{html.escape(record["subject"])}</h1>',
        ]

        # Lo que de verdad hace falta en desarrollo: el enlace, a un clic.
        for link in record["links"]:
            parts.append(
                '<div class="cta">'
                '<div class="eyebrow">Enlace del correo</div>'
                f'<a href="{html.escape(link, quote=True)}" target="_blank" rel="noopener">Abrir enlace</a>'
                f'<code>{html.escape(link)}</code>'
                "</div>"
            )

        parts.append(f'<iframe src="/raw/{record["id"]}" title="Contenido del correo"></iframe>')
        return "".join(parts)

    def _respond(self, status: int, content_type: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", f"{content_type}; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args) -> None:
        """Silencia el log de acceso: la consola es para los correos, no para el trafico web."""


# --------------------------------------------------------------------------- arranque


def start_web_server() -> None:
    server = ThreadingHTTPServer((WEB_HOST, WEB_PORT), InboxHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()


async def main() -> None:
    load_existing()
    start_web_server()

    loop = asyncio.get_running_loop()
    server = await loop.create_server(SmtpSession, SMTP_HOST, SMTP_PORT)

    print(f"SMTP    escuchando en {SMTP_HOST}:{SMTP_PORT}")
    print(f"Bandeja abierta en    http://{WEB_HOST}:{WEB_PORT}")
    print(f"Mensajes guardados en {OUTPUT_DIR}")
    print(f"Cargados {len(messages)} correo(s) de ejecuciones anteriores.")
    print("Ctrl+C para detener.\n", flush=True)

    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nDetenido.")
