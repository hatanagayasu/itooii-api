package models.admin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

import models.User;
import models.Page;



public class UserManage extends User {

    public static long getCount(String filter){
        MongoCollection userCol = jongo.getCollection("user");
        long res = userCol.count(filter);
        
        return res;
    }
    
    public static List<User> get(String filter, Date until, int limit) {
        MongoCollection col = jongo.getCollection("user");
        String previous = null;

        MongoCursor<User> cursor = col
            .find("{" + filter +"created:{$lt:#}}", until)
            .sort("{created:-1}")
            .limit((int) limit)
            .as(User.class);

        List<User> users = new ArrayList<User>(limit);
        User user = null;
        int count = 0;
        while (cursor.hasNext()) {
            user = cursor.next();
            count++;
            users.add(user);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //if (count == limit)
        //    previous = String.format("until=%d&limit=%d", user.getCreatedTime().getTime(), limit);

        return users;
    }
}