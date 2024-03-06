package SplitBill.CoreService.SplitBillCoreService.Models;

import com.ctc.wstx.shaded.msv_core.datatype.xsd.DateTimeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Transaction {
  private String transactionId;
  private String payerId;
  private String billPicture;
  private double totalAmount;
  private String transactionDate;
  private String groupId;
  private List<TransactionItem> transactionItems;
  private List<TransactionMember> transactionMembers;
  private List<TransactionItemAssignment> transactionItemAssignments;

  public Transaction() {
  }

  public Transaction(String transactionId, String payerId, String billPicture, double totalAmount, String transactionDate, String groupId, List<TransactionItem> transactionItems, List<TransactionMember> transactionMembers, List<TransactionItemAssignment> transactionItemAssignments) {
    this.transactionId = transactionId;
    this.payerId = payerId;
    this.billPicture = billPicture;
    this.totalAmount = totalAmount;
    this.transactionDate = transactionDate;
    this.groupId = groupId;
    this.transactionItems = transactionItems;
    this.transactionMembers = transactionMembers;
    this.transactionItemAssignments = transactionItemAssignments;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(UUID transactionId) {
    this.transactionId = transactionId.toString();
  }

  public String getPayerId() {
    return payerId;
  }

  public void setPayerId(String payerId) {
    this.payerId = payerId;
  }

  public String getBillPicture() {
    return billPicture;
  }

  public void setBillPicture(String billPicture) {
    this.billPicture = billPicture;
  }

  public double getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(double totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getTransactionDate() {
    return transactionDate;
  }

  public void setTransactionDate(String transactionDate) {
    this.transactionDate = transactionDate;
  }

  public String getGroupId() {
    if (groupId != null) {
      return "'" + groupId + "'";
    } else {
      return null;
    }
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public List<TransactionItem> getTransactionItems() {
    return transactionItems;
  }

  public void setTransactionItems(List<TransactionItem> transactionItems) {
    this.transactionItems = transactionItems;
  }

  public void addTransactionItem(TransactionItem transactionItem) {
    this.transactionItems.add(transactionItem);
  }

  public List<TransactionMember> getTransactionMembers() {
    return transactionMembers;
  }

  public void setTransactionMembers(List<TransactionMember> transactionMembers) {
    this.transactionMembers = transactionMembers;
  }

  public void addTransactionMember(TransactionMember transactionMember) {
    this.transactionMembers.add(transactionMember);
  }

  public List<TransactionItemAssignment> getTransactionItemAssignments() {
    return transactionItemAssignments;
  }

  public void setTransactionItemAssignments(List<TransactionItemAssignment> transactionItemAssignments) {
    this.transactionItemAssignments = transactionItemAssignments;
  }

  public void addTransactionItemAssignment(TransactionItemAssignment transactionItemAssignment) {
    this.transactionItemAssignments.add(transactionItemAssignment);
  }

  public String membersArraytoJsonString(List<TransactionMember> members) {
    if (members == null || members.isEmpty()) {
      return "[]";
    }
    StringBuilder membersJson = new StringBuilder("[");
    for (TransactionMember member : members) {
      membersJson.append(member.toJsonString()).append(",");
    }
    membersJson.deleteCharAt(membersJson.length() - 1);
    membersJson.append("]");
    return membersJson.toString();
  }

  public String itemsArrayToJsonString(List<TransactionItem> items) {
    if (items == null || items.isEmpty()) {
      return "[]";
    }
    StringBuilder itemsJson = new StringBuilder("[");
    for (TransactionItem item : items) {
      itemsJson.append(item.toJsonString()).append(",");
    }
    itemsJson.deleteCharAt(itemsJson.length() - 1);
    itemsJson.append("]");
    return itemsJson.toString();
  }

  public String assignmentsArrayToJsonString(List<TransactionItemAssignment> assignments) {
    if (assignments == null || assignments.isEmpty()) {
      return "[]";
    }
    StringBuilder assignmentsJson = new StringBuilder("[");
    for (TransactionItemAssignment assignment : assignments) {
      assignmentsJson.append(assignment.toJsonString()).append(",");
    }
    assignmentsJson.deleteCharAt(assignmentsJson.length() - 1);
    assignmentsJson.append("]");
    return assignmentsJson.toString();
  }

  public String toJsonString() {
    //check for null items members assign

    return "{" +
      "\"transactionId\":\"" + transactionId + "\"," +
      "\"payerId\":\"" + payerId + "\"," +
      "\"billPicture\":\"" + billPicture + "\"," +
      "\"totalAmount\":" + totalAmount + "," +
      "\"transactionDate\":\"" + transactionDate + "\"," +
      "\"groupId\":\"" + groupId + "\"," +
      "\"transactionItems\":" + itemsArrayToJsonString(transactionItems) + "," +
      "\"transactionMembers\":" + membersArraytoJsonString(transactionMembers) + "," +
      "\"transactionItemAssignments\":" + assignmentsArrayToJsonString(transactionItemAssignments) +
      "}";
  }

  @Override
  public String toString() {
    return "Transaction{" +
      "transactionId='" + transactionId + '\'' +
      ", payerId='" + payerId + '\'' +
      ", billPicture='" + billPicture + '\'' +
      ", totalAmount=" + totalAmount +
      ", transactionDate=" + transactionDate +
      ", groupId='" + groupId + '\'' +
      ", transactionItems=" + transactionItems +
      ", transactionMembers=" + transactionMembers +
      ", transactionItemAssignments=" + transactionItemAssignments +
      '}';
  }
}
