import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

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

public class LocalTest
{
    public static final ObjectMapper mapper = new ObjectMapper();
    public String accessToken;

    @BeforeClass
    public void post_access_token() throws Exception
    {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://localhost:9000/access_token");

        ObjectNode params = mapper.createObjectNode();

        params.put("email", "claymens@gmail.com");
        params.put("password", "P@ssw0rd");

        StringEntity stringEntity = new StringEntity(params.toString(), ContentType.create("application/json", "UTF-8"));
        httppost.setEntity(stringEntity);
        CloseableHttpResponse response = httpclient.execute(httppost);
        HttpEntity entity = response.getEntity();

        ObjectNode result = mapper.readValue(entity.getContent(), ObjectNode.class);
        accessToken = result.get("access_token").textValue();
    }

  @Test
  public void post_users() throws Exception
  {
      CloseableHttpClient httpclient = HttpClients.createDefault();
      HttpPost httppost = new HttpPost("http://localhost:9000/user");
      
      int TOTLANGNUM = 5; // total language number
  	  int MAXNATLANGNUM = 3;	// maximum allowed native language number
  	  int MAXPRALANGNUM = 5;	// maximum allowed practice language number
  	  int TOTUSERNUM = 9000;	// (integer) user number
  	  int PRALANGLVLNUM= 4;	// practice lang level number: 0=beginner, 1=intermediate, 2=advanced, 3=proficient
  	  double NatLangProb[]= {0.62, 0.25, 0.24, 0.1, 0.05};
  	  double	 PraLangProb[]= {0.32, 0.6, 0.1, 0.05, 0.05};
	  int NatLangNum, PraLangNum;
	  int l, m;
	  
      for (Integer i=0; i<TOTUSERNUM; i++)
      {
	      ObjectNode params = mapper.createObjectNode();
	      String name = UUID.randomUUID().toString();
	      params.put("name", name);
	      params.put("email", name+"@itooii.com");
	      params.put("password", "P@ssw0rd");
	      System.out.println(params);
	      	      
	      ArrayNode natives = mapper.createArrayNode();
			NatLangNum= (int) Math.round(Math.random()*MAXNATLANGNUM);
			NatLangNum= NatLangNum== 0? 1 : NatLangNum;
			l= 0; m= 0;
			while (l < TOTLANGNUM && m < MAXNATLANGNUM)
			{
				if (Math.random()<= NatLangProb[l])
				{
					natives.add(l);
					m++;
				}
				l++;
			}
			if (m==0)
				natives.add((int) Math.floor(Math.random()*TOTLANGNUM));
	      params.put("native_language", natives);
	
	      ArrayNode practices = mapper.createArrayNode();
			PraLangNum= (int) Math.round(Math.random()*MAXPRALANGNUM);
			PraLangNum= PraLangNum== 0? 1 : PraLangNum;
			l= 0; m= 0;
			while (l< TOTLANGNUM && m< MAXPRALANGNUM)
			{
				if (Math.random()<= PraLangProb[l])
				{
				    ObjectNode practice = mapper.createObjectNode();
				    practice.put("id", l);
				    practice.put("level", (int) Math.floor(Math.random()*PRALANGLVLNUM));
				    practices.add(practice);
					m++;
				}
				l++;
			}
			if (m==0)
			{
			    ObjectNode practice = mapper.createObjectNode();
			    practice.put("id", (int) Math.floor(Math.random()*TOTLANGNUM));
			    practice.put("level", (int) Math.floor(Math.random()*PRALANGLVLNUM));				
			    practices.add(practice);
			}
		    params.put("practice_language", practices);
	
	      StringEntity stringEntity = new StringEntity(params.toString(), ContentType.create("application/json", "UTF-8"));
	      httppost.setEntity(stringEntity);
	      CloseableHttpResponse response = httpclient.execute(httppost);
	      HttpEntity entity = response.getEntity();
	
	      System.out.println(EntityUtils.toString(entity));
      }
  }
  
//    @Test
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
        practice.put("id", 2);
        practice.put("level", 0);
        practices.add(practice);

        params.put("practice_language", practices);

        StringEntity stringEntity = new StringEntity(params.toString(), ContentType.create("application/json", "UTF-8"));
        httppost.setEntity(stringEntity);
        CloseableHttpResponse response = httpclient.execute(httppost);
        HttpEntity entity = response.getEntity();

        System.out.println(EntityUtils.toString(entity));
    }

//    @Test
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

        JsonNode result = mapper.readValue(entity.getContent(), ObjectNode.class);
        Iterator<JsonNode> iterator = result.get("data").iterator();
        ArrayList<String> Ids = new ArrayList<String>();
        while (iterator.hasNext())
        {
        		Ids.add(iterator.next().get("id").textValue());
        }
        while(true)
        {
        		int IdIdx = (int) Math.floor(Math.random()*Ids.size());
            ArrayNode UserIds = mapper.createArrayNode();
            UserIds.add(Ids.get(IdIdx));
            params.set("user_id", UserIds);
            StringEntity stringEntity = new StringEntity(params.toString(), ContentType.create("application/json", "UTF-8"));
            httppost.setEntity(stringEntity);
            response = httpclient.execute(httppost);
            entity = response.getEntity();

            System.out.println(EntityUtils.toString(entity));
            Thread.sleep(1);
        }
    }
}
