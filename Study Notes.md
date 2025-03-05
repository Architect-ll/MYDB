个人学习笔记

# D1.TM

需要学NIO和RandomAccessFile

注意：updateXID()方法同时更新了xid值和对应事务的状态（active;commitied;aborted）

**作用：通过维护 XID 文件来维护事务的状态，并提供接口供其他模块来查询某个事务的状态。**

# D2.DM

```
AbstractCache
```

**引用计算缓存和共享内存数组**

使用引用计算法替换掉了数据库常见的LRU算法

实现原理是定义了三个HashMap（cache, references, getting）其中键为唯一标识。

* cache（键，数据资源）
* references（键，使用当前资源的其它线程或模块数目）
* getting（键，当前资源是否被其它线程占用）

共享内存数组的设计是通过传入统一个数组的引用，通过改变startIndex和endIndex的位置进行实现

**作用：DM 直接管理数据库 DB 文件和日志文件。DM 的主要职责有：1) 分页管理 DB 文件，并进行缓存；2) 管理日志文件，保证在发生错误时可以根据日志进行恢复；3) 抽象 DB 文件为 DataItem 供上层模块使用，并提供缓存。**

# D3.DM

数据页的缓存与管理

不太理解数据库崩溃时<u>recoverInsert()</u>和<u>recoverUpdate()</u>起到的具体作用。

# D4.DM

数据恢复策略

需要mydb.xid和mydb.log文件一起配合

其中mydb.xid标识了事务的具体id以及操作类型（正在进行，已提交，已撤销）

mydb.log文件标识了具体事务操作的数据（[size,cheackSum,data]）

这里最后存储的日志文件具体内容还有待考察