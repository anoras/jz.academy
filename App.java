import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.Verticle;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class App extends Verticle {
    public void start() {
        final String mongoNS = "vertx.mongopersistor";
        container.deployModule("vertx.mongo-persistor-v1.0");

        final EventBus eb = vertx.eventBus();

        HttpServer server = vertx.createHttpServer();

        RouteMatcher router = new RouteMatcher();
        router.get("/contacts", new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest req) {
                req.response.putHeader("content-type","application/json");
                JsonObject query = new JsonObject() {{
                    putString("action", "find");
                    putString("collection", "contacts");
                    putObject("matcher", new JsonObject());
                }};

                eb.send(mongoNS, query, new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(Message<JsonObject> result) {
                        req.response.end(result.body.getArray("results").encode());
                    }
                });
            }
        });
        router.get("/contacts/:id", new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest req) {
                req.response.putHeader("content-type","text/json");
                JsonObject query = new JsonObject() {{
                    putString("action", "findone");
                    putString("collection", "contacts");
                    putObject("matcher", new JsonObject() {{
                        putString("_id", req.params().get("id"));
                    }});
                }};
                eb.send(mongoNS, query, new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(Message<JsonObject> result) {
                        System.out.println(result.body.encode());
                        req.response.end(result.body.getObject("result").encode());
                    }
                });
            }
        });
        Handler<HttpServerRequest> postOrPutContactHandler = new Handler<HttpServerRequest>() {

            @Override
            public void handle(final HttpServerRequest req) {
                req.response.putHeader("content-type", "application/json");
                req.bodyHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(final Buffer body) {
                        String action = req.method.equals("POST") ? "Creating new" : "Updating";
                        System.out.println(action + " Contact:\n" + body.toString());
                        final JsonObject contact = new JsonObject(body.toString());
                        JsonObject record = new JsonObject();
                        record.putString("action", "save");
                        record.putString("collection", "contacts");
                        record.putObject("document", contact);
                        eb.send(mongoNS, record, new Handler<Message<JsonObject>>() {
                            @Override
                            public void handle(Message<JsonObject> result) {
                                System.out.println(result.body.toString());
                                contact.putString("_id", result.body.getString("_id"));
                                req.response.end(contact.encode());
                                if (req.method.equals("POST")) {
                                    System.out.println("Norifi");
                                    eb.send("contacts",contact);
                                }
                            }
                        });
                    }
                });
            }
        };
        router.post("/contacts", postOrPutContactHandler);
        router.put("/contacts/:id", postOrPutContactHandler);
        router.delete("/contacts/:id", new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest req) {
                req.response.putHeader("content-type","application/json");
                JsonObject query = new JsonObject() {{
                    putString("action","delete");
                    putString("collection","contacts");
                    putObject("matcher", new JsonObject() {{
                        putString("_id", req.params().get("id"));
                    }});
                }};
                eb.send(mongoNS,query);
                req.response.end();
            }
        });
        router.get("/javascripts/templates.js", new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest req) {
                req.response.putHeader("content-type","application/javascript");
                try {
                    ProcessBuilder builder = new ProcessBuilder("handlebars","templates/");
                    Process compiler = builder.start();
                    if (compiler.waitFor() != 0) throw new RuntimeException(streamToString(compiler.getErrorStream()));
                    req.response.end(streamToString(compiler.getInputStream()));
                    compiler.destroy();
                } catch (Exception e) {
                    req.response.statusCode = 500;
                    req.response.end(e.getMessage());
                }
            }
        });
        router.getWithRegEx("/javascripts/(.*\\.coffee$)", new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest req) {
                req.response.putHeader("content-type","application/javascript");
                String scriptpath = req.params().get("param0");
                try {
                    ProcessBuilder builder = new ProcessBuilder("coffee", "-p", scriptpath);
                    builder.directory(new File("static/javascripts/"));
                    Process compiler = builder.start();
                    if (compiler.waitFor() != 0) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(compiler.getErrorStream()));
                        String line;
                        StringBuilder sb = new StringBuilder();
                        while ((line = reader.readLine()) != null) sb.append(line);
                        throw new RuntimeException(sb.toString());
                    }
                    BufferedReader reader = new BufferedReader(new InputStreamReader(compiler.getInputStream()));
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = reader.readLine()) != null) sb.append(line);
                    req.response.end(sb.toString());
                    compiler.destroy();
                } catch (Exception e) {
                    req.response.statusCode = 500;
                    req.response.end(e.getMessage());
                }
            }
        });
        router.getWithRegEx("(.*)", new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest req) {
                String path = req.params().get("param0").equals("/") ? "/index.html" : req.params().get("param0");
                System.out.println("static" + path);
                try {
                    vertx.fileSystem().readFile("static" + path, new AsyncResultHandler<Buffer>() {
                        @Override
                        public void handle(AsyncResult<Buffer> event) {

                            final String content = event.result.toString();
                            System.out.println(content);
                            if (content.contains("{{contacts_json}}")) {
                                JsonObject query = new JsonObject() {{
                                    putString("action", "find");
                                    putString("collection", "contacts");
                                    putObject("matcher", new JsonObject());
                                }};

                                eb.send(mongoNS,query,new Handler<Message<JsonObject>>() {
                                    @Override
                                    public void handle(Message<JsonObject> result) {
                                        req.response.end(content.replace("{{contacts_json}}", result.body.getArray("results").encode()));
                                    }
                                });

                            } else{
                                req.response.end(event.result.toString());
                            }

                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    req.response.end();
                }
                //req.response.sendFile("static" + path);
            }
        });

        server.requestHandler(router);

        JsonArray permitted = new JsonArray() {{
            add(new JsonObject());
        }};
//        final SockJSServer sockJSServer = vertx.createSockJSServer(server);
//        sockJSServer.bridge(new JsonObject() {{
//            putString("prefix","/eventbus");
//        }}, permitted, permitted);

        server.listen(1337);
        System.out.println("...and we're up and running.");
    }

    private static String streamToString(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}