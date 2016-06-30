package models.admin;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Employee extends Model {
    @Id
    protected ObjectId id;
    private String name;
    @lombok.Setter
    private String password;

    public Employee() {
    }

    public Employee(String name, String password) {
        this.name = name;
        this.password = md5(password);
    }

    public void save() {
        MongoCollection col = jongo.getCollection("employee");
        col.save(this);
        password = null;
    }

    public static Employee get(ObjectId employeeId) {
        String key = "employee:" + employeeId;

        return cache(key, Employee.class, new Callable<Employee>() {
            public Employee call() {
                MongoCollection col = jongo.getCollection("employee");
                Employee employee = col.findOne(employeeId).as(Employee.class);

                return employee;
            }
        });
    }

    public static Employee getByName(String name) {
        MongoCollection col = jongo.getCollection("employee");

        Employee employee = col.findOne("{name:#}", name.toLowerCase())
            .projection("{password:1}").as(Employee.class);

        return employee;
    }

    public static Employee getByAccessToken(String token) {
        String id = getex("employee_token:" + token, 86400 * 7);

        if (id == null)
            return null;

        return get(new ObjectId(id));
    }

    public static String getEmployeeIdByAccessToken(String token) {
        String id = get("token:" + token);

        return id;
    }

    public String newAccessToken() {
        String token = UUID.randomUUID().toString();
        set("employee_token:" + token, 86400, id.toString());

        return token;
    }

    public static void deleteAccessToken(String token) {
        del("employee_token:" + token);
    }
}
