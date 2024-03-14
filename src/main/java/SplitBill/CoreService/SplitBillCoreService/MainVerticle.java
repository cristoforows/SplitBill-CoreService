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
      .handler(this::NewTransactionHandler); //handles new transaction submission

    router.get("/api/v1/transaction/user/:userId")
      .handler(this::AllTransactionsHandler); //handles getting all transactions for a user

    router.get("/api/v1/transaction/transaction/:transactionId")
      .handler(this::FindTransactionByIDHandler); //handles getting a transaction by id

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .onSuccess(server ->
        System.out.println(
          "HTTP server started on port " + server.actualPort()
        )
      );
  }

  private void NewTransactionHandler(RoutingContext routingContext) {
    // Extract JSON data
    JsonObject jsonData = new JsonObject(routingContext.request().getFormAttribute("payload"));

    List<FileUpload> fileUploads = routingContext.fileUploads();

    String imageFilePath = null;

    if (!fileUploads.isEmpty()) {
      FileUpload fileUpload = fileUploads.get(0);
      imageFilePath = saveFileAndGetPath(fileUpload);
    }

    JsonObject message = new JsonObject()
      .put("jsonData", jsonData)
      .put("imageFilePath", imageFilePath);

    vertx.eventBus().request("newTransaction.handler.addr", message, reply -> {
      if (reply.succeeded()) {
        routingContext.response().putHeader("Content-type", "application/json").end(reply.result().body().toString());
      } else {
        routingContext.response().end("Transaction could not be saved");
      }
    });
  }

  private void AllTransactionsHandler(RoutingContext routingContext) {
    System.out.println("start here");
    // send user id to get all transactions
    JsonObject message = new JsonObject()
      .put("userId", routingContext.request().getParam("userId"));
    System.out.println("message: " + message.toString());
    vertx.eventBus().request("transactionsByUserID.handler.addr", message, reply -> {
      if (reply.succeeded()) {
        routingContext.response().putHeader("Content-type", "application/json").end(reply.result().body().toString());
      } else {
        routingContext.response().end("Transactions could not be retrieved");
      }
    });
  }

  private void FindTransactionByIDHandler(RoutingContext routingContext) {
    // send transaction id to get transaction
    JsonObject message = new JsonObject()
      .put("transactionId", routingContext.request().getParam("transactionId"));

    vertx.eventBus().request("findTransaction.handler.addr", message, reply -> {
      if (reply.succeeded()) {
        routingContext.response().putHeader("Content-type", "application/json").end(reply.result().body().toString());
      } else {
        routingContext.response().end("Transaction could not be retrieved");
      }
    });
  }

  private String saveFileAndGetPath(FileUpload fileUpload) {
    String originalFilename = fileUpload.fileName();
    String uniqueFilename = generateUniqueFilename(originalFilename);

    Path source = Paths.get(fileUpload.uploadedFileName());
    Path destination = Paths.get(uniqueFilename);

    try {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
      return destination.toString();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
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
