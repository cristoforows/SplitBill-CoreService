package SplitBill.CoreService.SplitBillCoreService.Models;

import java.util.UUID;

public class TransactionMember {
  private UUID memberId;
  private double amount;
  private boolean isPaid;
  private boolean hasVerified;

  public TransactionMember() {
  }

  public TransactionMember(UUID memberId, double amount) {
    this.memberId = memberId;
    this.amount = amount;
    this.isPaid = false;
    this.hasVerified = false;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public void setMemberId(UUID memberId) {
    this.memberId = memberId;
  }

  public double getAmount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = amount;
  }

  public boolean getIsPaid() {
    return isPaid;
  }

  public void setIsPaid(boolean isPaid) {
    this.isPaid = isPaid;
  }

  public boolean getHasVerified() {
    return hasVerified;
  }

  public void setHasVerified(boolean hasVerified) {
    this.hasVerified = hasVerified;
  }

  public String toJsonString() {
    return "{" +
      "\"memberId\":\"" + memberId + "\"," +
      "\"amount\":" + amount + "," +
      "\"isPaid\":" + isPaid + "," +
      "\"hasVerified\":" + hasVerified +
      "}";
  }

  @Override
  public String toString() {
    return "TransactionMember{" +
      "memberId='" + memberId + '\'' +
      ", amount='" + amount + '\'' +
      ", isPaid=" + isPaid +
      ", hasVerified=" + hasVerified +
      '}';
  }
}
