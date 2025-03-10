package top.guoziyang.mydb.backend.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import top.guoziyang.mydb.backend.tbm.TableManager;
import top.guoziyang.mydb.transport.Encoder;
import top.guoziyang.mydb.transport.Package;
import top.guoziyang.mydb.transport.Packager;
import top.guoziyang.mydb.transport.Transporter;

public class Server {
    private int port;  // 监听端口
    TableManager tbm;

    public Server(int port, TableManager tbm) {
        this.port = port;
        this.tbm = tbm;
    }

    public void start() {
        // 创建一个ServerSocket对象，用于监听指定的端口
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Server listen to port: " + port);
        // 创建一个线程池，用于管理处理客户端连接请求的线程
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(10, 20, 1L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            // 无限循环，等待并处理客户端的连接请求
            while (true) {
                // 接收一个客户端的连接请求
                Socket socket = ss.accept();
                // 创建一个新的HandleSocket对象，用于处理这个连接请求
                Runnable worker = new HandleSocket(socket, tbm);
                // 将这个HandleSocket对象提交给线程池，由线程池中的一个线程来执行
                tpe.execute(worker);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 在最后，无论是否发生异常，都要关闭ServerSocket
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }
}

/*
    HandleSocket 类实现了 Runnable 接口，在建立连接后初始化 Packager，随后就循环接收来自客户端的数据并处理；
    主要通过 Executor 对象来执行 SQL语句，
    在接受、执行SQL语句的过程中发生异常的话，将会结束循环，并关闭 Executor和 Package;
 */
class HandleSocket implements Runnable {
    private Socket socket;
    private TableManager tbm;

    public HandleSocket(Socket socket, TableManager tbm) {
        this.socket = socket;
        this.tbm = tbm;
    }

    @Override
    public void run() {
        // 获取远程客户端的地址信息
        InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();
        // 打印客户端的IP地址和端口号
        System.out.println("Establish connection: " + address.getAddress().getHostAddress() + ":" + address.getPort());
        Packager packager = null;
        try {
            // 创建一个Transporter对象，用于处理网络传输
            Transporter t = new Transporter(socket);
            // 创建一个Encoder对象，用于处理数据的编码和解码
            Encoder e = new Encoder();
            // 创建一个Packager对象，用于处理数据的打包和解包
            packager = new Packager(t, e);
        } catch (IOException e) {
            // 如果在创建Transporter或Encoder时发生异常，打印异常信息并关闭socket
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;
        }
        // 创建一个Executor对象，用于执行SQL语句
        Executor exe = new Executor(tbm);
        while (true) {
            Package pkg = null;
            try {
                // 从客户端接收数据包
                pkg = packager.receive();
            } catch (Exception e) {
                // 如果在接收数据包时发生异常，结束循环
                break;
            }
            // 获取数据包中的SQL语句
            byte[] sql = pkg.getData();
            byte[] result = null;
            Exception e = null;
            try {
                // 执行SQL语句，并获取结果
                result = exe.execute(sql);
            } catch (Exception e1) {
                // 如果在执行SQL语句时发生异常，保存异常信息
                e = e1;
                e.printStackTrace();
            }
            // 创建一个新的数据包，包含执行结果和可能的异常信息
            pkg = new Package(result, e);
            try {
                // 将数据包发送回客户端
                packager.send(pkg);
            } catch (Exception e1) {
                // 如果在发送数据包时发生异常，打印异常信息并结束循环
                e1.printStackTrace();
                break;
            }
        }
        // 关闭Executor
        exe.close();
        try {
            // 关闭Packager
            packager.close();
        } catch (Exception e) {
            // 如果在关闭Packager时发生异常，打印异常信息
            e.printStackTrace();
        }
    }
}