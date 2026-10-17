-- 用户访问频率限制：在时间窗口内对请求原子计数
--
-- KEYS[1] 完整的计数键（Java 侧已拼好前缀与计数桶）
-- ARGV[1] 时间窗口，单位秒
--
-- 用脚本而不是「INCR + EXPIRE 两次调用」：两次调用之间进程若挂掉，
-- 计数键就没有过期时间，该用户会被永久限流。

local accessKey = KEYS[1]
local timeWindow = tonumber(ARGV[1])

local currentAccessCount = redis.call("INCR", accessKey)

-- 只在计数从 0 变成 1 时设置过期时间。
-- 如果每次请求都 EXPIRE，就成了「滑动窗口」：持续访问时计数永远不归零，
-- 一旦超过阈值就再也降不下来
if currentAccessCount == 1 then
    redis.call("EXPIRE", accessKey, timeWindow)
end

return currentAccessCount
