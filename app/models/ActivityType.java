package models;

public enum ActivityType {
    follow(1),
    post(2),
    commentYourPost(3),
    likeYourPost(4),
    likeYourComment(5),
    commentPostYouComment(6),
    likePostYouComment(7),
    ownerCommentPostYouLike(8),
    mention(9),
    createEvent(10),
    joinEvent(11),
    followYou(12),
    changeProfilePhoto(13),
    hi(14),
    postOnEvent(15),
    postOnEventYouJoin(16);

    private int type;

    private ActivityType(int type) {
        this.type = type;
    }

    public int value() {
        return type;
    }
}
