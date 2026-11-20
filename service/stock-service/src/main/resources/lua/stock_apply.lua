local stockKey = KEYS[1]
local operationKey = KEYS[2]
local operation = ARGV[1]
local beforeVersion = tonumber(ARGV[2])
local nextVersion = ARGV[3]
local availableDelta = tonumber(ARGV[4])
local reservedDelta = tonumber(ARGV[5])

local function isInteger(value)
    return value ~= nil and value == math.floor(value)
end

if not isInteger(beforeVersion) or beforeVersion < 0
        or not isInteger(tonumber(nextVersion)) or tonumber(nextVersion) ~= beforeVersion + 1
        or not isInteger(availableDelta) or not isInteger(reservedDelta) then
    return -1
end

-- 操作方向必须与MySQL变更一致，尤其确认不能增加可售、释放不能增加预扣。
if operation == 'RESERVE' then
    if availableDelta > 0 or reservedDelta < 0 or availableDelta + reservedDelta ~= 0 then
        return -1
    end
elseif operation == 'CONFIRM' then
    if availableDelta > 0 or reservedDelta > 0 then
        return -1
    end
elseif operation == 'RELEASE' then
    if availableDelta < 0 or reservedDelta > 0 or availableDelta + reservedDelta ~= 0 then
        return -1
    end
elseif operation == 'ADJUST' then
    if reservedDelta ~= 0 then
        return -1
    end
else
    return -1
end

local previousOperation = redis.call('GET', operationKey)
if previousOperation == operation then
    return 0
end
-- 同一预扣单允许RESERVE推进到CONFIRM/RELEASE，但拒绝迟到的旧预扣操作回写。
if (previousOperation == 'CONFIRM' or previousOperation == 'RELEASE') and operation ~= 'ADJUST' then
    return 0
end

local values = redis.call('HMGET', stockKey, 'merchantId', 'total', 'available', 'reserved', 'version')
if not values[1] or not values[2] or not values[3] or not values[4] or not values[5] then
    if redis.call('EXISTS', stockKey) == 1 then
        redis.call('DEL', stockKey)
    end
    -- miss时绝不能从0开始累加；保留7天操作幂等标记，等待读取回源。
    redis.call('SET', operationKey, operation, 'EX', 604800)
    return 2
end

local total = tonumber(values[2])
local available = tonumber(values[3])
local reserved = tonumber(values[4])
local cachedVersion = tonumber(values[5])
if not isInteger(total) or not isInteger(available) or not isInteger(reserved)
        or not isInteger(cachedVersion) or total < 0 or available < 0 or reserved < 0 or cachedVersion < 0
        or total ~= available + reserved then
    redis.call('DEL', stockKey)
    return -1
end

-- MySQL每次更新version+1，缓存基线必须正好是更新前版本。
if cachedVersion ~= beforeVersion then
    redis.call('DEL', stockKey)
    redis.call('SET', operationKey, operation, 'EX', 604800)
    return 3
end

local newAvailable = available + availableDelta
local newReserved = reserved + reservedDelta
local newTotal = total + availableDelta + reservedDelta
if newAvailable < 0 or newReserved < 0 or newTotal < 0
        or newTotal ~= newAvailable + newReserved then
    redis.call('DEL', stockKey)
    return -1
end

redis.call('HSET', stockKey,
        'total', newTotal,
        'available', newAvailable,
        'reserved', newReserved,
        'version', nextVersion)
redis.call('SET', operationKey, operation, 'EX', 604800)
return 1
