-- 입찰 처리 Lua 스크립트 (경매 연장 포함)
-- 원자적으로 입찰 검증 + 현재가 갱신 + 경매 연장 수행
--
-- KEYS[1]: auction:{auctionId} (경매 해시 키)
-- ARGV[1]: bidAmount (입찰 금액, ONE_TOUCH면 0)
-- ARGV[2]: bidderId (입찰자 ID)
-- ARGV[3]: bidType (ONE_TOUCH / DIRECT)
-- ARGV[4]: currentTimeMs (현재 시간, 밀리초)
--
-- 반환값:
--   성공: {1, newCurrentPrice, newTotalBidCount, newBidIncrement, extended(0/1), newExtensionCount, newScheduledEndTimeMs}
--   실패: {0, errorCode, ...}
--     errorCode: "NOT_FOUND", "NOT_ACTIVE", "SELF_BID", "BID_TOO_LOW"

local auctionKey = KEYS[1]
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

-- 2. 경매 상태 검증
local status = auction['status']
if status ~= 'BIDDING' then
    return {0, "NOT_ACTIVE"}
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

-- 5. 연장 횟수에 따른 할증 입찰단위 계산 (3회마다 50% 증가)
local adjustedBidIncrement = bidIncrement
local surchargeMultiplier = math.floor(extensionCount / 3)
if surchargeMultiplier > 0 then
    adjustedBidIncrement = math.floor(bidIncrement * (1 + 0.5 * surchargeMultiplier))
end

local minBidAmount = currentPrice + adjustedBidIncrement

-- 6. 입찰 금액 결정 (ONE_TOUCH면 자동 계산)
local bidAmount
if bidType == 'ONE_TOUCH' then
    bidAmount = minBidAmount
else
    bidAmount = requestedAmount
end

-- 7. 입찰 금액 검증
if bidAmount < minBidAmount then
    return {0, "BID_TOO_LOW", currentPrice, minBidAmount}
end

-- 8. 경매 연장 확인 (종료 5분 전)
local scheduledEndTimeMs = tonumber(auction['scheduledEndTimeMs'] or '0')
local extended = 0
local fiveMinutesMs = 5 * 60 * 1000

if scheduledEndTimeMs > 0 and currentTimeMs > 0 then
    local timeToEnd = scheduledEndTimeMs - currentTimeMs

    -- 종료 5분 전이면 연장
    if timeToEnd > 0 and timeToEnd <= fiveMinutesMs then
        extended = 1
        local newEndTimeMs = currentTimeMs + fiveMinutesMs
        redis.call('HSET', auctionKey, 'scheduledEndTimeMs', newEndTimeMs)
        extensionCount = extensionCount + 1
        redis.call('HSET', auctionKey, 'extensionCount', extensionCount)
    end
end

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
return {1, bidAmount, newTotalBidCount, newBidIncrement, extended, extensionCount, finalScheduledEndTimeMs}
