package top.guoziyang.mydb.transport;

public class Packager {
    private Transporter transpoter;
    private Encoder encoder;

    public Packager(Transporter transpoter, Encoder encoder) {
        this.transpoter = transpoter;
        this.encoder = encoder;
    }

    /**
     * 将信息编码之后发送
     */
    public void send(Package pkg) throws Exception {
        byte[] data = encoder.encode(pkg);
        transpoter.send(data);
    }

    /**
     * 将数据接收之后解密
     */
    public Package receive() throws Exception {
        byte[] data = transpoter.receive();
        return encoder.decode(data);
    }

    public void close() throws Exception {
        transpoter.close();
    }

    public Transporter getTransporter() {
        return transpoter;
    }

}
