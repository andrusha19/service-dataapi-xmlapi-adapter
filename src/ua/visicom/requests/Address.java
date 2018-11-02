package ua.visicom.requests;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.ext.web.RoutingContext;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.xpath.XPathExpressionException;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import ua.visicom.helpers.Strings;
import static ua.visicom.helpers.Strings.isBlank;
import ua.visicom.helpers.XPathHelper;

/**
 *
 * @author ae-kotelnikov
 */
public class Address {
        
    static org.slf4j.Logger log = LoggerFactory.getLogger(Address.class);
            
    XPathHelper xml;
    HttpClient httpClient;
    
    public Address(XPathHelper xml, HttpClient httpClient) {
        this.xml = xml;
        this.httpClient = httpClient;
    }
    
    
    public void exec(RoutingContext context) throws XPathExpressionException, UnsupportedEncodingException 
    {
        Handler exceptionHandler = ex -> {
            log.error(((Throwable)ex).getMessage(), ex);
            context.fail(500);
        };
                
        String authKey = context.get("key");
        String lang = context.get("lang");
        String apiUrl = context.get("apiUrl");
        
        String layerValue = xml.getString("/request/parameters/response/layer");
        final String layer;
        String firstTag;
        String lastTag;
        String openTag;
        String closeTag;
        switch(layerValue.toLowerCase()){
            case "adr":
                layer = "adr_address";
                firstTag = "<addresses>";
                lastTag = "</addresses>";
                openTag = "<address>";
                closeTag = "</address>";
                break;
            case "str":
                layer = "adr_street";
                firstTag = "<streets>";
                lastTag = "</streets>";
                openTag = "<street>";
                closeTag = "</street>";
                break;
            case "settlements":
                layer = "adm_settlement";
                firstTag = "<settlements>";
                lastTag = "</settlements>";
                openTag = "<settlement>";
                closeTag = "</settlement>";
                break;
            default:
                layer = null;
                firstTag = "";
                lastTag = "";
                openTag = "";
                closeTag = "";
        }
        
        NodeList semantics = xml.getNodes("/request/parameters/filter/semantic");
        
        if(semantics == null || semantics.getLength() == 0 || isBlank(layer)){
            context.fail(400);
            return;
        }
        
        String text = IntStream.range(0, semantics.getLength())
                .mapToObj(i -> {
                    NodeList list = semantics.item(i).getChildNodes();
                    for(int j = 0; j < list.getLength(); j++){
                        if("name".equals(list.item(j).getNodeName())){
                            return list.item(j).getTextContent();
                        }
                    }
                    return "";
                })
                .collect(Collectors.joining(" "));
        
        text = URLEncoder.encode(text, StandardCharsets.UTF_8.toString());
        
        String url = Strings.join(apiUrl, "/", lang, "/geocode.json?key=", authKey, "&text=", text);
        
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
                
                String xmlResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><response>";
                
                List<JsonObject> features = new ArrayList<>();
                if("Feature".equals(dapiResponse.getString("type")) &&
                        dapiResponse.getJsonObject("properties").getString("categories").contains(layer)){
                    features.add(dapiResponse);
                }else if("FeatureCollection".equals(dapiResponse.getString("type"))){
                    dapiResponse.getJsonArray("features").stream()
                        .filter(feat -> ((JsonObject)feat).getJsonObject("properties").getString("categories").contains(layer))
                        .forEach(feat -> features.add((JsonObject)feat));
                }
                
                if(features.isEmpty()){
                    xmlResponse += "objects not found";  
                    xmlResponse += "</response>";
                    context.put("dapi-result", xmlResponse);
                    context.response().setStatusCode(200).end(xmlResponse);
                    return;
                }

                xmlResponse += firstTag;
                
                for(int i = 0; i < features.size(); i++){
                    JsonObject feature = features.get(i);
                    
                    xmlResponse += openTag;

                    String name = "";
                    String description = "";
                    switch(layer){
                        case "adr_address":
                            name = feature.getJsonObject("properties").getString("name");
                            description = feature.getJsonObject("properties").getString("settlement");
                            description += ", " + feature.getJsonObject("properties").getString("street");
                            description += feature.getJsonObject("properties").containsKey("street_type") ? " " + feature.getJsonObject("properties").getString("street_type") : "";
                            break;
                        case "adr_street":
                            name = feature.getJsonObject("properties").getString("name");
                            name += feature.getJsonObject("properties").containsKey("street_type") ? " " + feature.getJsonObject("properties").getString("street_type") : "";
                            description = feature.getJsonObject("properties").getString("settlement");
                            break;
                        case "adm_settlement":
                            name = feature.getJsonObject("properties").getString("name");
                            description += feature.getJsonObject("properties").containsKey("level3") ? feature.getJsonObject("properties").getString("level3") + ", " : "";
                            description += feature.getJsonObject("properties").containsKey("level2") ? feature.getJsonObject("properties").getString("level2") + ", " : "";
                            description += feature.getJsonObject("properties").containsKey("level1") ? feature.getJsonObject("properties").getString("level1") : "";
                            break;
                    }
                    
                    xmlResponse += "<name>";
                    xmlResponse += name;
                    xmlResponse += "</name>";
                    
                    xmlResponse += "<description>";
                    xmlResponse += description;
                    xmlResponse += "</description>";

                    xmlResponse += "<id>";
                    xmlResponse += feature.getString("id");
                    xmlResponse += "</id>";
                    
                    xmlResponse += closeTag;
                }
                
                xmlResponse += lastTag;
                xmlResponse += "</response>";
        
                context.put("dapi-result", xmlResponse);
                context.response().setStatusCode(200).end(xmlResponse);
            });
        });

        req.exceptionHandler(exceptionHandler);

        req.end();
    }
}
