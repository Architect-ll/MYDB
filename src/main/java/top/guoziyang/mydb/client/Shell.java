package top.guoziyang.mydb.client;

import top.guoziyang.mydb.transport.Transporter;

import java.net.SocketAddress;
import java.util.Scanner;

public class Shell {
    private Client client;

    public Shell(Client client) {
        this.client = client;
    }

    // 定义一个运行方法，用于启动客户端的交互式命令行界面
    public void run() {
        // 创建一个Scanner对象，用于读取用户的输入
        Scanner sc = new Scanner(System.in);
        try {
            // 循环接收用户的输入，直到用户输入"exit"或"quit"
            while (true) {
                // 打印提示符
                Transporter transporter = client.getTransporter();
                SocketAddress serverSocketAddress = transporter.getSocket().getRemoteSocketAddress();
                System.out.print(serverSocketAddress + " mysql: ");
                // 读取用户的输入
                String statStr = sc.nextLine();
                // 如果用户输入"exit"或"quit"，则退出循环
                if ("exit".equals(statStr) || "quit".equals(statStr)) {
                    break;
                }
                // 尝试执行用户的输入命令，并打印执行结果
                try {
                    // 将用户的输入转换为字节数组，并执行
                    byte[] res = client.execute(statStr.getBytes());
                    // 将执行结果转换为字符串，并打印
                    System.out.println(new String(res));
                    // 如果在执行过程中发生异常，打印异常信息
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
            // 无论是否发生异常，都要关闭Scanner和Client
        } finally {
            // 关闭Scanner
            sc.close();
            // 关闭Client
            client.close();
        }
    }
}
