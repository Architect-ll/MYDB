package top.guoziyang.mydb.backend.dm.page;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import top.guoziyang.mydb.backend.dm.pageCache.PageCache;

public class PageImpl implements Page {
    private int pageNumber;   // 页面的页号，从1开始计数
    private byte[] data;      // 实际包含的字节数据
    private boolean dirty;    // 标志着页面是否是脏页面，在缓存驱逐时，脏页面需要被写回磁盘
    private Lock lock;        // 用于页面的锁
    
    private PageCache pc;  // 保存了一个 PageCache 的引用，方便在拿到 Page 的引用时可以快速对页面的缓存进行释放操作

    public PageImpl(int pageNumber, byte[] data, PageCache pc) {
        this.pageNumber = pageNumber;
        this.data = data;
        this.pc = pc;
        lock = new ReentrantLock();
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void release() {
        pc.release(this);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public byte[] getData() {
        return data;
    }

}
