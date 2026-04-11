"""SQLite 기반 알람 상태 관리.

목적:
- 활성 알람 추적: 어떤 메트릭이 현재 위반 상태인지 + 마지막 발송 시각/값
- severity별 cooldown: 같은 알람 재발송 빈도 제어
- 악화 감지: 직전 발송값 대비 변화량 비교
- 해소 감지: 이전엔 위반이었는데 이번엔 정상이 된 메트릭 식별
- 일일 카운터: 일일 요약 리포트용 anomaly_count 집계
"""
from __future__ import annotations

import sqlite3
import time
from dataclasses import dataclass
from datetime import date
from pathlib import Path


@dataclass
class ActiveAlert:
    """현재 위반 상태인 메트릭의 마지막 알람 정보."""

    rule_key: str
    fired_at: int       # unix timestamp
    last_value: float   # 마지막으로 발송된 시점의 값 (악화 비교용)
    severity: str


class StateStore:
    def __init__(self, db_path: str):
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(db_path)
        self.conn.execute("PRAGMA journal_mode=WAL")
        self._init_schema()

    def _init_schema(self) -> None:
        # 구버전 cooldowns 테이블 제거 (단순한 dev 마이그레이션)
        self.conn.executescript(
            """
            DROP TABLE IF EXISTS cooldowns;

            CREATE TABLE IF NOT EXISTS active_alerts (
                rule_key   TEXT PRIMARY KEY,
                fired_at   INTEGER NOT NULL,
                last_value REAL NOT NULL,
                severity   TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS daily_stats (
                date              TEXT PRIMARY KEY,
                anomaly_count     INTEGER NOT NULL DEFAULT 0,
                last_summary_sent INTEGER NOT NULL DEFAULT 0
            );
            """
        )
        self.conn.commit()

    # === 활성 알람 ===

    def list_active(self) -> dict[str, ActiveAlert]:
        """현재 활성 알람 전체를 dict로 반환."""
        cur = self.conn.execute(
            "SELECT rule_key, fired_at, last_value, severity FROM active_alerts"
        )
        return {
            row[0]: ActiveAlert(
                rule_key=row[0], fired_at=row[1], last_value=row[2], severity=row[3]
            )
            for row in cur.fetchall()
        }

    def record_alert(self, rule_key: str, value: float, severity: str) -> None:
        """알람을 발송했음을 기록 (신규/재발송 모두). fired_at은 현재 시각으로 갱신."""
        self.conn.execute(
            """
            INSERT INTO active_alerts(rule_key, fired_at, last_value, severity)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(rule_key) DO UPDATE SET
                fired_at = excluded.fired_at,
                last_value = excluded.last_value,
                severity = excluded.severity
            """,
            (rule_key, int(time.time()), value, severity),
        )
        self.conn.commit()

    def clear_alert(self, rule_key: str) -> None:
        """위반 해소 시 활성 알람 목록에서 제거."""
        self.conn.execute("DELETE FROM active_alerts WHERE rule_key = ?", (rule_key,))
        self.conn.commit()

    # === 일일 카운터 ===

    def increment_today_anomaly(self, count: int = 1) -> None:
        today = date.today().isoformat()
        self.conn.execute(
            """
            INSERT INTO daily_stats(date, anomaly_count) VALUES (?, ?)
            ON CONFLICT(date) DO UPDATE SET anomaly_count = anomaly_count + excluded.anomaly_count
            """,
            (today, count),
        )
        self.conn.commit()

    def get_today_anomaly_count(self) -> int:
        today = date.today().isoformat()
        cur = self.conn.execute(
            "SELECT anomaly_count FROM daily_stats WHERE date = ?", (today,)
        )
        row = cur.fetchone()
        return row[0] if row else 0

    def mark_summary_sent(self) -> None:
        today = date.today().isoformat()
        self.conn.execute(
            """
            INSERT INTO daily_stats(date, last_summary_sent) VALUES (?, ?)
            ON CONFLICT(date) DO UPDATE SET last_summary_sent = excluded.last_summary_sent
            """,
            (today, int(time.time())),
        )
        self.conn.commit()

    def is_summary_sent_today(self) -> bool:
        today = date.today().isoformat()
        cur = self.conn.execute(
            "SELECT last_summary_sent FROM daily_stats WHERE date = ?", (today,)
        )
        row = cur.fetchone()
        return bool(row and row[0] > 0)

    def close(self) -> None:
        self.conn.close()
