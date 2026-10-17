-- 一次性幂等 Token 的原子消费
-- 返回：
--   -2  Token 不存在或已过期
--   -1  Token 不属于当前用户
--   >=0 消费成功，值为 Token 剩余的毫秒数（用于业务失败时按原有效期回填）
local value = redis.call('GET', KEYS[1])
if not value then
    return -2
end
if value ~= ARGV[1] then
    return -1
end
local ttl = redis.call('PTTL', KEYS[1])
redis.call('DEL', KEYS[1])
return ttl
