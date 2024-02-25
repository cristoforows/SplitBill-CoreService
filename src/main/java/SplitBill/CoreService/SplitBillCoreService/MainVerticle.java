package SplitBill.CoreService.SplitBillCoreService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;


public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {

    vertx.deployVerticle(new TransactionVerticle());

    Router router = Router.router(vertx);

    router.post("/api/v1/transaction")
      .consumes("multipart/form-data")
      .handler(BodyHandler.create())
      .handler(this::TransactionHandler); //handles new transaction submission

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .onSuccess(server ->
        System.out.println(
          "HTTP server started on port " + server.actualPort()
        )
      );
  }

  private void TransactionHandler(RoutingContext routingContext) {
    // Extract JSON data
    JsonObject jsonData = new JsonObject(routingContext.request().getFormAttribute("payload"));

    List<FileUpload> fileUploads = routingContext.fileUploads();

    if (!fileUploads.isEmpty()) {
      // Image is uploaded, handle the first file
      FileUpload fileUpload = fileUploads.get(0);
      String originalFilename = fileUpload.fileName();
      String uniqueFilename = generateUniqueFilename(originalFilename);

      Path source = Paths.get(fileUpload.uploadedFileName());
      Path destination = Paths.get(uniqueFilename);

      try {
        // Use Files.move to save the file to the destination
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        e.printStackTrace();
        routingContext.response().end("Error saving the file");
        return;
      }
      vertx.eventBus().request("transaction.handler.addr", new JsonObject()
        .put("jsonData", jsonData)
        .put("imageFilePath", destination.toString()), reply -> {
        if (reply.succeeded()) {
          routingContext.response().putHeader("Content-type", "application/json").end(reply.result().body().toString());
        } else {
          System.out.println(reply.cause());
          routingContext.response().end("Transaction could not be saved");
        }
      });
    } else {
      vertx.eventBus().request("transaction.handler.addr", new JsonObject()
        .put("jsonData", jsonData), reply -> {
        if (reply.succeeded()) {
          routingContext.response().putHeader("Content-type", "application/json").end(reply.result().body().toString());
        } else {
          System.out.println(reply.cause());
          routingContext.response().end("Transaction could not be saved");
        }
      });
    }
  }

  private String generateUniqueFilename(String originalFilename) {
    long timestamp = System.currentTimeMillis();
    String timestampStr = String.valueOf(timestamp);

    // Extract file extension (if any) from the original filename
    String fileExtension = "";
    int dotIndex = originalFilename.lastIndexOf('.');
    if (dotIndex != -1) {
      fileExtension = originalFilename.substring(dotIndex);
    }

    // Combine timestamp, original filename (without extension), and file extension
    return "src/main/resources/" + timestampStr + "_" + originalFilename.replace(fileExtension, "") + fileExtension;
  }
}
