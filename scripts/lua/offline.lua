local user_id = ARGV[1];
local token = ARGV[2];

if redis.call('SREM', 'online:token', token) ~= 0 then
    if redis.call('HINCRBY', 'online:count', user_id, -1) == 0 then
        redis.call('ZREM', 'online:user_id', user_id)
        return 1
    end
end

return 0
