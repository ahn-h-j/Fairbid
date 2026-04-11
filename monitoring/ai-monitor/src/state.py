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
            -- 주간 evolver가 읽어가는 전체 발송 이력 (4가지 kind)
            CREATE TABLE IF NOT EXISTS alert_history (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_key   TEXT NOT NULL,
                severity   TEXT NOT NULL,
                kind       TEXT NOT NULL,
                value      REAL,
                threshold  REAL,
                fired_at   INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_history_time ON alert_history(fired_at);
            CREATE INDEX IF NOT EXISTS idx_history_rule ON alert_history(rule_key);
            -- 주간 evolver 중복 발송 방지용
            CREATE TABLE IF NOT EXISTS evolve_runs (
                run_date TEXT PRIMARY KEY,
                sent_at  INTEGER NOT NULL
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

    # === 알람 이력 (주간 evolver용) ===

    def record_history(
        self,
        rule_key: str,
        severity: str,
        kind: str,
        value: float | None,
        threshold: float | None,
    ) -> None:
        """모든 알람 발송(신규/악화/지속/해소)을 이력에 기록.

        kind: new | escalation | persistence | recovery
        """
        self.conn.execute(
            """
            INSERT INTO alert_history(rule_key, severity, kind, value, threshold, fired_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (rule_key, severity, kind, value, threshold, int(time.time())),
        )
        self.conn.commit()

    def aggregate_last_days(self, days: int) -> dict:
        """지난 N일 이력을 집계하여 evolver에 전달할 dict를 반환."""
        since_ts = int(time.time()) - days * 86400

        cur = self.conn.execute(
            """
            SELECT rule_key, severity, kind, COUNT(*), MIN(value), MAX(value)
            FROM alert_history
            WHERE fired_at >= ?
            GROUP BY rule_key, severity, kind
            """,
            (since_ts,),
        )

        metric_map: dict[str, dict] = {}
        total = 0
        for rule_key, severity, kind, count, vmin, vmax in cur.fetchall():
            total += count
            entry = metric_map.setdefault(
                rule_key,
                {
                    "rule_key": rule_key,
                    "severity": severity,
                    "counts": {"new": 0, "escalation": 0, "persistence": 0, "recovery": 0},
                    "value_min": None,
                    "value_max": None,
                    "avg_duration_minutes": None,
                },
            )
            if kind in entry["counts"]:
                entry["counts"][kind] = count
            if vmin is not None:
                cur_min = entry["value_min"]
                entry["value_min"] = vmin if cur_min is None else min(cur_min, vmin)
            if vmax is not None:
                cur_max = entry["value_max"]
                entry["value_max"] = vmax if cur_max is None else max(cur_max, vmax)

        for rule_key, entry in metric_map.items():
            entry["avg_duration_minutes"] = self._avg_alert_duration(rule_key, since_ts)

        from datetime import datetime, timezone
        return {
            "period_days": days,
            "period_start": datetime.fromtimestamp(since_ts, tz=timezone.utc).date().isoformat(),
            "period_end": datetime.now(tz=timezone.utc).date().isoformat(),
            "total_alerts": total,
            "by_metric": list(metric_map.values()),
        }

    def _avg_alert_duration(self, rule_key: str, since_ts: int) -> float | None:
        """특정 메트릭의 new → recovery 평균 지속 시간(분).

        같은 rule_key에 대해 new와 recovery가 번갈아 발생한다고 가정하고
        시간순으로 짝지어 지속 시간을 평균낸다. unresolved한 new는 제외.
        """
        cur = self.conn.execute(
            """
            SELECT kind, fired_at FROM alert_history
            WHERE rule_key = ? AND fired_at >= ? AND kind IN ('new', 'recovery')
            ORDER BY fired_at ASC
            """,
            (rule_key, since_ts),
        )
        rows = cur.fetchall()
        durations: list[int] = []
        current_new_at: int | None = None
        for kind, ts in rows:
            if kind == "new":
                current_new_at = ts
            elif kind == "recovery" and current_new_at is not None:
                durations.append(ts - current_new_at)
                current_new_at = None
        if not durations:
            return None
        return round(sum(durations) / len(durations) / 60, 1)

    # === evolver 중복 방지 ===

    def is_evolve_sent_this_week(self) -> bool:
        """이번 주(월요일 기준)에 이미 evolve 리포트를 발송했는지."""
        from datetime import date as _date, timedelta
        today = _date.today()
        week_start = (today - timedelta(days=today.weekday())).isoformat()
        cur = self.conn.execute(
            "SELECT sent_at FROM evolve_runs WHERE run_date = ?", (week_start,)
        )
        return cur.fetchone() is not None

    def mark_evolve_sent(self) -> None:
        from datetime import date as _date, timedelta
        today = _date.today()
        week_start = (today - timedelta(days=today.weekday())).isoformat()
        self.conn.execute(
            "INSERT OR REPLACE INTO evolve_runs(run_date, sent_at) VALUES (?, ?)",
            (week_start, int(time.time())),
        )
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
