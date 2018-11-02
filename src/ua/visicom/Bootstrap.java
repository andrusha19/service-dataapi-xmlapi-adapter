package ua.visicom;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.RxHelper;
import io.vertx.rxjava.core.Vertx;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import ua.visicom.helpers.Environment;
import ua.visicom.helpers.Files;
import ua.visicom.helpers.Objects;
import ua.visicom.http.HttpVerticle;
import ua.visicom.properties2.ConfigReader;

/**
 *
 * @author ae-kotelnikov
 */

public class Bootstrap 
{
    static Logger log = LoggerFactory.getLogger(Bootstrap.class);
    
    public static void main(String[] args) 
    {
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");
        try 
        {
            Map<String,String> config = ConfigReader.fromFile(".", "config.properties");
            Vertx vertx = Vertx.vertx(new VertxOptions()
                                        .setWorkerPoolSize(5)
                                        .setBlockedThreadCheckInterval(Environment.isDebug() ? 1000*1000 : 1000)
                                        .setFileResolverCachingEnabled(false));
            
            
            new Bootstrap().deploy(vertx, new JsonObject((Map)config))
                    .subscribe( notused -> {
                        log.info("=============================================");
                        log.info("Service-dapi-xml-adapter successfully started");
                        log.info("Http http://127.0.0.1:{}", Objects.firstNonNull(config.get("xml.dapi.http.port"), "80"));
                        log.info("=============================================");
                    }, ex -> {
                        log.error("EXIT with 1 " + ex.toString(), ex);
                        System.exit(1);
                    });
        }
        catch(IOException ex){
            log.error(ex.getMessage(), ex);
            System.exit(1);
        }
    }
    
    public Observable deploy(Vertx vertx, JsonObject config){

        DeploymentOptions options = new DeploymentOptions().setConfig(config);

        return Observable.just(true)
                .flatMap( noused -> RxHelper.deployVerticle(vertx, new HttpVerticle(), options) )
                .flatMap( noused -> RxHelper.deployVerticle(vertx, new HttpVerticle(), options) )
                .flatMap( noused -> RxHelper.deployVerticle(vertx, new LoggingVerticle(), options) )
                .doOnError( ex-> {
                    log.error("Error occured" + ex.toString(), ex);
                })
                .map( it -> {
                    addShutdownHook(vertx);
                    return it;
                }); 
    };
    
    public static void addShutdownHook(Vertx vertx) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("addShutdownHook call");
            CountDownLatch latch = new CountDownLatch(1);
            vertx.close(ar -> 
            {
              if (!ar.succeeded()) {
                log.error("Failure in stopping Vert.x", ar.cause());
              }
              latch.countDown();
            });
            try 
            {
              if (!latch.await(10, TimeUnit.SECONDS)) {
                log.error("Timed out waiting to undeploy all");
              }

              Files.deleteDirectoryQuietly(Paths.get(".", ".vertx"));
              Files.deleteDirectoryQuietly(Paths.get(".", ".file-uploads"));

            } catch (InterruptedException e) {
              throw new IllegalStateException(e);
            }
          }));
    }
}

