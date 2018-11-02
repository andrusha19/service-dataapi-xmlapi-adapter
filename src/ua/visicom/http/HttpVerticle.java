package ua.visicom.http;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.visicom.LoggingVerticle;
import ua.visicom.helpers.Strings;
import static ua.visicom.helpers.Strings.firstNotNull;
import static ua.visicom.helpers.Strings.substringAfterLast;
import ua.visicom.helpers.XPathHelper;
import ua.visicom.helpers.assertion;
import ua.visicom.requests.Address;
import ua.visicom.requests.IdentifyObject;
import ua.visicom.requests.Nearest;
import ua.visicom.requests.Pointer;
import ua.visicom.requests.Route;

/**
 *
 * @author ae-kotelnikov
 */
public class HttpVerticle extends AbstractVerticle {

    static Logger log = LoggerFactory.getLogger(HttpVerticle.class);
    
    HttpClient httpClient;
    String apiUrl;
    
    public HttpVerticle() {
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        HttpServer server = vertx.createHttpServer( new HttpServerOptions()
                                .setTcpKeepAlive(true)
                                .setReuseAddress(true));
        
        
        
        Router router = Router.router(vertx);
        
        httpClient = vertx.createHttpClient(new HttpClientOptions().setConnectTimeout(10000));
        
        apiUrl = Strings.firstNotEmpty(config().getString("service.api.local.url"), config().getString("service.api.url"));
        assertion.isNotEmpty(apiUrl, "No 'service.api.url' property in config.properties");
        
        Handler logging = new LoggingVerticle.Handler();
        
        // CORS Headers
        router.route().handler( context -> {
            context.response().putHeader("Access-Control-Allow-Origin", "*");
            context.next();
        });
        router.options().handler( context -> {
            context.response().putHeader("Access-Control-Allow-Headers", "X-Requested-With, Content-Type");
            context.response().end();
        });
        
        router.get().handler(context -> context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "text/html;charset=UTF-8")
                .end("<pre>\nVisicom XML-API.\nTo interect with use POST method.\n</pre>")
        );
        
        router.post().handler(BodyHandler.create()).handler(logging).handler(process());
        
        router.route().failureHandler(this::failureHandler);
        
        int port = Strings.toInt(config().getString("xml.dapi.http.port"), 80);
        server.requestHandler(router::accept)
                .listen(port, ar -> {
                    if (ar.succeeded()) {
                        startFuture.complete();
                    } else {
                        log.error("Could not start a HTTP server", ar.cause());
                        startFuture.fail(ar.cause());
                    }

                });
    }

    @Override
    public void stop() throws Exception {
        
    }
    
    Handler<RoutingContext> process(){

        return (context) -> {          
            context.response().putHeader("Content-Type", "text/xml; charset=UTF-8");
            context.put("apiUrl", apiUrl);
            
            try{
                XPathHelper helper = new XPathHelper(context.getBodyAsString().trim());

                String method = helper.getString("/request/method/@name");
                String authorityKey = helper.getString("/request/authority/@key");
                String db = helper.getString("/request/parameters/database");
                String lang = firstNotNull(substringAfterLast(db, "_"), "uk");
                if (lang.equalsIgnoreCase("ua"))
                    lang = "uk";

                if(Strings.isBlank(authorityKey))
                    context.fail(401);
                    
                context.put("key", authorityKey);
                context.put("lang", lang);
                
                switch (method) {
                    case "getNearest": 
                        new Nearest(helper, httpClient).exec(context);  
                        break;
                    case "getPointer":
                        new Pointer(helper, httpClient).exec(context);    
                        break;
                    case "getRoute":
                        new Route(helper, httpClient).exec(context);    
                        break;
                    case "getAddress":
                        new Address(helper, httpClient).exec(context);    
                        break;
                    case "IdentifyObject":
                        new IdentifyObject(helper, httpClient).exec(context);    
                        break;
                    default:
                        context.fail(404);
                        break;
                }
                
            }catch(Exception ex){
                context.fail(500);
            }
        };        
    }

    void failureHandler(RoutingContext context) 
    {
        int statusCode = context.statusCode();
        String errorMessage = "";
        switch(statusCode){
            case 400:
                errorMessage = "Bad request";
                break;
            case 401:
                errorMessage = "Unauthorized";
                break;
            case 403:
                errorMessage = "Forbidden";
                break;    
            case 404:
                errorMessage = "Resource not found";
                break;
            case 500:
                errorMessage = "500 Internal Server Error";
                break;
            default:
                errorMessage = "500 Internal Server Error";
        }  
        context.put("dapi-result", getErrorResponse(errorMessage));
        context.response().setStatusCode(200).end(getErrorResponse(errorMessage));
    }

    public static String getErrorResponse(String errorMessage) {
        return Strings.join(
                "<?xml version='1.0' encoding='UTF-8'?>",
                "<response>",
                "<error>", errorMessage, "</error>",
                "</response>");
    }
}
