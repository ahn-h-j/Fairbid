-- 입찰 처리 Lua 스크립트 (경매 연장 + 즉시 구매 포함)
-- 원자적으로 입찰 검증 + 현재가 갱신 + 경매 연장 + 즉시 구매 수행
--
-- KEYS[1]: auction:{auctionId} (경매 해시 키)
-- KEYS[2]: auction:closing (종료 대기 큐 Sorted Set)
-- ARGV[1]: bidAmount (입찰 금액, ONE_TOUCH면 0)
-- ARGV[2]: bidderId (입찰자 ID)
-- ARGV[3]: bidType (ONE_TOUCH / DIRECT / INSTANT_BUY)
-- ARGV[4]: currentTimeMs (현재 시간, 밀리초)
--
-- 반환값:
--   성공: {1, newCurrentPrice, newTotalBidCount, newBidIncrement, extended(0/1), newExtensionCount, newScheduledEndTimeMs, instantBuyActivated(0/1)}
--   실패: {0, errorCode, ...}
--     errorCode: "NOT_FOUND", "NOT_ACTIVE", "SELF_BID", "BID_TOO_LOW", "AUCTION_ENDED",
--                "INSTANT_BUY_NOT_AVAILABLE", "INSTANT_BUY_DISABLED", "INSTANT_BUY_ALREADY_ACTIVATED"

local auctionKey = KEYS[1]
local closingQueueKey = KEYS[2]
-- 경매 ID 추출 (콜론 위치 기반, 키 포맷 변경에도 안전)
local colonIndex = string.find(auctionKey, ":", 1, true)
local auctionId = string.sub(auctionKey, colonIndex + 1)
local requestedAmount = tonumber(ARGV[1])
local bidderId = ARGV[2]
local bidType = ARGV[3]
local currentTimeMs = tonumber(ARGV[4])

-- 1. 경매 정보 조회
local auctionData = redis.call('HGETALL', auctionKey)
if #auctionData == 0 then
    return {0, "NOT_FOUND"}
end

-- Hash를 테이블로 변환
local auction = {}
for i = 1, #auctionData, 2 do
    auction[auctionData[i]] = auctionData[i + 1]
end

-- 2. 경매 상태 검증 (BIDDING 또는 INSTANT_BUY_PENDING 상태만 입찰 가능)
local status = auction['status']
if status ~= 'BIDDING' and status ~= 'INSTANT_BUY_PENDING' then
    return {0, "NOT_ACTIVE"}
end

-- 2-1. 종료 시간 검증 (스케줄러 처리 전 입찰 차단)
local scheduledEndTimeMs = tonumber(auction['scheduledEndTimeMs'] or '0')
if scheduledEndTimeMs > 0 and currentTimeMs > scheduledEndTimeMs then
    return {0, "AUCTION_ENDED"}
end

-- 3. 본인 경매 입찰 검증
local sellerId = auction['sellerId']
if sellerId == bidderId then
    return {0, "SELF_BID"}
end

-- 4. 현재가와 입찰단위, 연장 횟수 조회
local currentPrice = tonumber(auction['currentPrice'])
local bidIncrement = tonumber(auction['bidIncrement'])
local extensionCount = tonumber(auction['extensionCount'] or '0')
local instantBuyPrice = tonumber(auction['instantBuyPrice'] or '0')

-- 5. 즉시 구매 처리
local instantBuyActivated = 0
local oneHourMs = 60 * 60 * 1000

if bidType == 'INSTANT_BUY' then
    -- 5-1. 즉시 구매가 설정 여부 확인
    if instantBuyPrice <= 0 then
        return {0, "INSTANT_BUY_NOT_AVAILABLE"}
    end

    -- 5-2. 이미 즉시 구매 활성화된 경우 차단
    if status == 'INSTANT_BUY_PENDING' then
        return {0, "INSTANT_BUY_ALREADY_ACTIVATED"}
    end

    -- 5-3. 90% 이상이면 즉시 구매 비활성화
    local threshold = math.floor(instantBuyPrice * 0.9)
    if currentPrice >= threshold then
        return {0, "INSTANT_BUY_DISABLED"}
    end

    -- 5-4. 즉시 구매 처리
    instantBuyActivated = 1
    redis.call('HSET', auctionKey, 'status', 'INSTANT_BUY_PENDING')
    redis.call('HSET', auctionKey, 'instantBuyActivatedTimeMs', currentTimeMs)
    redis.call('HSET', auctionKey, 'instantBuyerId', bidderId)

    -- 5-5. 종료 시간을 현재 + 1시간으로 변경 (1시간 최종 입찰 기회)
    local newEndTimeMs = currentTimeMs + oneHourMs
    redis.call('HSET', auctionKey, 'scheduledEndTimeMs', newEndTimeMs)
    redis.call('HSET', auctionKey, 'scheduledEndTime', tostring(newEndTimeMs))
    scheduledEndTimeMs = newEndTimeMs

    -- 5-5-1. 종료 대기 큐 score 갱신
    redis.call('ZADD', closingQueueKey, newEndTimeMs, auctionId)

    -- 5-6. 현재가를 즉시 구매가로 갱신
    redis.call('HSET', auctionKey, 'currentPrice', instantBuyPrice)
    local newTotalBidCount = redis.call('HINCRBY', auctionKey, 'totalBidCount', 1)

    -- 5-6-1. 1순위 입찰자 정보 갱신 (기존 1순위 → 2순위로 이동)
    local prevTopBidderId = redis.call('HGET', auctionKey, 'topBidderId')
    local prevTopBidAmount = redis.call('HGET', auctionKey, 'topBidAmount')
    if prevTopBidderId and prevTopBidderId ~= '' and prevTopBidderId ~= bidderId then
        redis.call('HSET', auctionKey, 'secondBidderId', prevTopBidderId)
        if prevTopBidAmount and prevTopBidAmount ~= '' then
            redis.call('HSET', auctionKey, 'secondBidAmount', prevTopBidAmount)
        end
    end
    redis.call('HSET', auctionKey, 'topBidderId', bidderId)
    redis.call('HSET', auctionKey, 'topBidAmount', instantBuyPrice)

    -- 5-7. 입찰 단위 재계산
    local newBidIncrement
    if instantBuyPrice < 10000 then
        newBidIncrement = 500
    elseif instantBuyPrice < 50000 then
        newBidIncrement = 1000
    elseif instantBuyPrice < 100000 then
        newBidIncrement = 3000
    elseif instantBuyPrice < 500000 then
        newBidIncrement = 5000
    elseif instantBuyPrice < 1000000 then
        newBidIncrement = 10000
    else
        newBidIncrement = 30000
    end
    redis.call('HSET', auctionKey, 'bidIncrement', newBidIncrement)

    -- 즉시 구매 성공 반환 (연장 없음)
    return {1, instantBuyPrice, newTotalBidCount, newBidIncrement, 0, extensionCount, scheduledEndTimeMs, instantBuyActivated}
end

-- 6. 일반 입찰 처리 (ONE_TOUCH / DIRECT)

-- 6-1. 연장 횟수에 따른 할증 입찰단위 계산 (3회마다 50% 증가)
local adjustedBidIncrement = bidIncrement
local surchargeMultiplier = math.floor(extensionCount / 3)
if surchargeMultiplier > 0 then
    adjustedBidIncrement = math.floor(bidIncrement * (1 + 0.5 * surchargeMultiplier))
end

local minBidAmount = currentPrice + adjustedBidIncrement

-- 6-2. 입찰 금액 결정 (ONE_TOUCH면 자동 계산)
local bidAmount
if bidType == 'ONE_TOUCH' then
    bidAmount = minBidAmount
else
    bidAmount = requestedAmount
end

-- 6-3. 입찰 금액 검증
if bidAmount < minBidAmount then
    return {0, "BID_TOO_LOW", currentPrice, minBidAmount}
end

-- 7. 경매 연장 확인 (종료 5분 전, 단 INSTANT_BUY_PENDING 상태에서는 연장 불가)
local extended = 0
local fiveMinutesMs = 5 * 60 * 1000

if status == 'BIDDING' and scheduledEndTimeMs > 0 and currentTimeMs > 0 then
    local timeToEnd = scheduledEndTimeMs - currentTimeMs

    -- 종료 5분 전이면 연장
    if timeToEnd > 0 and timeToEnd <= fiveMinutesMs then
        extended = 1
        local newEndTimeMs = currentTimeMs + fiveMinutesMs
        redis.call('HSET', auctionKey, 'scheduledEndTimeMs', newEndTimeMs)
        redis.call('HSET', auctionKey, 'scheduledEndTime', tostring(newEndTimeMs))
        extensionCount = extensionCount + 1
        redis.call('HSET', auctionKey, 'extensionCount', extensionCount)

        -- 종료 대기 큐 score 갱신
        redis.call('ZADD', closingQueueKey, newEndTimeMs, auctionId)
    end
end

-- 8. 1순위 입찰자 정보 갱신 (기존 1순위 → 2순위로 이동)
local prevTopBidderId = redis.call('HGET', auctionKey, 'topBidderId')
local prevTopBidAmount = redis.call('HGET', auctionKey, 'topBidAmount')
if prevTopBidderId and prevTopBidderId ~= '' and prevTopBidderId ~= bidderId then
    -- 기존 1순위가 존재하고, 현재 입찰자와 다르면 2순위로 이동
    redis.call('HSET', auctionKey, 'secondBidderId', prevTopBidderId)
    if prevTopBidAmount and prevTopBidAmount ~= '' then
        redis.call('HSET', auctionKey, 'secondBidAmount', prevTopBidAmount)
    end
end
redis.call('HSET', auctionKey, 'topBidderId', bidderId)
redis.call('HSET', auctionKey, 'topBidAmount', bidAmount)

-- 9. 현재가 갱신 + 입찰수 증가
redis.call('HSET', auctionKey, 'currentPrice', bidAmount)
local newTotalBidCount = redis.call('HINCRBY', auctionKey, 'totalBidCount', 1)

-- 10. 입찰 단위 재계산 (가격 구간별)
local newBidIncrement
if bidAmount < 10000 then
    newBidIncrement = 500
elseif bidAmount < 50000 then
    newBidIncrement = 1000
elseif bidAmount < 100000 then
    newBidIncrement = 3000
elseif bidAmount < 500000 then
    newBidIncrement = 5000
elseif bidAmount < 1000000 then
    newBidIncrement = 10000
else
    newBidIncrement = 30000
end
redis.call('HSET', auctionKey, 'bidIncrement', newBidIncrement)

-- 11. 최종 종료 시간 조회 (연장됐을 수 있으므로)
local finalScheduledEndTimeMs = tonumber(redis.call('HGET', auctionKey, 'scheduledEndTimeMs'))

-- 12. 성공 반환
return {1, bidAmount, newTotalBidCount, newBidIncrement, extended, extensionCount, finalScheduledEndTimeMs, instantBuyActivated}
