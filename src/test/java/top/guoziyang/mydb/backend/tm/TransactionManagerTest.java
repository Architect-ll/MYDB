package top.guoziyang.mydb.backend.tm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Test;

public class TransactionManagerTest {

    static Random random = new SecureRandom();

    private int transCnt = 0;
    private int noWorkers = 50; //线程数
    private int noWorks = 3000; //任务数
    private Lock lock = new ReentrantLock();
    private TransactionManager tmger;
    private Map<Long, Byte> transMap;
    private CountDownLatch cdl;

    /*
     * 该测试类用于验证 TransactionManager 在多线程并发场景下的正确性。
     * 主要测试点包括：
     * 1. 并发事务的开启、提交和回滚操作
     * 2. 事务状态的正确性验证（active/committed/aborted）
     * 3. 线程安全性和资源竞争处理
     *
     * 测试设计特点：
     * - 使用 50 个工作线程(noWorkers)模拟高并发
     * - 每个线程执行 3000 次随机操作(noWorks)
     * - 通过 ConcurrentHashMap(transMap) 记录事务状态
     * - 使用 ReentrantLock 保证关键操作的原子性
     * - 通过 CountDownLatch 进行多线程同步
     *
     * 注意：当前的线程启动方式 new Thread(r).run() 存在问题（应使用 start()），
     * 这会导致实际在单线程执行，需要修改为正确的方式才能真实测试并发场景。
     */
    @Test
    public void testMultiThread() {
        String path = "D:\\JavaProjectLearning\\MYDB\\tmp\\tranmger_test";
//        tmger = TransactionManager.create("/tmp/tranmger_test");
        tmger = TransactionManager.create(path);
        transMap = new ConcurrentHashMap<>();
        cdl = new CountDownLatch(noWorkers);
        for(int i = 0; i < noWorkers; i ++) {
            Runnable r = () -> worker();
            new Thread(r).start();
        }
        try {
            cdl.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        assert new File("/tmp/tranmger_test.xid").delete();
//        assert new File(path + ".xid").delete();
        tearDown(path);
    }

    /*
     * 工作线程执行逻辑：
     * 1. 通过随机操作(op)混合事务操作(25% 概率)和状态验证(75% 概率)
     * 2. 事务操作包括：
     *    - 开启事务（记录为活跃状态 0）
     *    - 随机提交（状态 1）或回滚（状态 2）
     * 3. 状态验证会随机检查已存在事务的状态是否一致
     * 4. 使用双重检查锁模式保证线程安全
     */
    private void worker() {
        boolean inTrans = false;
        long transXID = 0;
        for(int i = 0; i < noWorks; i++) {
            int op = Math.abs(random.nextInt(6));
            if(op == 0) {  //开启或者修改事务状态
                lock.lock();
                if(inTrans == false) {
                    long xid = tmger.begin();
                    transMap.put(xid, (byte)0);
                    transCnt++;
                    transXID = xid;
                    inTrans = true;
                } else {
                    int status = (random.nextInt(Integer.MAX_VALUE) % 2) + 1;
                    switch(status) {
                        case 1:
                            tmger.commit(transXID);
                            break;
                        case 2:
                            tmger.abort(transXID);
                            break;
                    }
                    transMap.put(transXID, (byte)status);
                    inTrans = false;
                }
                lock.unlock();
            } else { // 查询
                lock.lock();
                if(transCnt > 0) {
                    long xid = (long)((random.nextInt(Integer.MAX_VALUE) % transCnt) + 1);
                    byte status = transMap.get(xid);
                    boolean ok = false;
                    switch (status) {
                        case 0:
                            ok = tmger.isActive(xid);
                            break;
                        case 1:
                            ok = tmger.isCommitted(xid);
                            break;
                        case 2:
                            ok = tmger.isAborted(xid);
                            break;
                    }
                    assert ok;
                }
                lock.unlock();
            }
        }
        cdl.countDown();
    }

    // 推荐方案 1：在 @After 清理方法中处理（最佳实践）
    public void tearDown(String path) {
        Path xidFile = Paths.get(path + ".xid");
        try {
            Files.deleteIfExists(xidFile);
        } catch (IOException e) {
            System.err.println("清理文件失败: " + e.getMessage());
        }
    }

}
