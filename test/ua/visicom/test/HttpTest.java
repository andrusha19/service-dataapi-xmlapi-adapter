package ua.visicom.test;

import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import ua.visicom.Bootstrap;
import ua.visicom.helpers.Files;
import ua.visicom.helpers.Strings;
import ua.visicom.httpclient.HttpClient;
import ua.visicom.httpclient.HttpClient.Response;
import ua.visicom.properties2.ConfigReader;

/**
 *
 * @author ae-kotelnikov
 */
public class HttpTest {
    
    static HttpClient httpClient;
    static Vertx vertx;
    static String authorityKey;
    static String preBody = " <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                            " <request>" +
                            " <authority key=\"%s\"/>" +
                            "<method name=\"%s\"/><parameters>" +
                            "<database>World_ru</database>";
    
    static String afterBody = "</parameters></request>";
    
    static String[] methodNames = {"getAddress", "IdentifyObject", "getNearest", "getPointer", "getRoute"};
    
    public HttpTest() {
    }
    
    @BeforeClass
    public static void setUpClass() throws IOException {
        JsonObject config = new JsonObject(ConfigReader.fromFile(".", "config.properties"));
        
        authorityKey = config.getString("authority.key");
        httpClient = new HttpClient("http://127.0.0.1:" + config.getString("xml.dapi.http.port"));

        vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1000*1000));    

        new Bootstrap().deploy(vertx, config).toBlocking().first();;
    }
    
    @AfterClass
    public static void tearDownClass() {
        if(vertx != null)
            vertx.close();

        Files.deleteDirectoryQuietly(Paths.get(".", ".vertx"));
        Files.deleteDirectoryQuietly(Paths.get(".", ".file-uploads"));
    }

    @Test
    public void testGetRequest() throws Exception {
        Response resp = httpClient.get("");
        assertEquals("Get response status code is not 200", 200, resp.statusCode);
        assertTrue("Get response body is null", resp.body != null);
        if(resp.body != null)
            assertTrue("Get response body is not valid", resp.body.contains("To interect with use POST method"));
    }

    @Test
    public void testMethodsEmptyBody() throws Exception {
        for(String methodName : methodNames){
            String pre = String.format(preBody, authorityKey, methodName);
            String body = "empty body";
            Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
            checkResponseStatusCodeContentTypeBody(resp, methodName);
            if(resp.body != null)
                assertTrue(methodName + " response body is not valid", resp.body.contains("Bad request"));
        }
    }   

    @Test
    public void testMethodGetAddressResponseStreet() throws Exception {
        String methodName = "getAddress";
        String pre = String.format(preBody, authorityKey, methodName);
        String body =  "<filter>" +
                            "<semantic>" +
                                "<layer type=\"name\">Settlements</layer>" +
                                "<name>Черкассы</name>" +
                            "</semantic>" +
                            "<semantic>" +
                                "<layer type=\"name\">STR</layer>" +
                                "<name>Сумгаитская</name>" +  
                            "</semantic>" +
                        "</filter>" +
                        "<response>" +
                            "<layer type=\"name\">STR</layer>" +
                        "</response>";
        Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
        checkResponseStatusCodeContentTypeBody(resp, methodName);
        if(resp.body != null)
            assertTrue(methodName + " response body is not valid", resp.body.contains("<name>Сумгаитская</name>"));
    }     
    
    @Test
    public void testMethodGetAddressResponseAddress() throws Exception {
        String methodName = "getAddress";
        String pre = String.format(preBody, authorityKey, methodName);
        String body =  "<filter>" +
                            "<semantic>" +
                                "<layer type=\"name\">Settlements</layer>" +
                                "<name>Черкассы</name>" +
                            "</semantic>" +
                            "<semantic>" +
                                "<layer type=\"name\">STR</layer>" +
                                "<name>шевченко</name>" +  
                            "</semantic>" +
                            "<semantic>" +
                                "<layer type=\"name\">ADR</layer>" +
                                "<name>56</name>" +  
                            "</semantic>" +
                        "</filter>" +
                        "<response>" +
                            "<layer type=\"name\">ADR</layer>" +
                        "</response>";
        Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
        checkResponseStatusCodeContentTypeBody(resp, methodName);
        if(resp.body != null)
            assertTrue(methodName + " response body is not valid", resp.body.contains("<name>56</name>"));
    }  
    
    @Test
    public void testMethodIdentifyObject() throws Exception {
        String methodName = "IdentifyObject";
        String pre = String.format(preBody, authorityKey, methodName);
        String body =   "<point lat=\"50.455189\" lng=\"30.511414\"/>" +
                        "<layers>" +
                            "<layer type=\"alias\">Адреса</layer>" +
                            "<layer type=\"alias\">Населенные пункты</layer>" +
                        "</layers>";
        Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
        checkResponseStatusCodeContentTypeBody(resp, methodName);
        if(resp.body != null)
            assertTrue(methodName + " response body is not valid", resp.body.contains("<name>25/2</name>"));
    }  
    
    @Test
    public void testMethodGetNearest() throws Exception {
        String methodName = "getNearest";
        String pre = String.format(preBody, authorityKey, methodName);
        String body =   "<point lat=\"50.455189\" lng=\"30.511414\"/>" +
                        "<layer type=\"alias\">Адреса</layer>";
        
        Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
        checkResponseStatusCodeContentTypeBody(resp, methodName);
        if(resp.body != null)
            assertTrue(methodName + " response body is not valid", resp.body.contains("<description>") && 
                    resp.body.contains("<id>"));
    }  
    
    @Test
    public void testMethodGetPointer() throws Exception {
        String methodName = "getPointer";
        String pre = String.format(preBody, authorityKey, methodName);
        String body =   "<objects>" +
"                           <id>ADMUA7J</id>" +
"                       </objects>";
        
        Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
        checkResponseStatusCodeContentTypeBody(resp, methodName);
        if(resp.body != null)
            assertTrue(methodName + " response body is not valid", resp.body.contains("<point lng=") && 
                    resp.body.contains("lat"));
    }  
    
    @Test
    public void testMethodGetRoute1() throws Exception {
        String methodName = "getRoute";
        String pre = String.format(preBody, authorityKey, methodName);
        String body =   "<points>" +
                            "<point lat=\"48.44154260756791\" lng=\"34.9563500390625\" type=\"start\" />" +
                            "<point lat=\"47.68778196140485\" lng=\"32.506612226876655\" type=\"finish\" />" +
                        "</points>";
        
        Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
        checkResponseStatusCodeContentTypeBody(resp, methodName);
        if(resp.body != null)
            assertTrue(methodName + " response body is not valid", resp.body.contains("<edges length="));
    } 
    
    @Test
    public void testMethodGetRoute2() throws Exception {
        String methodName = "getRoute";
        String pre = String.format(preBody, authorityKey, methodName);
        String body =   "<points>" +
                            "<point lat=\"50.5530\" lng=\"30.6066\" type=\"start\" />" +
                            "<point lat=\"50.5530\" lng=\"30.6066\" type=\"finish\" />" +
                        "</points>";
        
        Response resp = httpClient.post("", Strings.join(pre, body, afterBody));
        checkResponseStatusCodeContentTypeBody(resp, methodName);
        if(resp.body != null)
            assertTrue(methodName + " response body is not valid", resp.body.contains("<edges length=\"0\""));
    } 
    
    public void checkResponseStatusCodeContentTypeBody(Response resp, String method){
        assertEquals(method + " response status code is not 200", 200, resp.statusCode);
        assertTrue(method = " response is xml", resp.headers.get("Content-Type").toString().contains("text/xml"));
        assertTrue(method + " response body is null", resp.body != null);
    }
}
