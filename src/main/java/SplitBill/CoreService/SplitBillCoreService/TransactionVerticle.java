package SplitBill.CoreService.SplitBillCoreService;

import SplitBill.CoreService.SplitBillCoreService.Models.Transaction;
import SplitBill.CoreService.SplitBillCoreService.Models.TransactionItem;
import SplitBill.CoreService.SplitBillCoreService.Models.TransactionItemAssignment;
import SplitBill.CoreService.SplitBillCoreService.Models.TransactionMember;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TransactionVerticle extends AbstractVerticle {

  @Override
  public void start() {
    vertx.eventBus().consumer("transaction.handler.addr", message -> {
      JsonObject data = (JsonObject) message.body();
      // Retrieve JSON data and image file path
      JsonObject jsonPayload = data.getJsonObject("jsonData");
      String imageFilePath = data.getString("imageFilePath");


      ObjectMapper mapper = new ObjectMapper();
      try {
        Transaction transaction = mapper.readValue(jsonPayload.toString(), Transaction.class);

        // save transaction to db
        PgConnectOptions connectOptions = new PgConnectOptions()
          .setPort(5432)
          .setHost("billy-transaction-service.crgqkgekiynr.ap-southeast-1.rds.amazonaws.com")
          .setDatabase("postgres")
          .setUser("billy")
          .setPassword("IemNTU2023!");

        PoolOptions poolOptions = new PoolOptions()
          .setMaxSize(10);

        Pool pool = Pool.pool(vertx, connectOptions, poolOptions);

        List<Tuple> transactionItems = new ArrayList<>();
        for (TransactionItem transactionItem : transaction.getTransactionItems()) {
          transactionItems.add(Tuple.of(transactionItem.getItemSequence(), transactionItem.getItemName(), transactionItem.getQuantity(), transactionItem.getPrice()));
        }

        List<Tuple> transactionMembers = new ArrayList<>();
        for (TransactionMember transactionMember : transaction.getTransactionMembers()) {
          if (Objects.equals(transactionMember.getMemberId(), transaction.getPayerId())) {
            transactionMember.setIsPaid(true);
          }
          transactionMembers.add(Tuple.of(transactionMember.getMemberId(), transactionMember.getAmount(), transactionMember.getIsPaid()));
        }

        List<Tuple> transactionItemAssignments = new ArrayList<>();
        for (TransactionItemAssignment transactionItemAssignment : transaction.getTransactionItemAssignments()) {
          transactionItemAssignments.add(Tuple.of(transactionItemAssignment.getMemberId(), transactionItemAssignment.getTransactionItemSequence(), transactionItemAssignment.getShares()));
        }

        S3Client s3Client = S3Client.builder()
          .region(Region.AP_SOUTHEAST_1)
          .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
          .build();

        // insert transaction to db and get the id
        pool.getConnection()
          .onSuccess(conn ->
            conn.begin()
              .compose(tx -> conn
                .query("INSERT INTO public.transaction (payer_id, total, transaction_date, group_id, created_by) " +
                  "VALUES ('" + transaction.getPayerId() +
                  "', " + transaction.getTotalAmount() +
                  ", '" + transaction.getTransactionDate() + "', " + transaction.getGroupId() + ", '" + transaction.getPayerId() + "') returning transaction_id")
                .execute()
                .onSuccess(res1 -> {
                  Row row = res1.iterator().next();
                  transaction.setTransactionId(row.getUUID("transaction_id"));
                })
                .compose(res1 ->
                  conn
                    .preparedQuery("INSERT INTO public.transaction_item (item_sequence, transaction_id, name, quantity, price ) " +
                      "VALUES ($1, '" + transaction.getTransactionId() + "', $2, $3, $4)")
                    .executeBatch(transactionItems)
                    .compose(res2 ->
                      conn.preparedQuery("INSERT INTO public.transaction_member (transaction_id, member_id, amount, is_paid) " +
                          "VALUES ('" + transaction.getTransactionId() + "', $1, $2, $3) returning transaction_member_id")
                        .executeBatch(transactionMembers)
                        .compose(res3 -> conn
                          .preparedQuery("INSERT INTO public.transaction_item_assignment (transaction_member_id, transaction_item_id, shares) " +
                            "VALUES ((SELECT transaction_member_id FROM transaction_member WHERE member_id=$1 AND transaction_id='" + transaction.getTransactionId() + "')," +
                            "(SELECT transaction_item_id FROM transaction_item WHERE item_sequence=$2 AND transaction_id='" + transaction.getTransactionId() + "') , $3)")
                          .executeBatch(transactionItemAssignments)))
                ).compose(res4 -> tx.commit())
              )
              .eventually(() -> conn.close())
              .onSuccess(v -> {
                if (imageFilePath != null) {
                  String bucketName = "billy-transactions-s3";
                  String key = transaction.getTransactionId() + "_" + LocalDate.now() + ".jpg";
                  System.out.println("Uploading picture to s3 with key: " + key);

                  File imageFile = new File(imageFilePath);

                  //upload picture to s3
                  PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

                  s3Client.putObject(putObjectRequest, imageFile.toPath());

                  //add picture to transaction db
                  pool.getConnection()
                    .onSuccess(conn2 -> {
                      conn2.query("UPDATE public.transaction SET bill_picture='" + key + "' WHERE transaction_id='" + transaction.getTransactionId() + "'")
                        .execute()
                        .onSuccess(res -> {
                          System.out.println("Transaction inserted to db");
                          message.reply("Transaction inserted to db");
                        })
                        .onFailure(err -> {
                          System.out.println("Error inserting picture to db");
                          err.printStackTrace();
                        });
                    });
                }
              })
              .onFailure(err -> {

                System.out.println("Error inserting transaction to db");
                err.printStackTrace();
              })
          );
        //chained transaction to insert transaction, transaction_items, transaction_members, and transaction_item_assignments, and picture

      } catch (IOException e) {
        System.out.println("Error parsing json payload");
        e.printStackTrace();
      }

    });

  }
}

