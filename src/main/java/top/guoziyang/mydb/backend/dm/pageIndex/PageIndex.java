package top.guoziyang.mydb.backend.dm.pageIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import top.guoziyang.mydb.backend.dm.pageCache.PageCache;

public class PageIndex {
    // 将一页划成40个区间
    private static final int INTERVALS_NO = 40;
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;   // 204

    private Lock lock;
    private List<PageInfo>[] lists;

    @SuppressWarnings("unchecked")
    public PageIndex() {
        lock = new ReentrantLock();
        lists = new List[INTERVALS_NO+1];
        for (int i = 0; i < INTERVALS_NO+1; i ++) {
            lists[i] = new ArrayList<>();
        }
    }

    public void add(int pgno, int freeSpace) {
        lock.lock();
        try {
            int number = freeSpace / THRESHOLD;
            lists[number].add(new PageInfo(pgno, freeSpace));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 根据给定的空间大小选择一个 PageInfo 对象。
     * @param spaceSize 需要的空间大小
     * @return 一个 PageInfo 对象，其空闲空间大于或等于给定的空间大小。如果没有找到合适的 PageInfo，返回 null。
     */
    public PageInfo select(int spaceSize) {
        lock.lock(); // 获取锁，确保线程安全
        try {
            int number = spaceSize / THRESHOLD; // 计算需要的空间大小对应的区间编号
            // 此处+1主要为了向上取整
        /*
            1、假需要存储的字节大小为5168，此时计算出来的区间号是25，但是25*204=5100显然是不满足条件的
            2、此时向上取整找到 26，而26*204=5304，是满足插入条件的
         */
            if (number < INTERVALS_NO) number++; // 如果计算出的区间编号小于总的区间数，编号加一
            while (number <= INTERVALS_NO) { // 从计算出的区间编号开始，向上寻找合适的 PageInfo
                if (lists[number].size() == 0) { // 如果当前区间没有 PageInfo，继续查找下一个区间
                    number++;
                    continue;
                }
                return lists[number].remove(0); // 如果当前区间有 PageInfo，返回第一个 PageInfo，并从列表中移除
            }
            return null; // 如果没有找到合适的 PageInfo，返回 null
        } finally {
            lock.unlock(); // 释放锁
        }
    }

}
