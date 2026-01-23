import { useState, useEffect, useCallback } from 'react';
import { getServerTime } from '../api/client';
import { TIMER_WARNING_THRESHOLD, TIMER_DANGER_THRESHOLD } from '../utils/constants';
import { padZero } from '../utils/formatters';

/**
 * 경매 카운트다운 타이머 훅
 * 서버 시간 기준으로 남은 시간을 매 초 계산하며,
 * 경매 연장 시 endTimeStr 변경에 즉시 반응한다.
 *
 * @param {string|null} endTimeStr - 경매 종료 시각 (ISO 8601)
 * @returns {{
 *   hours: number,
 *   minutes: number,
 *   seconds: number,
 *   totalSeconds: number,
 *   isExpired: boolean,
 *   timerState: 'normal'|'warning'|'danger',
 *   formattedTime: string
 * }}
 */
export function useTimer(endTimeStr) {
  const calculate = useCallback(() => {
    if (!endTimeStr) {
      return { hours: 0, minutes: 0, seconds: 0, totalSeconds: 0, isExpired: true };
    }

    const endTime = new Date(endTimeStr).getTime();
    const now = getServerTime().getTime();
    const diff = endTime - now;

    if (diff <= 0) {
      return { hours: 0, minutes: 0, seconds: 0, totalSeconds: 0, isExpired: true };
    }

    const totalSeconds = Math.floor(diff / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    return { hours, minutes, seconds, totalSeconds, isExpired: false };
  }, [endTimeStr]);

  const [timeState, setTimeState] = useState(calculate);

  useEffect(() => {
    // endTimeStr 변경 시 즉시 재계산 (경매 연장 대응)
    setTimeState(calculate());

    const interval = setInterval(() => {
      const newState = calculate();
      setTimeState(newState);

      if (newState.isExpired) {
        clearInterval(interval);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [calculate]);

  // 시각적 상태 결정 (normal → warning → danger)
  let timerState = 'normal';
  if (!timeState.isExpired) {
    if (timeState.totalSeconds <= TIMER_DANGER_THRESHOLD) {
      timerState = 'danger';
    } else if (timeState.totalSeconds <= TIMER_WARNING_THRESHOLD) {
      timerState = 'warning';
    }
  }

  const formattedTime = timeState.isExpired
    ? '종료됨'
    : `${padZero(timeState.hours)}:${padZero(timeState.minutes)}:${padZero(timeState.seconds)}`;

  return {
    ...timeState,
    timerState,
    formattedTime,
  };
}
