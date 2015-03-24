import play.*;
import play.mvc.*;
import play.mvc.Http.*;
import play.libs.F.*;

import static play.mvc.Results.*;

import play.GlobalSettings;
import play.api.mvc.EssentialFilter;
import play.filters.gzip.GzipFilter;

public class Global extends GlobalSettings {
    public void onStart(Application app) {
        Logger.info("Application has started");

        models.Model.init();
    }

    public void onStop(Application app) {
        Logger.info("Application shutdown...");
    }

    public Promise<Result> onBadRequest(RequestHeader request, String error) {
        return Promise.<Result> pure(badRequest(error));
    }

    public <T extends EssentialFilter> Class<T>[] filters() {
        return new Class[] { GzipFilter.class };
    }
}
