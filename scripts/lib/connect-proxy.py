"""A minimal HTTP CONNECT proxy, used by scripts/device-net-via-mac.sh.

Its only job is to let a USB-attached phone borrow this Mac's network — and
with it the WireGuard tunnel the dev tier's WAF allows. It forwards bytes and
nothing else: no TLS interception, no certificate to install, nothing written
to disk. Bearer tokens crossing it are as opaque to it as they are to a switch.

Python's standard library only, so there is nothing to install on a machine
that is already running the build.
"""

import asyncio
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8899
CONNECT_TIMEOUT = 20


async def pipe(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            writer.write(data)
            await writer.drain()
    except Exception:
        # A half-closed tunnel is the normal way these end; the peer's own pipe
        # task reports anything that matters.
        pass
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def handle(client_reader: asyncio.StreamReader, client_writer: asyncio.StreamWriter) -> None:
    try:
        request_line = await asyncio.wait_for(client_reader.readline(), timeout=CONNECT_TIMEOUT)
    except asyncio.TimeoutError:
        client_writer.close()
        return
    if not request_line:
        client_writer.close()
        return

    parts = request_line.decode("latin-1").split()
    if len(parts) < 3:
        client_writer.close()
        return
    method, target = parts[0], parts[1]

    headers = []
    while True:
        line = await client_reader.readline()
        if line in (b"\r\n", b"\n", b""):
            break
        headers.append(line)

    if method.upper() == "CONNECT":
        host, _, port = target.partition(":")
        port = int(port or 443)
        try:
            remote_reader, remote_writer = await asyncio.wait_for(
                asyncio.open_connection(host, port), timeout=CONNECT_TIMEOUT
            )
        except Exception:
            client_writer.write(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
            await client_writer.drain()
            client_writer.close()
            return
        client_writer.write(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        await client_writer.drain()
        print(f"CONNECT {host}:{port}", flush=True)
        await asyncio.gather(
            pipe(client_reader, remote_writer),
            pipe(remote_reader, client_writer),
        )
        return

    # Plain HTTP arrives in absolute form (`GET http://host/path`); rewrite it to
    # origin form and forward. Android sends captive-portal probes this way.
    _, _, rest = target.partition("://")
    hostport, _, path = rest.partition("/")
    host, _, port = hostport.partition(":")
    port = int(port or 80)
    try:
        remote_reader, remote_writer = await asyncio.open_connection(host, port)
    except Exception:
        client_writer.close()
        return
    remote_writer.write(f"{method} /{path} HTTP/1.1\r\n".encode("latin-1"))
    for header in headers:
        remote_writer.write(header)
    remote_writer.write(b"\r\n")
    await remote_writer.drain()
    print(f"{method} {target}", flush=True)
    await asyncio.gather(
        pipe(client_reader, remote_writer),
        pipe(remote_reader, client_writer),
    )


async def main() -> None:
    server = await asyncio.start_server(handle, "127.0.0.1", PORT)
    print(f"listening on 127.0.0.1:{PORT}", flush=True)
    async with server:
        await server.serve_forever()


try:
    asyncio.run(main())
except KeyboardInterrupt:
    pass
