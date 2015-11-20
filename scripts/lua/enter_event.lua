local event_id = ARGV[1];
local user_id = ARGV[2];
local token = ARGV[3];

redis.call('ZADD', 'event:online_user_id:' .. event_id, ARGV[4], user_id)
if redis.call('SADD', 'event:token:' .. event_id, token) == 1 then
    redis.call('HSET', 'token:event', token, event_id)
    if redis.call('HINCRBY', 'event:user_id:' .. event_id, user_id, 1) == 1 then
        return redis.call('SMEMBERS', 'event:token:' .. event_id)
    end
end

return {}
