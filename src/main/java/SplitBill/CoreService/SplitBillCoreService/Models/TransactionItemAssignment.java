package SplitBill.CoreService.SplitBillCoreService.Models;

import java.util.UUID;

public class TransactionItemAssignment {
  public String transactionItemId;

  public int transactionItemSequence;
  public UUID memberId;
  public int shares;

  public TransactionItemAssignment() {
  }

  public TransactionItemAssignment(String transactionItemId, int transactionItemSequence, UUID memberId, int shares) {
    this.transactionItemId = transactionItemId;
    this.transactionItemSequence = transactionItemSequence;
    this.memberId = memberId;
    this.shares = shares;
  }

  public String getTransactionItemId() {
    return transactionItemId;
  }

  public void setTransactionItemId(String transactionItemId) {
    this.transactionItemId = transactionItemId;
  }

  public int getTransactionItemSequence() {
    return transactionItemSequence;
  }

  public void setTransactionItemSequence(int transactionItemSequence) {
    this.transactionItemSequence = transactionItemSequence;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public void setMemberId(UUID memberId) {
    this.memberId = memberId;
  }

  public int getShares() {
    return shares;
  }

  public void setShares(int shares) {
    this.shares = shares;
  }

  public String toJsonString() {
    return "{" +
      "\"transactionItemId\":\"" + transactionItemId + "\"," +
      "\"transactionItemSequence\":" + transactionItemSequence + "," +
      "\"memberId\":\"" + memberId + "\"," +
      "\"shares\":" + shares +
      "}";
  }

  @Override
  public String toString() {
    return "TransactionItemAssignment{" +
      "transactionItemId='" + transactionItemId + '\'' +
      ", transactionItemSequence=" + transactionItemSequence +
      ", memberId=" + memberId +
      ", shares=" + shares +
      '}';
  }
}
