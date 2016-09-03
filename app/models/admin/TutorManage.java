package models.admin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;

import com.fasterxml.jackson.databind.JsonNode;

import models.Post;
import models.User;

public class TutorManage extends Post {

    public static List<Post> get(String filter, Date until, int limit) {
        MongoCollection col = jongo.getCollection("post");
        String previous = null;

        MongoCursor<Post> cursor = col
            .find("{" + filter +"created:{$lt:#}}", until)
            .sort("{created:-1}")
            .limit((int) limit)
            .as(Post.class);

        List<Post> tutors = new ArrayList<Post>(limit);
        Post tutor = null;
        int count = 0;
        while (cursor.hasNext()) {
            tutor = cursor.next();
            count++;
            tutors.add(tutor);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //if (count == limit)
        //    previous = String.format("until=%d&limit=%d", user.getCreatedTime().getTime(), limit);

        return tutors;
    }
    
    public static void update(ObjectId postId, JsonNode params) {
        String status = params.get("status").asText();
        MongoCollection postCol = jongo.getCollection("post");

        postCol.update(postId).with("{$set:{'status':#}}", status);
    }
}
