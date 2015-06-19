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

public class PostTest extends CommonTest
{
    public String accessToken;

    @BeforeClass
    public void post_access_token() throws Exception
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:9000/access_token");

        ObjectNode params = mapper.createObjectNode();
        params.put("email", "hata@isee.com.tw");
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

//    @Test
    public void post_feed() throws Exception
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = new URIBuilder("http://localhost:9000/user/feed")
            .addParameter("access_token", accessToken)
            .build();
        HttpPost httpPost = new HttpPost(uri);

        ObjectNode params = mapper.createObjectNode();

        for (int i = 0; i < 100; i++)
        {
            params.put("text", "from TestNG " + i);

            httpPost.setEntity(JsonEntity(params));
            CloseableHttpResponse response = httpClient.execute(httpPost);

            String content = getContent(response);
            System.out.println(content);
        }
    }

    @Test
    public void post_comment() throws Exception
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = new URIBuilder("http://localhost:9000/post/5565551544ae3fae7965dcc3/comment")
            .addParameter("access_token", accessToken)
            .build();
        HttpPost httpPost = new HttpPost(uri);

        ObjectNode params = mapper.createObjectNode();

        for (int i = 0; i < 100; i++)
        {
            params.put("text", "from TestNG " + i);

            httpPost.setEntity(JsonEntity(params));
            CloseableHttpResponse response = httpClient.execute(httpPost);

            String content = getContent(response);
            System.out.println(content);
        }
    }
}
