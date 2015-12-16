local event_id = ARGV[1];
local user_id = ARGV[2];
local token = ARGV[3];

if user_id == '' then
    if redis.call('SREM', 'event:token:' .. event_id, token) ~= 0 then
        redis.call('HDEL', 'token:event', token)
    end

    return {}
end

if redis.call('SREM', 'event:token:' .. event_id, token) ~= 0 then
    redis.call('HDEL', 'token:event', token)
    if redis.call('HINCRBY', 'event:user_id:' .. event_id, user_id, -1) == 0 then
        redis.call('HDEL', 'event:user_id:' .. event_id, user_id)
        redis.call('ZREM', 'event:online_user_id:' .. event_id, user_id)
        return redis.call('SMEMBERS', 'event:token:' .. event_id)
    end
end

return {}
