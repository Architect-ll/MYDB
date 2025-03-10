package top.guoziyang.mydb.backend.tbm;

import java.util.Arrays;
import java.util.List;

import com.google.common.primitives.Bytes;

import top.guoziyang.mydb.backend.im.BPlusTree;
import top.guoziyang.mydb.backend.parser.statement.SingleExpression;
import top.guoziyang.mydb.backend.tm.TransactionManagerImpl;
import top.guoziyang.mydb.backend.utils.Panic;
import top.guoziyang.mydb.backend.utils.ParseStringRes;
import top.guoziyang.mydb.backend.utils.Parser;
import top.guoziyang.mydb.common.Error;

/**
 * field 表示字段信息
 * 二进制格式为：
 * [FieldName][TypeName][IndexUid]
 * 如果field无索引，IndexUid为0
 */
public class Field {
    // 唯一标识符，用于标识每个Field对象
    long uid;
    // Field对象所属的表
    private Table tb;
    // 字段名，用于标识表中的每个字段名称
    String fieldName;
    // 字段类型，用于标识字段的数据类型
    String fieldType;
    // 索引，用于标识字段是否有索引，0表示没有索引
    private long index;
    // B+树，用于存储索引，如果字段有索引，这个B+树会被加载
    private BPlusTree bt;

    /**
     * 从持久化存储中加载一个Field对象。
     */
    public static Field loadField(Table tb, long uid) {
        byte[] raw = null;  // 用于存储从持久化存储中读取的原始字节数据
        try {
            // 从持久化存储中读取uid对应的原始字节数据
            raw = ((TableManagerImpl) tb.tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        } catch (Exception e) {
            // 如果读取过程中出现异常，调用Panic.panic方法处理异常
            Panic.panic(e);
        }
        // 断言原始字节数据不为null，如果为null，那么会抛出AssertionError
        assert raw != null;
        // 创建一个新的Field对象，并调用parseSelf方法解析原始字节数据
        return new Field(uid, tb).parseSelf(raw);
    }

    public Field(long uid, Table tb) {
        this.uid = uid;
        this.tb = tb;
    }

    public Field(Table tb, String fieldName, String fieldType, long index) {
        this.tb = tb;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.index = index;
    }

    /**
     * 解析原始字节数组并设置字段名、字段类型和索引
     * @param raw 原始字节数组
     * @return 返回当前Field对象
     */
    private Field parseSelf(byte[] raw) {
        int position = 0;  // 初始化位置为0
        ParseStringRes res = Parser.parseString(raw);  // 解析原始字节数组，获取字段名和下一个位置
        fieldName = res.str;  // 设置字段名
        position += res.next;  // 更新位置
        res = Parser.parseString(Arrays.copyOfRange(raw, position, raw.length));  // 从新的位置开始解析原始字节数组，获取字段类型和下一个位置
        fieldType = res.str;  // 设置字段类型
        position += res.next;  // 更新位置

        this.index = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));  // 从新的位置开始解析原始字节数组，获取索引
        if (index != 0) {  // 如果索引不为0，说明存在B+树索引
            try {
                bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);  // 加载B+树索引
            } catch (Exception e) {
                Panic.panic(e);  // 如果加载失败，抛出异常
            }
        }
        return this;  // 返回当前Field对象
    }

    /**
     * 创建一个新的Field对象
     * @param tb        表对象，Field对象所属的表
     * @param xid       事务ID
     * @param fieldName 字段名
     * @param fieldType 字段类型
     * @param indexed   是否创建索引
     * @return 返回创建的Field对象
     * @throws Exception 如果字段类型无效或者创建B+树索引失败，会抛出异常
     */
    public static Field createField(Table tb, long xid, String fieldName, String fieldType, boolean indexed) throws Exception {
        typeCheck(fieldType);  // 检查字段类型是否有效
        Field f = new Field(tb, fieldName, fieldType, 0);  // 创建一个新的Field对象
        if (indexed) {  // 如果需要创建索引
            long index = BPlusTree.create(((TableManagerImpl) tb.tbm).dm);  // 创建一个新的B+树索引
            BPlusTree bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);  // 加载这个B+树索引
            f.index = index;  // 设置Field对象的索引
            f.bt = bt;  // 设置Field对象的B+树
        }
        f.persistSelf(xid);  // 将Field对象持久化到存储中
        return f;  // 返回创建的Field对象
    }

    /**
     * 将当前Field对象持久化到存储中
     */
    private void persistSelf(long xid) throws Exception {
        // 将字段名转换为字节数组
        byte[] nameRaw = Parser.string2Byte(fieldName);
        // 将字段类型转换为字节数组
        byte[] typeRaw = Parser.string2Byte(fieldType);
        // 将索引转换为字节数组
        byte[] indexRaw = Parser.long2Byte(index);
        // 将字段名、字段类型和索引的字节数组合并，然后插入到持久化存储中
        // 插入成功后，会返回一个唯一的uid，将这个uid设置为当前Field对象的uid
        this.uid = ((TableManagerImpl) tb.tbm).vm.insert(xid, Bytes.concat(nameRaw, typeRaw, indexRaw));
    }

    private static void typeCheck(String fieldType) throws Exception {
        if(!"int32".equals(fieldType) && !"int64".equals(fieldType) && !"string".equals(fieldType)) {
            throw Error.InvalidFieldException;
        }
    }

    public boolean isIndexed() {
        return index != 0;
    }

    public void insert(Object key, long uid) throws Exception {
        long uKey = value2Uid(key);
        bt.insert(uKey, uid);
    }

    public List<Long> search(long left, long right) throws Exception {
        return bt.searchRange(left, right);
    }

    public Object string2Value(String str) {
        switch(fieldType) {
            case "int32":
                return Integer.parseInt(str);
            case "int64":
                return Long.parseLong(str);
            case "string":
                return str;
        }
        return null;
    }

    public long value2Uid(Object key) {
        long uid = 0;
        switch(fieldType) {
            case "string":
                uid = Parser.str2Uid((String)key);
                break;
            case "int32":
                int uint = (int)key;
                return (long)uint;
            case "int64":
                uid = (long)key;
                break;
        }
        return uid;
    }

    public byte[] value2Raw(Object v) {
        byte[] raw = null;
        switch(fieldType) {
            case "int32":
                raw = Parser.int2Byte((int)v);
                break;
            case "int64":
                raw = Parser.long2Byte((long)v);
                break;
            case "string":
                raw = Parser.string2Byte((String)v);
                break;
        }
        return raw;
    }

    class ParseValueRes {
        Object v;
        int shift;
    }

    public ParseValueRes parserValue(byte[] raw) {
        ParseValueRes res = new ParseValueRes();
        switch(fieldType) {
            case "int32":
                res.v = Parser.parseInt(Arrays.copyOf(raw, 4));
                res.shift = 4;
                break;
            case "int64":
                res.v = Parser.parseLong(Arrays.copyOf(raw, 8));
                res.shift = 8;
                break;
            case "string":
                ParseStringRes r = Parser.parseString(raw);
                res.v = r.str;
                res.shift = r.next;
                break;
        }
        return res;
    }

    public String printValue(Object v) {
        String str = null;
        switch(fieldType) {
            case "int32":
                str = String.valueOf((int)v);
                break;
            case "int64":
                str = String.valueOf((long)v);
                break;
            case "string":
                str = (String)v;
                break;
        }
        return str;
    }

    @Override
    public String toString() {
        return new StringBuilder("(")
            .append(fieldName)
            .append(", ")
            .append(fieldType)
            .append(index!=0?", Index":", NoIndex")
            .append(")")
            .toString();
    }

    public FieldCalRes calExp(SingleExpression exp) throws Exception {
        Object v = null;
        FieldCalRes res = new FieldCalRes();
        switch(exp.compareOp) {
            case "<":
                res.left = 0;
                v = string2Value(exp.value);
                res.right = value2Uid(v);
                if(res.right > 0) {
                    res.right --;
                }
                break;
            case "=":
                v = string2Value(exp.value);
                res.left = value2Uid(v);
                res.right = res.left;
                break;
            case ">":
                res.right = Long.MAX_VALUE;
                v = string2Value(exp.value);
                res.left = value2Uid(v) + 1;
                break;
        }
        return res;
    }
}
