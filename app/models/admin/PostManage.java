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
import models.Comments;
import models.Page;
import models.Post;



public class PostManage extends Post {
    
    public static long getCount(String filter){
        MongoCollection userCol = jongo.getCollection("post");
        long res = userCol.count(filter);
        
        return res;
    }
    
    public static void delete(ObjectId postId) {
        MongoCollection postCol = jongo.getCollection("post");

        Post post = postCol.findAndModify("{_id:#}", postId)
            .with("{$set:{deleted:#}}", true)
            .projection("{_id:1}")
            .as(Post.class);

        if (post != null)
            del("post:" + postId);
    }
    
    public static void deleteComment(ObjectId commentId) {
        MongoCollection postCol = jongo.getCollection("post");
        MongoCollection commentCol = jongo.getCollection("comment");

        Comments comments = commentCol
            .findAndModify("{comments:{$elemMatch:{_id:#}}}", commentId)
            .with("{$set:{'comments.$.deleted':#}}", true)
            .projection("{post_id:1}")
            .as(Comments.class);

        if (comments == null) {
            // a post owner can delete a another's comments
            comments = commentCol
                .findAndModify("{comments:{$elemMatch:{_id:#}}}", commentId)
                .with("{$set:{'comments.$.deleted':#}}", true)
                .projection("{post_id:1}")
                .as(Comments.class);

            if (comments == null)
                return;
        }

        postCol.update(comments.getPostId())
            .with("{$inc:{comment_count:-1},$pull:{comments:{_id:#}}}", commentId);

        del("post:" + comments.getPostId());
    }
}

