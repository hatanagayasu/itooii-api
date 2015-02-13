package controllers;

@lombok.Getter
public class Result
{
    private int status;
    private Object object;

    Result(int status)
    {
        this.status = status;
    }

    Result(int status, Object object)
    {
        this.status = status;
        this.object = object;
    }

    public String toString()
    {
        return "{\"status\":\"" + status + "\"" + (object == null ? "" :
            ",\"body\":" + object.toString()) + "}";
    }
}
