package models;

import java.util.Date;
import java.util.UUID;

import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;
import redis.clients.jedis.Jedis;

@lombok.Getter @lombok.Setter
public class User extends Model
{
    @Id
    private String id;
    private String email;
    private String password;
    private String name;
    private Date createdTime;
    private Date modifiedTime;

    public User()
    {
    }

    public User(String id, String email, String password, String name)
    {
        Date now = new Date();

        this.id = id;
        this.email = email;
        this.password = md5(password);
        this.name = name;
        createdTime = now;
        modifiedTime = now;
    }

    public static User add(String email, String password, String name)
    {
        String id = UUID.randomUUID().toString();
        User user = new User(id, email, password, name);

        MongoCollection userCol = jongo.getCollection("user");
        userCol.save(user);

        user.setPassword(null);

        return user;
    }

    public static User getByEmail(String email)
    {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findOne("{email:#}", email).as(User.class);

        return user;
    }

    public static User getByToken(String token)
    {
        Jedis jedis = getJedis();
        String email = jedis.get("token:" + token);
        returnJedis(jedis);

        if(email == null)
            return null;

        return getByEmail(email);
    }

    public boolean matchPassword(String password)
    {
        return this.password.equals(md5(password));
    }

    public String newToken()
    {
        String token = UUID.randomUUID().toString();
        Jedis jedis =  getJedis();
        jedis.setex("token:" + token, 86400, email);
        returnJedis(jedis);

        return token;
    }

    public static void deleteToken(String token)
    {
        Jedis jedis =  getJedis();
        jedis.del("token:" + token);
        returnJedis(jedis);
    }
}
