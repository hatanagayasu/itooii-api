import play.*;
import play.mvc.*;
import play.mvc.Http.*;
import play.libs.F.*;
import static play.mvc.Results.*;

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
}
