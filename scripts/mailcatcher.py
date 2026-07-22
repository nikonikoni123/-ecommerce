"""
Capturador de correos para desarrollo, sin dependencias externas.

Alternativa a Mailpit cuando no se quiere levantar Docker. Escucha SMTP en el 1025, no reenvia
nada a ningun sitio, y guarda cada mensaje en scripts/mails/ ademas de imprimir en consola el
asunto, el destinatario y los enlaces que contiene, que es lo que hace falta para probar la
verificacion de correo.

Uso:  python scripts/mailcatcher.py
"""

from __future__ import annotations

import asyncio
import email
import re
from datetime import datetime
from email.policy import default as default_policy
from pathlib import Path

HOST = "127.0.0.1"
PORT = 1025
OUTPUT_DIR = Path(__file__).parent / "mails"

LINK_PATTERN = re.compile(r'https?://[^\s"\'<>]+')


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
                    self._store()
                    self._send("250 Mensaje aceptado")
                else:
                    # Deshacer el punto inicial duplicado que exige el protocolo.
                    self.data += (line[1:] if line.startswith(b"..") else line) + b"\r\n"
                continue

            self._handle_command(line.decode("utf-8", errors="replace"))

    def _handle_command(self, line: str) -> None:
        command = line.split(" ", 1)[0].upper()

        if command == "EHLO" or command == "HELO":
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
        elif command == "NOOP":
            self._send("250 Ok")
        else:
            self._send("250 Ok")

    def _store(self) -> None:
        message = email.message_from_bytes(self.data, policy=default_policy)
        subject = message.get("Subject", "(sin asunto)")
        to = ", ".join(self.recipients) or message.get("To", "(sin destinatario)")

        OUTPUT_DIR.mkdir(exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        path = OUTPUT_DIR / f"{stamp}.eml"
        path.write_bytes(self.data)

        body = message.get_body(preferencelist=("html", "plain"))
        content = body.get_content() if body else ""
        links = sorted(set(LINK_PATTERN.findall(content)))

        print("\n" + "=" * 78)
        print(f"Para    : {to}")
        print(f"Asunto  : {subject}")
        print(f"Guardado: {path}")
        for link in links:
            print(f"Enlace  : {link}")
        print("=" * 78, flush=True)


async def main() -> None:
    loop = asyncio.get_running_loop()
    server = await loop.create_server(SmtpSession, HOST, PORT)
    print(f"Capturador de correos escuchando en {HOST}:{PORT}")
    print(f"Los mensajes se guardan en {OUTPUT_DIR}")
    print("Ctrl+C para detener.\n", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nDetenido.")
