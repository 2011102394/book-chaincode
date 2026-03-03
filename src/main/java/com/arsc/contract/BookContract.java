package com.arsc.contract;

import com.arsc.pojo.Book;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import com.owlike.genson.Genson;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Contract(
        name = "BookContract",
        info = @Info(title = "Book Traceability Contract", description = "图书溯源智能合约", version = "1.0.0")
)
@Default
public class BookContract implements ContractInterface {

    private final Genson genson = new Genson(); // JSON 转换工具

    /**
     * 1. 图书上链 (初始化图书信息)
     * intent = SUBMIT 表示这是一个会修改账本状态的交易，需要节点共识
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Book createBook(Context ctx, String bookId, String bookName, String publisher, String currentLocation,
                           String operator, String operatorRole) {
        // 检查账本中是否已经存在该图书
        boolean exists = bookExists(ctx, bookId);
        if (exists) {
            throw new ChaincodeException("图书已存在: " + bookId);
        }

        // 创建图书对象，初始状态设为 "已出版"
        Book book = new Book(bookId, bookName, publisher, currentLocation, "已出版");
        book.setOperator(operator);
        book.setOperatorRole(operatorRole);
        // 将图书对象转换为 JSON 字符串
        String bookJson = genson.serialize(book);

        // 【核心API】存入区块链账本
        // putStringState(Key, Value)
        ctx.getStub().putStringState(bookId, bookJson);
        // 抛出新建图书事件
        ctx.getStub().setEvent("BookCreatedEvent", bookJson.getBytes(StandardCharsets.UTF_8));

        return book;
    }

    /**
     * 2. 更新图书位置 (流转记录)
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Book updateBookLocation(Context ctx, String bookId, String newLocation, String newStatus,
                                   String operator, String operatorRole) {
        // 先从账本查出当前图书
        String bookJson = ctx.getStub().getStringState(bookId);
        if (bookJson == null || bookJson.isEmpty()) {
            throw new ChaincodeException("图书不存在: " + bookId);
        }

        // 反序列化，修改属性
        Book book = genson.deserialize(bookJson, Book.class);
        book.setCurrentLocation(newLocation);
        book.setStatus(newStatus);
        book.setOperator(operator);
        book.setOperatorRole(operatorRole);
        // 重新存入账本 (覆盖旧的 Value，但 Fabric 会自动记录历史版本)
        ctx.getStub().putStringState(bookId, genson.serialize(book));
        // 抛出图书流转事件
        ctx.getStub().setEvent("BookUpdatedEvent", genson.serialize(book).getBytes(StandardCharsets.UTF_8));
        return book;
    }

    /**
     * 3. 溯源查询 (查询当前状态)
     * intent = EVALUATE 表示这只是一个查询操作，不产生新区块，速度极快
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryBook(Context ctx, String bookId) {
        String bookJson = ctx.getStub().getStringState(bookId);
        if (bookJson == null || bookJson.isEmpty()) {
            throw new ChaincodeException("图书不存在: " + bookId);
        }
        return bookJson;
    }

    /**
     * 辅助方法：检查图书是否存在
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean bookExists(Context ctx, String bookId) {
        byte[] buffer = ctx.getStub().getState(bookId);
        return (buffer != null && buffer.length > 0);
    }

    /**
     * 4. 获取图书的历史流转轨迹 (溯源核心机制)
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getBookHistory(Context ctx, String bookId) {
        List<Map<String, Object>> historyList = new ArrayList<>();

        // 【核心API】获取某个 Key 从诞生到现在的每一笔修改记录
        QueryResultsIterator<KeyModification> results = ctx.getStub().getHistoryForKey(bookId);

        for (KeyModification mod : results) {
            Map<String, Object> record = new HashMap<>();
            record.put("txId", mod.getTxId());                     // 1. 独一无二的区块链交易ID
            record.put("timestamp", mod.getTimestamp().toString());// 2. 节点达成共识的准确时间戳
            record.put("isDelete", mod.isDeleted());               // 3. 这次操作是否是删除

            String valueStr = mod.getStringValue();
            if (valueStr != null && !valueStr.isEmpty()) {
                // 将历史版本的 JSON 字符串反序列化为 Map，方便组装成结构化数据返回
                Map<String, Object> valueMap = genson.deserialize(valueStr, Map.class);
                record.put("value", valueMap);                     // 4. 当时的具体图书数据
            }
            historyList.add(record);
        }

        // 将包含所有轨迹的 List 转换为 JSON 数组返回给网关
        return genson.serialize(historyList);
    }

    /**
     * 5. 删除图书 (仅从当前状态世界中删除，历史轨迹永久保留)
     * intent = SUBMIT 因为删除也是一种修改账本状态的交易
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void deleteBook(Context ctx, String bookId) {
        // 先检查图书当前是否存在
        if (!bookExists(ctx, bookId)) {
            throw new ChaincodeException("图书不存在，无法删除: " + bookId);
        }

        // 【核心API】从状态数据库中彻底删除该 Key
        ctx.getStub().delState(bookId);
        // 抛出图书删除事件 (因为书被删了，载荷里传个 JSON 格式的 ID 就行)
        String payload = "{\"bookId\":\"" + bookId + "\"}";
        ctx.getStub().setEvent("BookDeletedEvent", payload.getBytes(StandardCharsets.UTF_8));
    }
}
