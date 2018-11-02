package ua.visicom;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.net.SocketAddress;
import io.vertx.rxjava.ext.web.RoutingContext;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;
import ua.visicom.helpers.Strings;
import static ua.visicom.helpers.Strings.substringBefore;

/**
 *
 * @author ae-kotelnikov

 */
public class LoggingVerticle extends AbstractVerticle 
{
    static org.slf4j.Logger log = LoggerFactory.getLogger(LoggingVerticle.class);
    
    Map<String, Logger> loggers = new HashMap();
    String authKey;

    @Override
    public void start() throws Exception 
    {
                
        vertx.eventBus().consumer("log-access", msg -> {
            JsonObject event = (JsonObject) msg.body();
            String key = event.getString("key");
            if(key != null) {
                String ip = event.getString("ip");
                String requestUrl = event.getString("requestUrl");
                Integer status = event.getInteger("status");
                Long millisec = event.getLong("millisec");
                String contentLength = event.getString("contentLength");
                String query = event.getString("query");
                String response = event.getString("response");
                logger(key).info("{} {} {} {} {} \n Request: \n {} \n Response: \n {}", ip, status, contentLength, millisec, requestUrl, query, response);
            }
        });
    }
    
    
    @Override
    public void stop() throws Exception {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.stop();
    }
        
    private Logger logger(String key) {

        Logger logger = loggers.get(key);
        if(logger != null)
            return logger;
        
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HH:mm:ss} %msg%n");
        encoder.setContext(lc);
        encoder.start();

        RollingFileAppender fileAppender = new RollingFileAppender();
        String fileName = Paths.get(config().getString("ROOT"), "_logs", "xml-api-access", key + ".log").toString();
        fileAppender.setContext(lc);
        fileAppender.setFile(fileName);
        fileAppender.setEncoder(encoder);
        
        TimeBasedRollingPolicy tbp = new TimeBasedRollingPolicy();
        tbp.setMaxHistory(180);
        String fileNamePattern = Paths.get(config().getString("ROOT"), "_logs", "xml-api-access", key + "-%d{yyyy-MM-dd}.log").toString();
        tbp.setFileNamePattern(fileNamePattern);
        tbp.setContext(lc);
        tbp.setParent(fileAppender);
        tbp.start();
        
        fileAppender.setRollingPolicy(tbp);
        fileAppender.setTriggeringPolicy(tbp);
        fileAppender.start();

        logger = (Logger) LoggerFactory.getLogger(key);
        logger.addAppender(fileAppender);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        
        loggers.put(key, logger);

        return logger;
    }
    
    
    public static class Handler implements io.vertx.core.Handler<RoutingContext>  
    {
        public void handle(RoutingContext context) 
        {
            long start = System.currentTimeMillis();
            
            context.addBodyEndHandler( res -> {
                String ip = remoteIP(context);
                String key = context.get("key");
                String requestUrl = context.request().path();
                MultiMap headers = context.response().headers();
                String contentLength = headers.get("Content-Length");
                int status = context.response().getStatusCode();
                String query = context.request().query();
                String requestBody = context.getBodyAsString();
                if(Strings.isNotBlank(query))
                    requestUrl = Strings.join(requestUrl, "?", ua.visicom.httpclient.HttpClient.decodeUrl(query));
                                
                String responseBody = context.get("dapi-result");
                context.vertx().eventBus().publish("log-access", new JsonObject()
                        .put("ip", ip)
                        .put("key", key)
                        .put("requestUrl", requestUrl)
                        .put("status", status)
                        .put("contentLength", contentLength)
                        .put("query", requestBody)
                        .put("response", responseBody)
                        .put("millisec", System.currentTimeMillis() - start)
                );
            });
            context.next();
        }
    
    }
    
    static String remoteIP(RoutingContext context){
        String ip = context.request().getHeader("x-forwarded-for");
        if(ip != null){
           ip = substringBefore(ip.trim(), ",");
           ip = substringBefore(ip.trim(), ":");
           return validateIPv4(ip) ? ip : "0.0.0.0";
        }
        SocketAddress sa = context.request().remoteAddress();
        ip = (sa != null && validateIPv4(sa.host())? sa.host() : "0.0.0.0");
        return ip;
    }
    
    static boolean validateIPv4(final String ip) {
        String PATTERN = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";

        return ip.matches(PATTERN);
    }
}
