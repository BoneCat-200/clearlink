-- 登录失败计数：INCR 与首次 EXPIRE 必须原子执行
-- 否则第一次请求执行完 INCR、还没来得及 EXPIRE 时进程挂掉，
-- 这个计数键就永远不过期，该用户会被永久锁定。
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
