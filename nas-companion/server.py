#!/usr/bin/env python3
"""
Atom2Universe NAS Companion
Nœud de synchronisation P2P pour NAS/serveur domestique.

Endpoints exposés (même protocole que l'app Android) :
  GET /info              → {"deviceId":"...", "latestEventAt":T, "latestLyricsAt":T}
  GET /events?since=T    → JSON array de listen_events
  GET /lyrics?since=T    → JSON array de SyncLyricsEntry

Le service se découvre automatiquement avec tous les appareils Atom2Universe
sur le LAN via mDNS (_a2u._tcp), sans configuration.
"""

import json
import logging
import os
import socket
import sqlite3
import threading
import time
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

from zeroconf import ServiceBrowser, ServiceInfo, Zeroconf

# ── Configuration ─────────────────────────────────────────────────────────────

PORT             = int(os.environ.get("PORT", 47123))
DB_PATH          = os.environ.get("DB_PATH", "/data/a2u.db")
RESYNC_INTERVAL  = int(os.environ.get("RESYNC_INTERVAL", 18000))  # secondes
SCAN_DURATION    = int(os.environ.get("SCAN_DURATION", 15))       # secondes d'écoute par scan mDNS
MAX_EVENTS       = 5000
_svc_name        = os.environ.get("SERVICE_NAME", "a2u")
SERVICE_TYPE     = f"_{_svc_name}._tcp.local."

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s  %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("a2u-nas")

DEVICE_ID: str = ""   # initialisé dans main()


# ── Base de données ───────────────────────────────────────────────────────────

def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn

# Connexion partagée avec verrou (accès multi-thread)
_db_lock = threading.Lock()
_db_conn: sqlite3.Connection | None = None

def db() -> sqlite3.Connection:
    global _db_conn
    if _db_conn is None:
        _db_conn = get_db()
    return _db_conn

def db_exec(sql: str, params=()):
    with _db_lock:
        return db().execute(sql, params)

def db_exec_many(sql: str, rows):
    with _db_lock:
        db().executemany(sql, rows)
        db().commit()

def db_commit():
    with _db_lock:
        db().commit()


def init_db():
    with _db_lock:
        db().executescript("""
            CREATE TABLE IF NOT EXISTS listen_events (
                uuid              TEXT PRIMARY KEY,
                trackKey          TEXT NOT NULL,
                deviceId          TEXT NOT NULL,
                listenedAt        INTEGER NOT NULL,
                durationListenedMs INTEGER NOT NULL DEFAULT 0,
                trackDurationMs   INTEGER NOT NULL DEFAULT 0,
                title             TEXT NOT NULL DEFAULT '',
                artist            TEXT NOT NULL DEFAULT '',
                album             TEXT NOT NULL DEFAULT '',
                isMigrated        INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_ev_track     ON listen_events(trackKey);
            CREATE INDEX IF NOT EXISTS idx_ev_device    ON listen_events(deviceId);
            CREATE INDEX IF NOT EXISTS idx_ev_listened  ON listen_events(listenedAt);

            CREATE TABLE IF NOT EXISTS lyrics_cache (
                metadataKey TEXT PRIMARY KEY,
                title       TEXT NOT NULL DEFAULT '',
                artist      TEXT NOT NULL DEFAULT '',
                album       TEXT NOT NULL DEFAULT '',
                lyrics      TEXT NOT NULL DEFAULT '',
                source      TEXT NOT NULL DEFAULT 'lan_sync',
                isSynced    INTEGER NOT NULL DEFAULT 0,
                modifiedAt  INTEGER NOT NULL DEFAULT 0,
                deletedAt   INTEGER
            );

            CREATE TABLE IF NOT EXISTS device_info (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
        """)
        db().commit()


def load_device_id() -> str:
    with _db_lock:
        row = db().execute("SELECT value FROM device_info WHERE key='deviceId'").fetchone()
        if row:
            return row["value"]
        new_id = str(uuid.uuid4())
        db().execute("INSERT INTO device_info VALUES ('deviceId', ?)", (new_id,))
        db().commit()
        return new_id


# ── Requêtes ──────────────────────────────────────────────────────────────────

def latest_event_ts() -> int:
    row = db_exec("SELECT MAX(listenedAt) FROM listen_events").fetchone()
    return row[0] or 0

def latest_lyrics_ts() -> int:
    row = db_exec(
        "SELECT MAX(modifiedAt) FROM lyrics_cache WHERE lyrics != '' AND deletedAt IS NULL"
    ).fetchone()
    return row[0] or 0

def events_since(since: int) -> list[dict]:
    rows = db_exec(
        "SELECT * FROM listen_events WHERE listenedAt >= ? LIMIT ?",
        (since, MAX_EVENTS)
    ).fetchall()
    return [dict(r) for r in rows]

def lyrics_since(since: int) -> list[dict]:
    rows = db_exec(
        "SELECT * FROM lyrics_cache WHERE modifiedAt >= ? AND lyrics != ''",
        (since,)
    ).fetchall()
    # Convertir au format SyncLyricsEntry attendu par Android
    result = []
    for r in rows:
        entry = {
            "key":       r["metadataKey"],
            "title":     r["title"],
            "artist":    r["artist"],
            "album":     r["album"],
            "lyrics":    r["lyrics"],
            "source":    r["source"],
            "isSynced":  bool(r["isSynced"]),
            "modifiedAt": r["modifiedAt"],
        }
        if r["deletedAt"] is not None:
            entry["deletedAt"] = r["deletedAt"]
        result.append(entry)
    return result

def latest_from_peer(device_id: str) -> int:
    row = db_exec(
        "SELECT MAX(listenedAt) FROM listen_events WHERE deviceId=?",
        (device_id,)
    ).fetchone()
    return row[0] or 0

def insert_events(events: list[dict]) -> int:
    if not events:
        return 0
    uuids = [e["uuid"] for e in events]
    placeholders = ",".join("?" * len(uuids))
    with _db_lock:
        existing = {
            r[0] for r in db().execute(
                f"SELECT uuid FROM listen_events WHERE uuid IN ({placeholders})", uuids
            ).fetchall()
        }
        new = [e for e in events if e["uuid"] not in existing]
        db().executemany("""
            INSERT OR IGNORE INTO listen_events
            (uuid, trackKey, deviceId, listenedAt, durationListenedMs,
             trackDurationMs, title, artist, album, isMigrated)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """, [(
            e["uuid"], e.get("trackKey",""), e.get("deviceId",""),
            e.get("listenedAt", 0), e.get("durationListenedMs", 0),
            e.get("trackDurationMs", 0), e.get("title",""),
            e.get("artist",""), e.get("album",""),
            1 if e.get("isMigrated") else 0
        ) for e in new])
        db().commit()
    return len(new)

def merge_lyrics(entries: list[dict]) -> int:
    if not entries:
        return 0
    count = 0
    with _db_lock:
        for e in entries:
            key = e.get("key", "")
            peer_ts = e.get("modifiedAt", 0)
            row = db().execute(
                "SELECT modifiedAt FROM lyrics_cache WHERE metadataKey=?", (key,)
            ).fetchone()
            local_ts = row["modifiedAt"] if row else 0
            if peer_ts > local_ts:
                db().execute("""
                    INSERT OR REPLACE INTO lyrics_cache
                    (metadataKey, title, artist, album, lyrics, source, isSynced, modifiedAt, deletedAt)
                    VALUES (?,?,?,?,?,?,?,?,?)
                """, (
                    key, e.get("title",""), e.get("artist",""), e.get("album",""),
                    e.get("lyrics",""), e.get("source","lan_sync"),
                    1 if e.get("isSynced") else 0,
                    peer_ts, e.get("deletedAt")
                ))
                count += 1
        db().commit()
    return count


# ── Serveur HTTP ──────────────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        pass  # on gère notre propre log

    def do_GET(self):
        parsed = urlparse(self.path)
        params = {k: v[0] for k, v in parse_qs(parsed.query).items()}

        if parsed.path == "/info":
            body = json.dumps({
                "deviceId":      DEVICE_ID,
                "latestEventAt": latest_event_ts(),
                "latestLyricsAt": latest_lyrics_ts(),
            })
        elif parsed.path == "/events":
            since = int(params.get("since", 0))
            body = json.dumps(events_since(since))
        elif parsed.path == "/lyrics":
            since = int(params.get("since", 0))
            body = json.dumps(lyrics_since(since))
        else:
            self.send_response(404)
            self.end_headers()
            return

        encoded = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(encoded)


# ── Client de synchronisation ─────────────────────────────────────────────────

def sync_with(host: str, port: int):
    base = f"http://{host}:{port}"
    try:
        with urllib.request.urlopen(f"{base}/info", timeout=5) as r:
            info = json.loads(r.read())

        peer_id = info.get("deviceId", "")
        if peer_id == DEVICE_ID:
            return  # soi-même

        # Events
        our_latest = latest_from_peer(peer_id)
        peer_latest = info.get("latestEventAt", 0)
        if peer_latest > our_latest:
            with urllib.request.urlopen(f"{base}/events?since={our_latest}", timeout=15) as r:
                events = json.loads(r.read())
            n = insert_events(events)
            if n:
                log.info(f"← {n} events  depuis {host}:{port}")

        # Paroles
        our_lyrics = latest_lyrics_ts()
        peer_lyrics = info.get("latestLyricsAt", 0)
        if peer_lyrics > our_lyrics:
            with urllib.request.urlopen(f"{base}/lyrics?since={our_lyrics}", timeout=15) as r:
                entries = json.loads(r.read())
            n = merge_lyrics(entries)
            if n:
                log.info(f"← {n} paroles depuis {host}:{port}")

    except Exception as exc:
        log.debug(f"Sync échouée avec {host}:{port} : {exc}")


# ── mDNS ─────────────────────────────────────────────────────────────────────

class PeerListener:
    def __init__(self):
        self._peers: dict[str, tuple[str, int]] = {}
        self._lock = threading.Lock()

    def add_service(self, zc: Zeroconf, stype: str, name: str):
        info = zc.get_service_info(stype, name)
        if not info or not info.addresses:
            return
        host = socket.inet_ntoa(info.addresses[0])
        port = info.port
        with self._lock:
            is_new = name not in self._peers
            self._peers[name] = (host, port)
        if is_new:
            log.info(f"Pair découvert : {name.split('.')[0]} @ {host}:{port}")

    def remove_service(self, zc, stype, name):
        with self._lock:
            self._peers.pop(name, None)

    def update_service(self, zc, stype, name):
        self.add_service(zc, stype, name)

    def all_peers(self) -> list[tuple[str, int]]:
        with self._lock:
            return list(self._peers.values())


def scan_peers(zc: Zeroconf, listener: PeerListener):
    """Ouvre un ServiceBrowser le temps de SCAN_DURATION secondes, puis le ferme."""
    log.info(f"Scan mDNS ({SCAN_DURATION}s)...")
    browser = ServiceBrowser(zc, SERVICE_TYPE, listener)
    time.sleep(SCAN_DURATION)
    browser.cancel()
    log.info(f"Scan terminé : {len(listener.all_peers())} pair(s) connu(s)")


def resync_loop(zc: Zeroconf, listener: PeerListener):
    """Scan + sync au démarrage, puis toutes les RESYNC_INTERVAL secondes."""
    while True:
        scan_peers(zc, listener)
        peers = listener.all_peers()
        if peers:
            log.info(f"Sync → {len(peers)} pair(s)")
            for host, port in peers:
                threading.Thread(target=sync_with, args=(host, port), daemon=True).start()
        time.sleep(RESYNC_INTERVAL)


def get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    finally:
        s.close()


# ── Point d'entrée ────────────────────────────────────────────────────────────

def main():
    global DEVICE_ID

    init_db()
    DEVICE_ID = load_device_id()

    local_ip  = get_local_ip()
    svc_name  = f"A2U-NAS-{DEVICE_ID[:8]}.{SERVICE_TYPE}"

    log.info(f"Device ID : {DEVICE_ID}")
    log.info(f"IP locale : {local_ip}:{PORT}")

    # Enregistrement mDNS
    zc = Zeroconf()
    svc_info = ServiceInfo(
        SERVICE_TYPE, svc_name,
        addresses=[socket.inet_aton(local_ip)],
        port=PORT,
        properties={},
    )
    zc.register_service(svc_info)
    log.info(f"mDNS enregistré : {svc_name}")

    # Scan initial + resync périodique (scan ponctuel toutes les 5h)
    listener = PeerListener()
    threading.Thread(target=resync_loop, args=(zc, listener), daemon=True).start()

    # Serveur HTTP
    server = HTTPServer(("0.0.0.0", PORT), Handler)
    log.info(f"Serveur HTTP démarré sur :{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        log.info("Arrêt...")
        zc.unregister_service(svc_info)
        zc.close()


if __name__ == "__main__":
    main()
