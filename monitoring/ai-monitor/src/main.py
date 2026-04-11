"""ai-monitor 진입점.

schedule 라이브러리로 주기 작업을 돌린다 (cron 외부 의존 제거).
- check_interval_seconds 마다 이상 탐지
- daily_summary_time 에 일일 요약 1회
"""
from __future__ import annotations

import logging
import signal
import sys
import time

import schedule

from .analyzer import ClaudeAnalyzer
from .config import Settings, load_settings
from .evolver import Evolver
from .prometheus import PrometheusClient, QueryResult
from .reporter import DiscordReporter
from .rules import evaluate
from .state import StateStore

logger = logging.getLogger("ai-monitor")


def main() -> None:
    settings = load_settings()
    _setup_logging(settings.log_level)

    logger.info("ai-monitor starting up")
    logger.info("prometheus_url=%s model=%s", settings.prometheus_url, settings.runtime.claude_model)

    prom = PrometheusClient(settings.prometheus_url, settings.runtime.prometheus_timeout_seconds)
    analyzer = ClaudeAnalyzer(settings)
    reporter = DiscordReporter(settings.discord_webhook_url)
    store = StateStore(settings.state_db_path)
    evolver = Evolver(settings, store)

    def check_anomalies() -> None:
        try:
            _check_anomalies(settings, prom, analyzer, reporter, store)
        except Exception as e:
            logger.exception("check_anomalies failed: %s", e)

    def send_daily_summary() -> None:
        try:
            _send_daily_summary(settings, prom, analyzer, reporter, store)
        except Exception as e:
            logger.exception("send_daily_summary failed: %s", e)

    def run_evolver() -> None:
        try:
            _run_evolver(settings, evolver, reporter, store)
        except Exception as e:
            logger.exception("evolver run failed: %s", e)

    schedule.every(settings.runtime.check_interval_seconds).seconds.do(check_anomalies)
    schedule.every().day.at(settings.runtime.daily_summary_time).do(send_daily_summary)

    # 주간 evolver 등록 — schedule 라이브러리 요일 메서드 사용
    evolve_cfg = settings.runtime.evolve
    if evolve_cfg.enabled:
        day_method = getattr(schedule.every(), evolve_cfg.day.lower(), None)
        if day_method is None:
            logger.error("invalid evolve.day: %s — weekly evolver disabled", evolve_cfg.day)
        else:
            day_method.at(evolve_cfg.time).do(run_evolver)
            logger.info(
                "weekly evolver scheduled: %s %s (lookback %d days)",
                evolve_cfg.day, evolve_cfg.time, evolve_cfg.lookback_days,
            )

    # 시작 즉시 1회 헬스체크 (Prometheus 연결 확인)
    check_anomalies()

    # 우아한 종료
    def shutdown(signum, frame):
        logger.info("received signal %d, shutting down", signum)
        store.close()
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    logger.info("scheduler started")
    while True:
        schedule.run_pending()
        time.sleep(1)


def _check_anomalies(
    settings: Settings,
    prom: PrometheusClient,
    analyzer: ClaudeAnalyzer,
    reporter: DiscordReporter,
    store: StateStore,
) -> None:
    """단일 이상 탐지 사이클.

    위반을 4가지로 분류한다:
    - 신규: 활성 알람 목록에 없는 새 위반 → Claude 분석 + 🚨 알람
    - 악화: 직전 발송값 대비 N% 이상 악화 → Claude 분석 + 📈 알람 + cooldown 리셋
    - 지속: cooldown 만료 후에도 위반 지속 → Claude 호출 없이 ⏰ 알람
    - 해소: 활성 알람이었는데 현재 정상 → ✅ 알람 + 활성 목록에서 제거

    cooldown 안 지났고 악화도 아닌 지속 알람은 단순 무시 (스팸 방지).
    """
    runtime = settings.runtime

    # 1. 모든 메트릭 폴링
    results: dict[str, QueryResult] = {}
    for metric in settings.metrics:
        results[metric.key] = prom.query(metric.key, metric.promql)

    # 2. 룰 평가
    violations = evaluate(settings.metrics, results)
    violation_map = {v.metric.key: v for v in violations}

    # 3. 활성 알람 조회
    active = store.list_active()
    now_ts = int(time.time())

    # 4. 분류
    new_violations: list = []
    escalated: list = []
    persisted: list = []

    for v in violations:
        prev = active.get(v.metric.key)
        if prev is None:
            new_violations.append(v)
            continue

        # 악화 감지: 절대값 기준 변화율
        denom = max(abs(prev.last_value), 1e-9)
        change_ratio = abs(v.value - prev.last_value) / denom
        worsened = (
            change_ratio >= runtime.escalation_threshold
            and (
                # gt: 값이 커지면 악화
                (v.metric.comparator == "gt" and v.value > prev.last_value)
                # lt: 값이 작아지면 악화
                or (v.metric.comparator == "lt" and v.value < prev.last_value)
                # abs_gt: 절대값이 커지면 악화
                or (v.metric.comparator == "abs_gt" and abs(v.value) > abs(prev.last_value))
            )
        )

        cooldown_min = runtime.cooldown_for(v.metric.severity)
        elapsed_seconds = now_ts - prev.fired_at

        if worsened:
            escalated.append(v)
        elif elapsed_seconds >= cooldown_min * 60:
            persisted.append(v)
        # else: cooldown 안 지났고 악화도 아님 → 무시

    # 해소: 이전 활성 알람 중 현재 위반에서 사라진 것
    recovered = [
        alert for key, alert in active.items() if key not in violation_map
    ]

    # 5. 발송
    if new_violations:
        _handle_new(new_violations, results, analyzer, reporter, store)
    if escalated:
        _handle_escalation(escalated, active, results, analyzer, reporter, store)
    if persisted:
        _handle_persistence(persisted, active, reporter, store)
    if recovered and runtime.send_recovery_notice:
        label_map = {m.key: m.label for m in settings.metrics}
        _handle_recovery(recovered, results, reporter, store, label_map)

    if not (new_violations or escalated or persisted or recovered):
        logger.debug("no actionable changes — skip")


def _handle_new(violations, results, analyzer, reporter, store) -> None:
    logger.info("calling Claude for %d new violations", len(violations))
    report = analyzer.analyze(violations, results)
    if reporter.send_anomaly(violations, report):
        logger.info("anomaly report sent")
    else:
        logger.error("anomaly send failed")
    for v in violations:
        store.record_alert(v.metric.key, v.value, v.metric.severity)
        store.record_history(
            v.metric.key, v.metric.severity, "new", v.value, v.metric.threshold
        )
    store.increment_today_anomaly(len(violations))


def _handle_escalation(violations, previous, results, analyzer, reporter, store) -> None:
    logger.info("calling Claude for %d escalated violations", len(violations))
    report = analyzer.analyze(violations, results)
    if reporter.send_escalation(violations, previous, report):
        logger.info("escalation report sent")
    else:
        logger.error("escalation send failed")
    for v in violations:
        store.record_alert(v.metric.key, v.value, v.metric.severity)
        store.record_history(
            v.metric.key, v.metric.severity, "escalation", v.value, v.metric.threshold
        )
    store.increment_today_anomaly(len(violations))


def _handle_persistence(violations, previous, reporter, store) -> None:
    """Claude 호출 없이 단순 알람만 발송."""
    logger.info("sending persistence notice for %d violations", len(violations))
    if reporter.send_persistence(violations, previous):
        logger.info("persistence report sent")
    else:
        logger.error("persistence send failed")
    for v in violations:
        store.record_alert(v.metric.key, v.value, v.metric.severity)
        store.record_history(
            v.metric.key, v.metric.severity, "persistence", v.value, v.metric.threshold
        )


def _handle_recovery(recovered, results, reporter, store, label_map) -> None:
    """Claude 호출 없이 단순 해소 알람."""
    logger.info("sending recovery notice for %d alerts", len(recovered))
    current_values = {k: r.value for k, r in results.items() if r.value is not None}
    if reporter.send_recovery(recovered, current_values, label_map):
        logger.info("recovery report sent")
    else:
        logger.error("recovery send failed")
    for alert in recovered:
        current_value = current_values.get(alert.rule_key)
        store.record_history(
            alert.rule_key, alert.severity, "recovery", current_value, None
        )
        store.clear_alert(alert.rule_key)


def _run_evolver(
    settings: Settings,
    evolver: Evolver,
    reporter: DiscordReporter,
    store: StateStore,
) -> None:
    """주간 모니터링 정책 리뷰. 이번 주 이미 발송했으면 skip."""
    if store.is_evolve_sent_this_week():
        logger.info("evolver already sent this week — skip")
        return

    report = evolver.run(days=settings.runtime.evolve.lookback_days)
    if report is None:
        logger.info("no data for evolver — skip")
        return

    if reporter.send_evolve_report(report):
        store.mark_evolve_sent()
        logger.info("evolve report sent")
    else:
        logger.error("evolve report send failed")


def _send_daily_summary(
    settings: Settings,
    prom: PrometheusClient,
    analyzer: ClaudeAnalyzer,
    reporter: DiscordReporter,
    store: StateStore,
) -> None:
    """일일 요약 — 정상이어도 1회 발송 (heartbeat 역할)."""
    if store.is_summary_sent_today():
        logger.info("daily summary already sent today")
        return

    # 현재 메트릭 스냅샷
    snapshot: dict[str, float | None] = {}
    for metric in settings.metrics:
        result = prom.query(metric.key, metric.promql)
        snapshot[metric.key] = result.value

    anomaly_count = store.get_today_anomaly_count()
    summary_text = analyzer.daily_summary(anomaly_count, snapshot)

    if reporter.send_daily_summary(summary_text, anomaly_count):
        store.mark_summary_sent()
        logger.info("daily summary sent")


def _setup_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


if __name__ == "__main__":
    main()
