import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

public class CommonTest {
    public static final ObjectMapper mapper = new ObjectMapper();

    public StringEntity JsonEntity(JsonNode params) {
        ContentType contentType = ContentType.create("application/json", "UTF-8");
        return new StringEntity(params.toString(), contentType);
    }

    public String getContent(CloseableHttpResponse response) throws Exception {
        HttpEntity entity = response.getEntity();
        return EntityUtils.toString(entity);
    }
}
