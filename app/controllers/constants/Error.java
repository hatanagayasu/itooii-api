package controllers.constants;

public enum Error
{
    BAD_REQUEST(40000, "Bad Request"),
    MISSING_PARAM(40001, "Missing Param %s"),
    MALFORMED_PARAM(40002, "Malformed Param %s"),

    UNAUTHORIZED(40100, "Unauthorized"),
    INCORRECT_USER(40101, "Incorrect User"),
    INCORRECT_PASSWORD(40102, "Incorrect Password"),
    MISSING_ACCESS_TOKEN(40103, "Missing access_token"),
    INVALID_ACCESS_TOKEN(40104, "Invalid access_token"),
    USER_ALREADY_EXISTS(40105, "User already exists"),

    NOT_FOUND(40400, "Not Found"),

    INTERNAL_SERVER_ERROR(50000, "Internal Server Error");

    private final int code;
    private final String description;

    private Error(int code, String description)
    {
        this.code = code;
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    public int getCode()
    {
        return code;
    }

    public String toString()
    {
        return code + ": " + description;
    }
}
