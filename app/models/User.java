package models;

import java.util.Date;
import java.util.UUID;

@lombok.Getter @lombok.Setter
public class User extends Model
{
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

        user.setPassword(null);

        return user;
    }
}
