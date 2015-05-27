package models;

public enum ActivityType {
    follow(1),
    post(2),
    commentYoutPost(3),
    likeYourPost(4),
    likeYourComment(5),
    commentPostYouComment(6),
    likePostYouComment(7),
    ownerCommentPostYouLike(8),
    mention(9);

    private int type;

    private ActivityType(int type) {
        this.type = type;
    }

    public int value() {
        return type;
    }
}
