package top.guoziyang.mydb.backend.vm;

import top.guoziyang.mydb.backend.tm.TransactionManager;

public class Visibility {

    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        // 获取条目的删除版本号
        long xmax = e.getXmax();
        // 如果事务的隔离级别为0，即读未提交，那么跳过该版本，返回false
        if (t.level == 0) {
            return false;
        } else {
            // 如果事务的隔离级别不为0，那么检查删除版本是否已提交，并且删除版本号大于事务的ID（不可见）或者删除版本号在事务的快照中（活跃）
            // 如果满足上述条件，那么不跳过该版本，返回true
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }
    }

    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        if(t.level == 0) {
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }

    // 用来在读提交的隔离级别下，某个记录是否对事务t可见
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        // 获取事务的ID
        long xid = t.xid;
        // 获取记录的创建版本号
        long xmin = e.getXmin();
        // 获取记录的删除版本号
        long xmax = e.getXmax();
        // 如果记录的创建版本号等于事务的ID并且记录未被删除，则返回true
        if (xmin == xid && xmax == 0) return true;

        // 如果记录的创建版本已经提交
        if (tm.isCommitted(xmin)) {
            // 如果记录未被删除（首次创建），则返回true
            if (xmax == 0) return true;
            // 如果记录的删除版本号不等于事务的ID
            if (xmax != xid) {
                // 如果记录的删除版本未提交，则返回true
                // 因为没有提交，代表该数据还是上一个版本可见的
                if (!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        // 其他情况返回false
        return false;
    }

    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if(xmin == xid && xmax == 0) return true;

        if(tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            if(xmax == 0) return true;
            if(xmax != xid) {
                if(!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

}
