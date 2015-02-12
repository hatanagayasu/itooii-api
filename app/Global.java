import play.*;
import play.mvc.*;
import play.mvc.Http.*;
import play.libs.F.*;

import static play.mvc.Results.*;

import play.GlobalSettings;
import play.api.mvc.EssentialFilter;
import play.filters.gzip.GzipFilter;

public class Global extends GlobalSettings
{
    public Promise<Result> onBadRequest(RequestHeader request, String error)
    {
        return Promise.<Result>pure(badRequest(error));
    }

    public <T extends EssentialFilter> Class<T>[] filters()
    {
        return new Class[]{GzipFilter.class};
    }
}
