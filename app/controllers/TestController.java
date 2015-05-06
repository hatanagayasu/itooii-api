package controllers;

import com.fasterxml.jackson.databind.JsonNode;

public class TestController extends AppController {
    public static Result echo(JsonNode params) {
        return Ok(params);
    }

    public static play.mvc.Result verifyEmail() {
        String link = "http://localhost:9999/account/verify-email/" +
            "96278552-8abc-41be-a9b8-bfa52c5f13c2";

        return ok(views.html.Email.verify_email.render(link));
    }
}
