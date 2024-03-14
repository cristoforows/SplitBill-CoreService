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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import io.github.cdimascio.dotenv.Dotenv;

public class TransactionVerticle extends AbstractVerticle {

  @Override
  public void start() {

    Dotenv dotenv = Dotenv.load();

    vertx.eventBus().consumer("newTransaction.handler.addr", message -> {
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
          .setHost(dotenv.get("TRANSACTION_DB_HOSTNAME"))
          .setDatabase("postgres")
          .setUser(dotenv.get("TRANSACTION_DB_USERNAME"))
          .setPassword(dotenv.get("TRANSACTION_DB_PASSWORD"));

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

        String accessKeyId = dotenv.get("AWS_ACCESS_KEY_ID");
        String secretAccessKey = dotenv.get("AWS_SECRET_ACCESS_KEY");

        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(awsCredentials);

        S3Client s3Client = S3Client.builder()
          .region(Region.AP_SOUTHEAST_1)
          .credentialsProvider(credentialsProvider)
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

                  File imageFile = new File(imageFilePath);

                  //upload picture to s3
                  PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

                  s3Client.putObject(putObjectRequest, imageFile.toPath());
                  Path source = imageFile.toPath();
                  try {
                    Files.delete(source);
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                  //add picture to transaction db
                  pool.query("UPDATE public.transaction SET bill_picture='" + key + "' WHERE transaction_id='" + transaction.getTransactionId() + "'")
                    .execute()
                    .onSuccess(res -> {
                      System.out.println("Transaction inserted to db");
                      message.reply("Transaction inserted to db");
                    })
                    .onFailure(err -> {
                      System.out.println("Error inserting picture to db");
                      err.printStackTrace();
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

    vertx.eventBus().consumer("transactionsByUserID.handler.addr", message -> {
      JsonObject data = (JsonObject) message.body();
      String userId = data.getString("userId");

      PgConnectOptions connectOptions = new PgConnectOptions()
        .setPort(5432)
        .setHost(dotenv.get("TRANSACTION_DB_HOSTNAME"))
        .setDatabase("postgres")
        .setUser(dotenv.get("TRANSACTION_DB_USERNAME"))
        .setPassword(dotenv.get("TRANSACTION_DB_PASSWORD"));

      PoolOptions poolOptions = new PoolOptions()
        .setMaxSize(10);

      Pool pool = Pool.pool(vertx, connectOptions, poolOptions);

      String query = "SELECT t.transaction_id, t.total, t.payer_id, t.transaction_date, " +
        "array_agg(tm.member_id) as member_ids, array_agg(tm.is_paid) as is_paid_statuses " +
        "FROM transaction t " +
        "JOIN transaction_member tm ON t.transaction_id = tm.transaction_id " +
        "WHERE\n" +
        "  t.transaction_id IN (\n" +
        "    SELECT DISTINCT transaction_id\n" +
        "    FROM transaction_member\n" +
        "    WHERE member_id = '" + UUID.fromString(userId) + "'  )\n" +
        "GROUP BY t.transaction_id, t.total, t.payer_id, t.transaction_date, t.bill_picture, t.group_id";

      pool
        .preparedQuery(query)
        .execute()
        .onSuccess(res -> {
          List<Transaction> transactions = new ArrayList<>();
          for (Row row : res) {
            Transaction transaction = new Transaction();
            transaction.setTransactionId(row.getUUID("transaction_id"));
            transaction.setTotalAmount(row.getDouble("total"));
            transaction.setPayerId(row.getUUID("payer_id").toString());
            transaction.setTransactionDate(row.getLocalDate("transaction_date").toString());

            List<TransactionMember> transactionMembers = new ArrayList<>();
            for (int i = 0; i < row.getArrayOfUUIDs("member_ids").length; i++) {
              TransactionMember transactionMember = new TransactionMember();
              transactionMember.setMemberId(row.getArrayOfUUIDs("member_ids")[i]);
              transactionMember.setIsPaid(row.getArrayOfBooleans("is_paid_statuses")[i]);
              transactionMembers.add(transactionMember);
            }
            transaction.setTransactionMembers(transactionMembers);
            transactions.add(transaction);
          }
          message.reply(new JsonObject(arrayOfJsonString(transactions)));
        })
        .onFailure(err -> {
          System.out.println("Error retrieving transactions");
          err.printStackTrace();
        });
    }); //handles finding transactions by user id

    vertx.eventBus().consumer("findTransaction.handler.addr", message -> {
      JsonObject data = (JsonObject) message.body();
      String transactionId = data.getString("transactionId");
      Transaction transaction = new Transaction();
      transaction.setTransactionId(UUID.fromString(transactionId));

      PgConnectOptions connectOptions = new PgConnectOptions()
        .setPort(5432)
        .setHost(dotenv.get("TRANSACTION_DB_HOSTNAME"))
        .setDatabase("postgres")
        .setUser(dotenv.get("TRANSACTION_DB_USERNAME"))
        .setPassword(dotenv.get("TRANSACTION_DB_PASSWORD"));

      PoolOptions poolOptions = new PoolOptions()
        .setMaxSize(10);

      Pool pool = Pool.pool(vertx, connectOptions, poolOptions);

      String query = "SELECT " +
        "t.transaction_id, " +
        "t.total, " +
        "t.payer_id, " +
        "t.transaction_date, " +
        "tm.member_id, " +
        "tm.is_paid, " +
        "tm.amount, " +
        "ARRAY_AGG(ti.transaction_item_id) AS item_ids, " +
        "ARRAY_AGG(ti.name) AS item_names, " +
        "ARRAY_AGG(ti.quantity) AS item_quantities, " +
        "ARRAY_AGG(ti.price) AS item_prices, " +
        "ARRAY_AGG(tia.shares) AS shares_per_item " +
        "FROM transaction t " +
        "JOIN transaction_member tm ON t.transaction_id = tm.transaction_id " +
        "JOIN transaction_item ti ON t.transaction_id = ti.transaction_id " +
        "JOIN transaction_item_assignment tia ON tia.transaction_item_id = ti.transaction_item_id " +
        "AND tia.transaction_member_id = tm.transaction_member_id " +
        "WHERE t.transaction_id = " + transaction.getTransactionId() + " " +  // Prepared statement for parameter binding
        "GROUP BY t.transaction_id, t.total, t.transaction_date, t.payer_id, tm.member_id, tm.is_paid, tm.amount;";

      pool
        .query(query)
        .execute()
        .onSuccess(res -> {
          transaction.setTransactionDate(res.iterator().next().getLocalDate("transaction_date").toString());
          transaction.setTotalAmount(res.iterator().next().getDouble("total"));
          transaction.setPayerId(res.iterator().next().getUUID("payer_id").toString());

          List<TransactionMember> transactionMembers = new ArrayList<>();
          List<TransactionItem> transactionItems = new ArrayList<>();
          for (Row row : res) {
            TransactionMember transactionMember = new TransactionMember();
            transactionMember.setMemberId(UUID.fromString(row.getUUID("member_id").toString()));
            transactionMember.setIsPaid(row.getBoolean("is_paid"));
            transactionMember.setAmount(row.getDouble("amount"));
            transactionMembers.add(transactionMember);

            for (int i = 0; i < row.getArrayOfUUIDs("item_ids").length; i++) {
              TransactionItem transactionItem = new TransactionItem();
              if (!transaction.hasTransactionItemId(row.getArrayOfUUIDs("item_ids")[i])) {
                transactionItem.setItemId(row.getArrayOfUUIDs("item_ids")[i].toString());
                transactionItem.setItemName(row.getArrayOfStrings("item_names")[i]);
                transactionItem.setQuantity(row.getArrayOfIntegers("item_quantities")[i]);
                transactionItem.setPrice(row.getArrayOfFloats("item_prices")[i]);
                transaction.addTransactionItem(transactionItem);
              }

              TransactionItemAssignment transactionItemAssignment = new TransactionItemAssignment();
              transactionItemAssignment.setTransactionItemId(row.getArrayOfUUIDs("item_ids")[i].toString());
              transactionItemAssignment.setMemberId(UUID.fromString(row.getUUID("member_id").toString()));
              transactionItemAssignment.setShares(row.getArrayOfIntegers("shares_per_item")[i]);
              transaction.addTransactionItemAssignment(transactionItemAssignment);
            }
          }
          transaction.setTransactionMembers(transactionMembers);
        });

      message.reply(new JsonObject(transaction.toJsonString()));

    }); //handles finding transaction by id

  }

  public String arrayOfJsonString(List<Transaction> transactions) {
    StringBuilder jsonString = new StringBuilder("{ \"transactions\": [");
    for (int i = 0; i < transactions.size(); i++) {
      jsonString.append(transactions.get(i).toJsonString());
      if (i != transactions.size() - 1) {
        jsonString.append(",");
      }
    }
    jsonString.append("]}");
    return jsonString.toString();
  }
}

