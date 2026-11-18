local stockKey = KEYS[1]
local merchantId = ARGV[1]
local total = tonumber(ARGV[2])
local available = tonumber(ARGV[3])
local reserved = tonumber(ARGV[4])
local version = tonumber(ARGV[5])

local function isInteger(value)
    return value ~= nil and value == math.floor(value)
end

if not isInteger(total) or not isInteger(available) or not isInteger(reserved) or not isInteger(version)
        or total < 0 or available < 0 or reserved < 0 or version < 0
        or total ~= available + reserved then
    return -1
end

-- 较旧的数据库读取结果不能覆盖已由提交后回调更新的新版本。
local currentVersion = tonumber(redis.call('HGET', stockKey, 'version') or '-1')
if currentVersion and currentVersion > version then
    return 0
end

redis.call('HSET', stockKey,
        'merchantId', merchantId,
        'total', ARGV[2],
        'available', ARGV[3],
        'reserved', ARGV[4],
        'version', ARGV[5])
return 1
