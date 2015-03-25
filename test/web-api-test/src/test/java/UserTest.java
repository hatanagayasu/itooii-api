import java.net.URI;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.testng.Assert;
import org.testng.annotations.*;

public class UserTest extends CommonTest
{
    public String accessToken;

    @BeforeClass
    public void post_access_token() throws Exception
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:9000/access_token");

        ObjectNode params = mapper.createObjectNode();
        params.put("email", "hata@itooii.com");
        params.put("password", "P@ssw0rd");

        httpPost.setEntity(JsonEntity(params));
        CloseableHttpResponse response = httpClient.execute(httpPost);
        String content = getContent(response);

        JsonNode result = mapper.readValue(content, JsonNode.class);
        accessToken = result.get("access_token").textValue();
    }

    @AfterClass
    public void delete_access_token() throws Exception
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = new URIBuilder("http://localhost:9000/access_token")
            .addParameter("access_token", accessToken)
            .build();
        HttpDelete httpDelete = new HttpDelete(uri);
        CloseableHttpResponse response = httpClient.execute(httpDelete);
    }

    @Test
    public void post_user() throws Exception
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:9000/user");

        ObjectNode params = mapper.createObjectNode();

        params.put("email", "hata@itooii.com");
        params.put("password", "P@ssw0rd");
        params.put("name", "hata");

        ArrayNode natives = mapper.createArrayNode();
        natives.add(1);
        natives.add(3);
        natives.add(5);
        params.put("native_language", natives);

        ArrayNode practices = mapper.createArrayNode();
        ObjectNode practice = mapper.createObjectNode();
        practice.put("id", 1);
        practice.put("level", 0);
        practices.add(practice);

        params.put("practice_language", practices);

        httpPost.setEntity(JsonEntity(params));
        CloseableHttpResponse response = httpClient.execute(httpPost);
    }

    @Test
    public void get_users() throws Exception
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet("http://localhost:9000/users");

        CloseableHttpResponse response = httpClient.execute(httpGet);
        String content = getContent(response);

        ObjectNode params = mapper.createObjectNode();
        ArrayNode ids = params.putArray("user_id");

        JsonNode result = mapper.readValue(content, JsonNode.class);
        Iterator<JsonNode> iterator = result.get("data").iterator();
        while (iterator.hasNext())
            ids.add(iterator.next().get("id").textValue());

        HttpPost httpPost = new HttpPost("http://localhost:9000/ready");
        httpPost.setEntity(JsonEntity(params));
        response = httpClient.execute(httpPost);
    }
}
