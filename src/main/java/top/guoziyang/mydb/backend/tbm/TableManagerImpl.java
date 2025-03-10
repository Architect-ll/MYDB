package top.guoziyang.mydb.backend.tbm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import top.guoziyang.mydb.backend.dm.DataManager;
import top.guoziyang.mydb.backend.parser.statement.Begin;
import top.guoziyang.mydb.backend.parser.statement.Create;
import top.guoziyang.mydb.backend.parser.statement.Delete;
import top.guoziyang.mydb.backend.parser.statement.Insert;
import top.guoziyang.mydb.backend.parser.statement.Select;
import top.guoziyang.mydb.backend.parser.statement.Update;
import top.guoziyang.mydb.backend.utils.Parser;
import top.guoziyang.mydb.backend.vm.VersionManager;
import top.guoziyang.mydb.common.Error;

public class TableManagerImpl implements TableManager {
    VersionManager vm; // 版本管理器，用于管理事务的版本
    DataManager dm; // 数据管理器，用于管理数据的存储和读取
    private Booter booter; // 启动信息管理器，用于管理数据库启动信息
    private Map<String, Table> tableCache; // 表缓存，用于缓存已加载的表，键是表名，值是表对象
    private Map<Long, List<Table>> xidTableCache; // 事务表缓存，用于缓存每个事务修改过的表，键是事务ID，值是表对象列表
    private Lock lock; // 锁，用于同步多线程操作
    
    TableManagerImpl(VersionManager vm, DataManager dm, Booter booter) {
        this.vm = vm;
        this.dm = dm;
        this.booter = booter;
        this.tableCache = new HashMap<>();
        this.xidTableCache = new HashMap<>();
        lock = new ReentrantLock();
        loadTables();
    }

    /**
     * 加载所有的数据库表。
     */
    private void loadTables() {
        // 获取第一个表的UID
        long uid = firstTableUid();
        // 当UID不为0时，表示还有表需要加载
        while (uid != 0) {
            // 加载表，并获取表的UID
            Table tb = Table.loadTable(this, uid);
            // 更新UID为下一个表的UID
            uid = tb.nextUid;
            // 将加载的表添加到表缓存中
            tableCache.put(tb.name, tb);
        }
    }

    /**
     * 获取 Botter 文件的前八位字节
     * @return
     */
    private long firstTableUid() {
        byte[] raw = booter.load();
        return Parser.parseLong(raw);
    }

    private void updateFirstTableUid(long uid) {
        byte[] raw = Parser.long2Byte(uid);
        booter.update(raw);
    }

    @Override
    public BeginRes begin(Begin begin) {
        BeginRes res = new BeginRes();
        int level = begin.isRepeatableRead?1:0;
        res.xid = vm.begin(level);
        res.result = "begin".getBytes();
        return res;
    }
    @Override
    public byte[] commit(long xid) throws Exception {
        vm.commit(xid);
        return "commit".getBytes();
    }
    @Override
    public byte[] abort(long xid) {
        vm.abort(xid);
        return "abort".getBytes();
    }
    @Override
    public byte[] show(long xid) {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            for (Table tb : tableCache.values()) {
                sb.append(tb.toString()).append("\n");
            }
            List<Table> t = xidTableCache.get(xid);
            if(t == null) {
                return "\n".getBytes();
            }
            for (Table tb : t) {
                sb.append(tb.toString()).append("\n");
            }
            return sb.toString().getBytes();
        } finally {
            lock.unlock();
        }
    }
    @Override
    public byte[] create(long xid, Create create) throws Exception {
        // 加锁，防止多线程并发操作
        lock.lock();
        try {
            // 检查表是否已存在，如果存在则抛出异常
            if (tableCache.containsKey(create.tableName)) {
                throw Error.DuplicatedTableException;
            }
            // 创建新的表，并获取表的UID
            Table table = Table.createTable(this, firstTableUid(), xid, create);
            // 更新第一个表的UID
            updateFirstTableUid(table.uid);
            // 将新创建的表添加到表缓存中
            tableCache.put(create.tableName, table);
            // 如果事务表缓存中没有当前事务ID的条目，则添加一个新的条目
            if (!xidTableCache.containsKey(xid)) {
                xidTableCache.put(xid, new ArrayList<>());
            }
            // 将新创建的表添加到当前事务的表列表中
            xidTableCache.get(xid).add(table);
            // 返回创建成功的消息
            return ("create " + create.tableName).getBytes();
        } finally {
            // 解锁
            lock.unlock();
        }
    }
    @Override
    public byte[] insert(long xid, Insert insert) throws Exception {
        lock.lock();
        Table table = tableCache.get(insert.tableName);
        lock.unlock();
        if(table == null) {
            throw Error.TableNotFoundException;
        }
        table.insert(xid, insert);
        return "insert".getBytes();
    }
    @Override
    public byte[] read(long xid, Select read) throws Exception {
        lock.lock();
        Table table = tableCache.get(read.tableName);
        lock.unlock();
        if(table == null) {
            throw Error.TableNotFoundException;
        }
        return table.read(xid, read).getBytes();
    }
    @Override
    public byte[] update(long xid, Update update) throws Exception {
        lock.lock();
        Table table = tableCache.get(update.tableName);
        lock.unlock();
        if(table == null) {
            throw Error.TableNotFoundException;
        }
        int count = table.update(xid, update);
        return ("update " + count).getBytes();
    }
    @Override
    public byte[] delete(long xid, Delete delete) throws Exception {
        lock.lock();
        Table table = tableCache.get(delete.tableName);
        lock.unlock();
        if(table == null) {
            throw Error.TableNotFoundException;
        }
        int count = table.delete(xid, delete);
        return ("delete " + count).getBytes();
    }
}
