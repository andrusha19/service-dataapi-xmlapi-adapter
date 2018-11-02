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
import static ua.visicom.helpers.Strings.isBlank;
import ua.visicom.helpers.XPathHelper;
import ua.visicom.model.Pair;

/**
 *
 * @author ae-kotelnikov
 */
public class Route {
    
    static org.slf4j.Logger log = LoggerFactory.getLogger(Route.class);
    
    XPathHelper xml;
    HttpClient httpClient;
    
    public Route(XPathHelper xml, HttpClient httpClient) {
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
        String apiUrl = context.get("apiUrl");
        
        String detail = xml.getString("/request/parameters/response/@detail");
        detail = isBlank(detail) ? "" : "&geometry=path";
        
        NodeList points = xml.getNodes("/request/parameters/points/point");
        
        if(points == null || points.getLength() < 2){
            context.fail(400);
            return;
        }
                
        List<Pair> pointsList = IntStream.range(0, points.getLength())
                .mapToObj(i -> {
                    Pair pair = Pair.of(
                            points.item(i).getAttributes().getNamedItem("lng").getNodeValue() + "," + points.item(i).getAttributes().getNamedItem("lat").getNodeValue(), 
                            points.item(i).getAttributes().getNamedItem("type").getNodeValue());
                    return pair;
                })
                .collect(Collectors.toList());                
        
        if(pointsList.isEmpty()){
            context.fail(400);
            return;
        }
        
        String start = "";
        String finish = "";
        String locks = "";
        
        for(int i = 0; i < pointsList.size(); i++){
            String lnglat = pointsList.get(i).first.toString();
            if("start".equals(pointsList.get(i).second)){
                start = lnglat;
            }else if("finish".equals(pointsList.get(i).second)){
                finish = lnglat;
            }else if("stop".equals(pointsList.get(i).second)){
                locks += lnglat + "|";
            }
        }
        
        locks = isBlank(locks) ? "" : "&locks=" + locks;
        
        String url = Strings.join(apiUrl, "/core/distance.json?key=", authKey, "&origin=", start, "&destination=", finish, detail, locks);

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

                String xmlResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><response><route>";
                
                if("Feature".equals(dapiResponse.getString("type"))){
                    xmlResponse += "<edges length=\"" + dapiResponse.getJsonObject("properties").getInteger("distance") + "\">";
                    JsonArray coords = dapiResponse.getJsonObject("geometry").getJsonArray("coordinates");
                    if(!coords.isEmpty()){
                        xmlResponse += "<edge length=\"" + dapiResponse.getJsonObject("properties").getInteger("distance") + "\" name=\"\">";
                        xmlResponse += "<points>";
                        for(int i = 0; i < coords.size(); i++){
                            xmlResponse += "<point lat=\"";
                            xmlResponse += coords.getJsonArray(i).getDouble(1);
                            xmlResponse += "\" lng=\"";
                            xmlResponse += coords.getJsonArray(i).getDouble(0);
                            xmlResponse += "\" />";
                        }
                        xmlResponse += "</points>";
                        xmlResponse += "</edge>";
                    }
                    xmlResponse += "</edges>";
                }else if(dapiResponse.containsKey("distance")){
                    xmlResponse += "<edges length=\"" + dapiResponse.getInteger("distance") + "\">";
                    xmlResponse += "</edges>";
                }else{
                    xmlResponse += "Object not found";  
                }
                                
                xmlResponse += "</route></response>";
        
                context.put("dapi-result", xmlResponse);
                context.response().setStatusCode(200).end(xmlResponse);
            });
        });

        req.exceptionHandler(exceptionHandler);

        req.end();
    }
}
