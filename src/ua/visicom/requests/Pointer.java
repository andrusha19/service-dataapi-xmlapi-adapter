package ua.visicom.requests;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.ext.web.RoutingContext;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.xpath.XPathExpressionException;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import ua.visicom.helpers.Strings;
import ua.visicom.helpers.XPathHelper;

/**
 *
 * @author ae-kotelnikov
 */
public class Pointer {

    static org.slf4j.Logger log = LoggerFactory.getLogger(Pointer.class);
    
    XPathHelper xml;
    HttpClient httpClient;
    
    public Pointer(XPathHelper xml, HttpClient httpClient) {
        this.xml = xml;
        this.httpClient = httpClient;
    }
    
    
    public void exec(RoutingContext context) throws XPathExpressionException 
    {
        Handler exceptionHandler = ex -> {
            log.error(((Throwable)ex).getMessage(), ex);
            context.fail(500);
        };
        
        String authKey = context.get("key");
        String lang = context.get("lang");
        String apiUrl = context.get("apiUrl");
                
        NodeList features = xml.getNodes("/request/parameters/objects/id");

        if(features == null || features.getLength() == 0){
            context.fail(400);
            return;
        }
        
        List featuresList = IntStream.range(0, features.getLength())
                .mapToObj(i -> features.item(i).getTextContent())
                .collect(Collectors.toList());
               
        if(featuresList.isEmpty()){
            context.fail(400);
            return;
        }
        
        String url = Strings.join(apiUrl, "/", lang, "/feature/", String.join(",", featuresList), ".json?key=", authKey, "&geometry=no");

        HttpClientRequest req = httpClient.getAbs(url);            
        
        req.handler( res -> {
            if(res.statusCode() != 200){
                log.error("Error from dapi response. Status code: " + res.statusCode());
                context.fail(res.statusCode());
                return;
            }
            
            res.exceptionHandler(exceptionHandler);
                                
            res.bodyHandler( buf -> {
                JsonObject dapiResponse = buf.toJsonObject();
                
                String xmlResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><response><points>";
                
                if("Feature".equals(dapiResponse.getString("type"))){
                    for(int i = 0; i < featuresList.size(); i++){
                        if(!featuresList.get(i).equals(dapiResponse.getString("id"))){
                            xmlResponse += "<point>Object not found</point>"; 
                        }else{
                            double lng = dapiResponse.getJsonObject("geo_centroid").getJsonArray("coordinates").getDouble(0);
                            double lat = dapiResponse.getJsonObject("geo_centroid").getJsonArray("coordinates").getDouble(1);
                            xmlResponse += String.format("<point lng=\"%s\" lat=\"%s\" />", lng, lat);
                        }
                    }
                }else if("FeatureCollection".equals(dapiResponse.getString("type"))){
                    JsonArray arr = dapiResponse.getJsonArray("features");
                    for(int i = 0, j = 0; i < featuresList.size(); i++){
                        if(!featuresList.get(i).equals(arr.getJsonObject(j).getString("id"))){
                            xmlResponse += "<point>Object not found</point>";                            
                        }else{                        
                            double lng = arr.getJsonObject(j).getJsonObject("geo_centroid").getJsonArray("coordinates").getDouble(0);
                            double lat = arr.getJsonObject(j).getJsonObject("geo_centroid").getJsonArray("coordinates").getDouble(1);
                            xmlResponse += String.format("<point lng=\"%s\" lat=\"%s\" />", lng, lat);
                            j++;
                        }
                    }
                }else{
                    for(int i = 0; i < featuresList.size(); i++){
                        xmlResponse += "<point>Object not found</point>";  
                    }
                }

                xmlResponse += "</points></response>";
        
                context.put("dapi-result", xmlResponse);
                context.response().setStatusCode(200).end(xmlResponse);
            });
        });

        req.exceptionHandler(exceptionHandler);

        req.end();
    }
}
