package top.guoziyang.mydb.backend.dm.logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.primitives.Bytes;

import top.guoziyang.mydb.backend.utils.Panic;
import top.guoziyang.mydb.backend.utils.Parser;
import top.guoziyang.mydb.common.Error;

/**
 * 日志文件读写
 * 
 * 日志文件标准格式为：
 * [XChecksum] [Log1] [Log2] ... [LogN] [BadTail]
 * XChecksum 为后续所有日志计算的Checksum，int类型
 * 
 * 每条正确日志的格式为：
 * [Size] [Checksum] [Data]
 * Size 4字节int 标识Data长度
 * Checksum 4字节int
 */
public class LoggerImpl implements Logger {

    private static final int SEED = 13331;

    private static final int OF_SIZE = 0;
    private static final int OF_CHECKSUM = OF_SIZE + 4;
    private static final int OF_DATA = OF_CHECKSUM + 4;
    
    public static final String LOG_SUFFIX = ".log";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    private long position;  // 当前日志指针的位置
    private long fileSize;  // 初始化时记录，log操作不更新
    private int xChecksum;

    public LoggerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    LoggerImpl(RandomAccessFile raf, FileChannel fc, int xChecksum) {
        this.file = raf;
        this.fc = fc;
        this.xChecksum = xChecksum;
        lock = new ReentrantLock();
    }

    public void init() {
        long size = 0;
        try {
            size = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        if(size < 4) {                                   // 若文件大小小于4，证明日志文件创建出现问题
            Panic.panic(Error.BadLogFileException);
        }

        ByteBuffer raw = ByteBuffer.allocate(4); // 创建一个容量为4的ByteBuffer
        try {
            fc.position(0);
            fc.read(raw);                                 // 读取四字节大小的内容
        } catch (IOException e) {
            Panic.panic(e);
        }
        int xChecksum = Parser.parseInt(raw.array());     // 将其转换成int整数
        this.fileSize = size;
        this.xChecksum = xChecksum;                       // 赋值给当前对象

        checkAndRemoveTail();                             //检查是否需要去除BadTail
    }

    // 检查并移除bad tail
    private void checkAndRemoveTail() {
        // 将当前位置重置为文件的开始位置
        // [XChecksum][Log1][Log2]...[LogN][BadTail] --> [Log1][Log2]...[LogN][BadTail]
        rewind();  // 向后移动四个字节
        // 初始化校验和为 0
        int xCheck = 0;
        while(true) {
            // 读取下一条日志
            byte[] log = internNext();
            // 如果读取到的日志为 null，说明没有更多的日志可以读取，跳出循环
            if (log == null) break;
            // 计算校验和
            xCheck = calChecksum(xCheck, log);
        }
        // 比较计算得到的校验和和文件中的校验和，如果不相等，说明日志已经被破坏，抛出异常
        if(xCheck != xChecksum) {
            Panic.panic(Error.BadLogFileException);
        }
        // 尝试将文件截断到当前位置，移除 "bad tail"
        try {
            truncate(position);
        } catch (Exception e) {
            Panic.panic(e);
        }
        // 尝试将文件的读取位置设置为当前位置
        try {
            file.seek(position);
        } catch (IOException e) {
            Panic.panic(e);
        }
        // 将当前位置重置为文件的开始位置  4字节处
        rewind();
    }

    private int calChecksum(int xCheck, byte[] log) {
        for (byte b : log) {
            xCheck = xCheck * SEED + b;
        }
        return xCheck;
    }

    @Override
    public void log(byte[] data) {
        byte[] log = wrapLog(data);
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();
        try {
            fc.position(fc.size()); // 这里就是移动到文件末尾
            fc.write(buf);
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            lock.unlock();
        }
        // 更新总校验值
        updateXChecksum(log);
    }

    private void updateXChecksum(byte[] log) {
        this.xChecksum = calChecksum(this.xChecksum, log);
        try {
            fc.position(0); // 切换到文件开头位置
            fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
            fc.force(false);
        } catch(IOException e) {
            Panic.panic(e);
        }
    }

    private byte[] wrapLog(byte[] data) {
        // 使用 calChecksum 方法计算数据的校验和，然后将校验和转换为字节数组
        byte[] checksum = Parser.int2Byte(calChecksum(0, data));
        // 将数据的长度转换为字节数组
        byte[] size = Parser.int2Byte(data.length);
        // 使用 Bytes.concat 方法将 size、checksum 和 data 连接成一个新的字节数组，然后返回这个字节数组
        return Bytes.concat(size, checksum, data);  // 大小 + 校验和 + 数据
    }

    @Override
    public void truncate(long x) throws Exception {
        lock.lock();
        try {
            fc.truncate(x);
        } finally {
            lock.unlock();
        }
    }

    private byte[] internNext() {
        if(position + OF_DATA >= fileSize) { // 越界，数据错误
            return null;
        }
        // 用于读取[size]
        ByteBuffer tmp = ByteBuffer.allocate(4);
        try {
            fc.position(position);
            fc.read(tmp);
        } catch(IOException e) {
            Panic.panic(e);
        }
        // 判断是否越界
        int size = Parser.parseInt(tmp.array());
        if(position + size + OF_DATA > fileSize) { // 越界，数据错误
            return null;
        }

        // 读取整条日志数据 [Size][Checksum][Data]
        ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size);
        try {
            fc.position(position);
            // 读取整条日志 [Size][Checksum][Data]
            fc.read(buf);
        } catch(IOException e) {
            Panic.panic(e);
        }

        byte[] log = buf.array();  // 该条日志大小（） + 数据
        int checkSum1 = calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length)); // 计算该条数据校验和
        int checkSum2 = Parser.parseInt(Arrays.copyOfRange(log, OF_CHECKSUM, OF_DATA));    // 该条数据的校验和
        // 比较计算得到的校验和和日志中的校验和，如果不相等，说明日志已经被破坏，返回 null
        if(checkSum1 != checkSum2) {
            return null;
        }
        // 更新当前位置
        position += log.length;
        // 返回读取到的日志
        return log;
    }

    @Override
    public byte[] next() {
        lock.lock();
        try {
            byte[] log = internNext();
            if(log == null) return null;
            return Arrays.copyOfRange(log, OF_DATA, log.length);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rewind() {
        position = 4;
    }

    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch(IOException e) {
            Panic.panic(e);
        }
    }
    
}
