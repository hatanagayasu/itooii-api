package controllers.constants;

public enum Error {
    BAD_REQUEST(40000, "Bad Request"),
    MISSING_PARAM(40001, "Missing Param %s"),
    MALFORMED_PARAM(40002, "Malformed Param %s"),
    MALFORMED_JSON(40003, "Malformed json"),
    MALFORMED_MULTIPART_FORM(40004, "Malformed Multipart Form"),
    INVALID_SIGNING_EXCEPTION(40005, "Invalid Signing"),

    UNAUTHORIZED(40100, "Unauthorized"),
    INCORRECT_USER(40101, "Incorrect User"),
    INCORRECT_PASSWORD(40102, "Incorrect Password"),
    INVALID_ACCESS_TOKEN(40103, "Invalid access_token"),
    USER_ALREADY_EXISTS(40104, "User already exists"),

    FORBIDDEN(40300, "Forbidden"),
    NOT_FRIEND(40301, "Not Firend"),
    INVALID_VIDEO_ACCESS_TOKEN(40302, "Invalid Video access_token"),
    INVALID_VIDEO_CHAT_ID(40303, "Invalid Video Chat Id"),
    SELF_FORBIDDEN(40304, "Self Forbidden"),
    OBJECT_FORBIDDEN(40305, "Object Forbidden"),

    NOT_FOUND(40400, "Not Found"),
    USER_NOT_FOUND(40401, "User Not Found"),

    METHOD_NOT_ALLOWED(40500, "Method Not Allowed"),

    INTERNAL_SERVER_ERROR(50000, "Internal Server Error"),
    MONGO_DUPLICATE_KEY(50001, "%s"),

    SERVICE_UNAVAILABLE(50300, "Service Unavailable");

    private final int code;
    private final String description;

    private Error(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public int getCode() {
        return code;
    }

    public String toString() {
        return code + ": " + description;
    }
}
