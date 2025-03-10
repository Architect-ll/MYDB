package top.guoziyang.mydb.client;

import top.guoziyang.mydb.transport.Package;
import top.guoziyang.mydb.transport.Packager;

public class RoundTripper {
    private Packager packager;

    public RoundTripper(Packager packager) {
        this.packager = packager;
    }

    // 定义一个方法，用于处理请求的往返传输
    public Package roundTrip(Package pkg) throws Exception {
        // 发送请求包
        packager.send(pkg);
        // 接收响应包，并返回
        return packager.receive();
    }

    public void close() throws Exception {
        packager.close();
    }
}
