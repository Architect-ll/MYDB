package top.guoziyang.mydb.backend.vm;

import java.util.Arrays;

import com.google.common.primitives.Bytes;

import top.guoziyang.mydb.backend.common.SubArray;
import top.guoziyang.mydb.backend.dm.dataItem.DataItem;
import top.guoziyang.mydb.backend.utils.Parser;

/**
 * VM向上层抽象出entry
 * entry结构：
 * [XMIN] [XMAX] [data]
 */
public class Entry {

    // 定义了XMIN的偏移量为0
    private static final int OF_XMIN = 0;
    // 定义了XMAX的偏移量为XMIN偏移量后的8个字节
    private static final int OF_XMAX = OF_XMIN+8;
    // 定义了DATA的偏移量为XMAX偏移量后的8个字节
    private static final int OF_DATA = OF_XMAX+8;

    // uid字段，用来唯一标识一个Entry的
    private long uid;
    // DataItem对象，用来存储数据的
    private DataItem dataItem;
    // VersionManager实例对象，用来管理版本的
    private VersionManager vm;

    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        if (dataItem == null) {
            return null;
        }
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = dataItem;
        entry.vm = vm;
        return entry;
    }

    // 静态方法，用来加载一个Entry。它首先从VersionManager中读取数据，然后创建一个新的Entry
    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl)vm).dm.read(uid);
        return newEntry(vm, di, uid);
    }

    /**
     * 生成日志格式数据
     */
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        // 将事务id转为8字节数组
        byte[] xmin = Parser.long2Byte(xid);
        // 创建一个空的8字节数组，等待版本修改或删除时候给定事务xid (TBM)
        byte[] xmax = new byte[8];
        // 拼接成日志格式
        return Bytes.concat(xmin, xmax, data);
    }

    public void release() {
        ((VersionManagerImpl)vm).releaseEntry(this);
    }

    // 方法，用来移除一个Entry。它通过调用dataItem的release方法来实现
    public void remove() {
        dataItem.release();
    }

    // 以拷贝的形式返回内容
    // 获取记录中持有的数据，也就需要按照上面这个结构(xmin, xmax, data)来解析
    public byte[] data() {
        // 加锁，确保数据安全
        dataItem.rLock();
        try {
            // 获取日志数据
            SubArray sa = dataItem.data();
            // 创建一个去除前16字节的数组，因为前16字节表示 xmin and xmax
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            // 拷贝数据到data数组上
            System.arraycopy(sa.raw, sa.start+OF_DATA, data, 0, data.length);
            return data;
        } finally {
            //释放锁
            dataItem.rUnLock();
        }
    }

    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMIN, sa.start+OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMAX, sa.start+OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 设置删除版本的事务编号
     * @param xid
     */
    public void setXmax(long xid) {
        // 在修改或删除之前先拷贝好旧数值 仅数据 raw->oldRaw
        dataItem.before();
        try {
            // 获取需要删除的日志数据
            SubArray sa = dataItem.data();
            // 将事务编号拷贝到 8~15 处字节
            System.arraycopy(Parser.long2Byte(xid), 0, sa.raw, sa.start+OF_XMAX, 8);
        } finally {
            // 生成一个修改日志
            dataItem.after(xid);
        }
    }

    public long getUid() {
        return uid;
    }
}
