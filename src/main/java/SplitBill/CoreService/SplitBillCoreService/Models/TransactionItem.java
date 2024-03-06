package SplitBill.CoreService.SplitBillCoreService.Models;

import java.util.UUID;

public class TransactionItem {
  private String itemId;
  private int itemSequence;
  private String itemName;
  private float price;
  private int quantity;

  public TransactionItem() {
  }

  public TransactionItem(String itemId, int itemSequence, String itemName, float price, int quantity) {
    this.itemId = itemId;
    this.itemSequence = itemSequence;
    this.itemName = itemName;
    this.price = price;
    this.quantity = quantity;
  }


  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public int getItemSequence() {
    return itemSequence;
  }

  public void setItemSequence(int itemSequence) {
    this.itemSequence = itemSequence;
  }

  public String getItemName() {
    return itemName;
  }

  public void setItemName(String itemName) {
    this.itemName = itemName;
  }

  public float getPrice() {
    return price;
  }

  public void setPrice(float price) {
    this.price = price;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public String toJsonString() {
    return "{" +
      "\"itemId\":\"" + itemId + "\"," +
      "\"itemSequence\":" + itemSequence + "," +
      "\"itemName\":\"" + itemName + "\"," +
      "\"price\":" + price + "," +
      "\"quantity\":" + quantity +
      "}";
  }

  @Override
  public String toString() {
    return "TransactionItem{" +
      ", itemId='" + itemId + '\'' +
      ", itemSequence=" + itemSequence +
      ", itemName='" + itemName + '\'' +
      ", price=" + price +
      ", quantity=" + quantity +
      '}';
  }
}

