package controllers.admin;

import models.admin.Employee;

import com.fasterxml.jackson.databind.JsonNode;

public class AppController extends controllers.AppController {
    public static Employee getEmployee(JsonNode params) {
        String token = params.get("access_token").textValue();

        return Employee.getByAccessToken(token);
    }
}
