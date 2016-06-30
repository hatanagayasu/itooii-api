package controllers.admin;

import controllers.Result;
import controllers.constants.Error;

import models.admin.Employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EmployeesController extends AppController {
    public static Result add(JsonNode params) {
        String name = params.get("name").textValue();
        String password = params.get("password").textValue();

        if (Employee.getByName(name) != null)
            return Error(Error.USER_ALREADY_EXISTS);

        Employee employee = new Employee(name, password);

        employee.save();

        return Ok(employee);
    }

    public static Result me(JsonNode params) {
        Employee employee = getEmployee(params);
        if (employee == null)
            return Error(Error.INVALID_ACCESS_TOKEN);

        employee.setPassword(null);

        return Ok(employee);
    }

    public static Result login(JsonNode params) {
        String name = params.get("name").textValue();
        String password = params.get("password").textValue();

        Employee employee = Employee.getByName(name);

        if (employee == null)
            return Error(Error.INCORRECT_USER);

        if (!employee.getPassword().equals(Employee.md5(password)))
            return Error(Error.INCORRECT_PASSWORD);

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", employee.newAccessToken());

        return Ok(result);
    }

    public static Result logout(JsonNode params) {
        String token = params.get("access_token").textValue();

        Employee.deleteAccessToken(token);

        return Ok();
    }
}
