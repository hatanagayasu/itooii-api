import java.net.URI;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.testng.Assert;
import org.testng.annotations.*;

public class UserTest
{
    public static final ObjectMapper mapper = new ObjectMapper();
    public String accessToken;

    @BeforeClass
    public void post_access_token() throws Exception
    {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://localhost:9000/access_token");

        ObjectNode params = mapper.createObjectNode();

        params.put("email", "hata@isee.com.tw");
        params.put("password", "P@ssw0rd");

        StringEntity stringEntity = new StringEntity(params.toString(), ContentType.create("application/json", "UTF-8"));
        httppost.setEntity(stringEntity);
        CloseableHttpResponse response = httpclient.execute(httppost);
        HttpEntity entity = response.getEntity();

        ObjectNode result = mapper.readValue(entity.getContent(), ObjectNode.class);
        accessToken = result.get("access_token").textValue();
    }

    //@Test
    public void post_user() throws Exception
    {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://localhost:9000/user");

        ObjectNode params = mapper.createObjectNode();

        params.put("email", "hata@itooii.com");
        params.put("password", "P@ssw0rd");

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

        StringEntity stringEntity = new StringEntity(params.toString(), ContentType.create("application/json", "UTF-8"));
        httppost.setEntity(stringEntity);
        CloseableHttpResponse response = httpclient.execute(httppost);
        HttpEntity entity = response.getEntity();

        System.out.println(EntityUtils.toString(entity));
    }

    @Test
    public void get_users() throws Exception
    {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        URI uri = new URIBuilder("http://localhost:9000/users")
            .addParameter("access_token", accessToken)
            .build();
        HttpGet httpget = new HttpGet(uri);
        CloseableHttpResponse response = httpclient.execute(httpget);
        HttpEntity entity = response.getEntity();

        HttpPost httppost = new HttpPost("http://localhost:9000/ready");
        ObjectNode params = mapper.createObjectNode();

        Iterator<JsonNode> result = mapper.readValue(entity.getContent(), ArrayNode.class).iterator();
        while (result.hasNext())
        {
            params.put("user_id", result.next().get("id").textValue());

            StringEntity stringEntity = new StringEntity(params.toString(), ContentType.create("application/json", "UTF-8"));
            httppost.setEntity(stringEntity);
            response = httpclient.execute(httppost);
            entity = response.getEntity();

            System.out.println(EntityUtils.toString(entity));
        }
    }
}
