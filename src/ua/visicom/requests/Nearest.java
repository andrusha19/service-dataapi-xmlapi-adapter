package ua.visicom.requests;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.ext.web.RoutingContext;
import javax.xml.xpath.XPathExpressionException;
import org.slf4j.LoggerFactory;
import ua.visicom.helpers.Strings;
import static ua.visicom.helpers.Strings.isBlank;
import ua.visicom.helpers.XPathHelper;

/**
 *
 * @author ae-kotelnikov
 */
public class Nearest {
    
    static org.slf4j.Logger log = LoggerFactory.getLogger(Nearest.class);
    
    XPathHelper xml;
    HttpClient httpClient;

    public Nearest(XPathHelper xml, HttpClient httpClient) {
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
        
        String lat = xml.getString("/request/parameters/point/@lat");
        String lng = xml.getString("/request/parameters/point/@lng");
        String radius = xml.getString("/request/parameters/point/@radius");
        radius = isBlank(radius) ? "" : "&radius=" + radius;
//        String limit = isBlank(radius) ? "&limit=1" : "";
        String limit = "&limit=1";
//        String layer = xml.getString("/request/parameters/point/@lng");
        String layer = "adr_address";
        
        if(isBlank(lat) || isBlank(lng) || isBlank(layer)){
            context.fail(400);
            return;
        }
        
        String url = Strings.join(apiUrl, "/", lang, "/search/", layer, ".json?key=", authKey, radius, limit, "&near=", lng, ",", lat);
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
                
                String xmlResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><response><object>";
                
                if("Feature".equals(dapiResponse.getString("type"))){
                    xmlResponse += "<name>";
                    xmlResponse += dapiResponse.getJsonObject("properties").getString("name");
                    xmlResponse += "</name>";
                    
                    xmlResponse += "<description>";
                    String description = dapiResponse.getJsonObject("properties").getString("settlement");
                    description += ", " + dapiResponse.getJsonObject("properties").getString("street");
                    description += " " + dapiResponse.getJsonObject("properties").getString("street_type");
                    xmlResponse += description;
                    xmlResponse += "</description>";
                   
                    xmlResponse += "<id>";
                    xmlResponse += dapiResponse.getString("id");
                    xmlResponse += "</id>";
                    
                    xmlResponse += "<distance>";
                    xmlResponse += dapiResponse.getJsonObject("properties").getInteger("dist_meters");
                    xmlResponse += "</distance>";
                }else{
                    xmlResponse += "Object not found";  
                }
               
                xmlResponse += "</object></response>";
        
                context.put("dapi-result", xmlResponse);
                context.response().setStatusCode(200).end(xmlResponse);
            });
        });

        req.exceptionHandler(exceptionHandler);

        req.end();
    }
}
