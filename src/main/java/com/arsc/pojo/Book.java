package com.arsc.pojo;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import com.owlike.genson.annotation.JsonProperty;

@DataType()
public class Book {

    @Property()
    private final String bookId;      // 图书唯一编号 (如 ISBN+流水号)

    @Property()
    private final String bookName;    // 书名

    @Property()
    private final String publisher;   // 出版社名称

    @Property()
    private String currentLocation;   // 当前位置 (如：某某印刷厂、某某物流枢纽、某某书店)

    @Property()
    private String status;            // 状态 (如：已出版、运输中、已入库、已售出)

    // 构造函数
    public Book(@JsonProperty("bookId") String bookId,
                @JsonProperty("bookName") String bookName,
                @JsonProperty("publisher") String publisher,
                @JsonProperty("currentLocation") String currentLocation,
                @JsonProperty("status") String status) {
        this.bookId = bookId;
        this.bookName = bookName;
        this.publisher = publisher;
        this.currentLocation = currentLocation;
        this.status = status;
    }

    public String getBookId() {
        return bookId;
    }

    public String getBookName() {
        return bookName;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
