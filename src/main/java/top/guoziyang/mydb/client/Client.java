package top.guoziyang.mydb.client;

import top.guoziyang.mydb.transport.Package;
import top.guoziyang.mydb.transport.Packager;

public class Client {
    // RoundTripper实例，用于处理请求的往返传输
    private RoundTripper rt;

    // 构造函数，接收一个Packager对象作为参数，并创建一个新的RoundTripper实例
    public Client(Packager packager) {
        this.rt = new RoundTripper(packager);
    }

    // execute方法，接收一个字节数组作为参数，将其封装为一个Package对象，并通过RoundTripper发送
    // 如果响应的Package对象中包含错误，那么抛出这个错误
    // 否则，返回响应的Package对象中的数据
    public byte[] execute(byte[] stat) throws Exception {
        Package pkg = new Package(stat, null);
        Package resPkg = rt.roundTrip(pkg);  // 过滤多余的空格
        if(resPkg.getErr() != null) {        // 捕获异常
            throw resPkg.getErr();
        }
        return resPkg.getData();
    }

    // close方法，关闭RoundTripper
    public void close() {
        try {
            rt.close();
        } catch (Exception e) {
        }
    }
}
