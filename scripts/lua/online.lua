local user_id = ARGV[1];
local token = ARGV[2];

if redis.call('SADD', 'online:token', token) == 1 then
    redis.call('ZADD', 'online:user_id', ARGV[3], user_id)
    if redis.call('HINCRBY', 'online:count', user_id, 1) == 1 then
        return 1
    end
end

return 0
