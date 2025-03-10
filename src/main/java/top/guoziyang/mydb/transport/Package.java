package top.guoziyang.mydb.transport;

/*
- [Flag] [Data]
- 若 flag 为 0，表示发送的是数据，那么 data 即为这份数据本身，err 就为空
- 若 flag 为 1，表示发送的是错误信息，那么 data 为空， err 为错误提示信息
 */
public class Package {
    byte[] data; // 存放数据信息
    Exception err; // 存放错误提示信息

    public Package(byte[] data, Exception err) {
        this.data = data;
        this.err = err;
    }

    public byte[] getData() {
        return data;
    }

    public Exception getErr() {
        return err;
    }
}
