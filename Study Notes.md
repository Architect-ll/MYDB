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

# D5.DM

DataManager（DM）是数据库管理系统中的一层，主要负责底层数据的管理和操作。其功能和作用包括：

1. **数据缓存和管理**：DataManager 实现了对 DataItem 对象的缓存管理，通过缓存管理，可以提高数据的访问效率，并减少对底层存储的频繁访问，从而提高系统的性能。
2. **数据访问和操作**：DataManager 提供了读取、插入和修改等数据操作方法，上层模块可以通过这些方法对数据库中的数据进行操作和管理。
3. **事务管理**：DataManager 支持事务的管理，通过事务管理，可以保证对数据的修改是原子性的，并且在事务提交或回滚时能够保持数据的一致性和完整性。
4. **日志记录和恢复**：DataManager 在数据修改操作前后会执行一系列的流程，包括日志记录和数据恢复等操作，以确保数据的安全性和可靠性，即使在系统崩溃或异常情况下也能够保证数据的完整性。
5. **页面索引管理**：DataManager 中实现了页面索引管理功能，通过页面索引可以快速定位到合适的空闲空间，从而提高数据插入的效率和性能。
6. **文件初始化和校验**：DataManager 在创建和打开数据库文件时，会进行文件的初始化和校验操作，以确保文件的正确性和完整性，同时在文件关闭时会执行相应的清理操作。
7. **资源管理和释放**：DataManager 在关闭时会执行资源的释放和清理操作，包括缓存和日志的关闭，以及页面的释放和页面索引的清理等。

# D6.VM

Entry格式(xmin, xmax, data)，其中xmin是创建该条数据记录的事务，xmax是修改或者删除该条记录的事务

当发生读写冲突时，通过限制读事务只能读取到该条数据记录已经提交的版本达到**读已提交隔离级别**

```
(XMIN == Ti and                             // 由Ti创建且
    XMAX == NULL                            // 还未被删除
)
or                                          // 或
(XMIN is commited and                       // 由一个已提交的事务创建且
    (XMAX == NULL or                        // 尚未删除或
    (XMAX != Ti and XMAX is not commited)   // 由一个未提交的事务删除
))
```

可重复读隔离级别指的是在一个事务的执行过程中先后对同一条数据记录的访问结果是相同的（不会因为在前后时间段中别的事务对该条记录的修改而产生前后不一致的现象）。

为此，需要在该条事务的执行过程中记录下处于**active 状态**的事务。可重复读的判断逻辑为：

```
(XMIN == Ti and                 // 由Ti创建且
 (XMAX == NULL                  // 尚未被删除
))
or                              // 或
(XMIN is commited and           // 由一个已提交的事务创建且
 XMIN < XID and                 // 这个事务小于Ti且
 XMIN is not in SP(Ti) and      // 这个事务在Ti开始前提交且
 (XMAX == NULL or               // 尚未被删除或
  (XMAX != Ti and               // 由其他事务删除但是
   (XMAX is not commited or     // 这个事务尚未提交或
XMAX > Ti or                    // 这个事务在Ti开始之后才开始或
XMAX is in SP(Ti)               // 这个事务在Ti开始前还未提交
))))
```

# D7.VM

死锁检测（通过深度优先搜索检测当前时间戳下是否存在循环依赖【环图】）

如果检测到当前深搜的事务id对应的资源id被其它具有相同时间戳的事务id持有，即：

```
事务i 请求资源uid;  uid 被事务j持有且时间戳相同（表示同时持有）
```

存在死锁现象。

# D8.IM

IM，即 Index Manager，索引管理器，为 MYDB 提供了基于 B+ 树的聚簇索引。目前 MYDB 只支持基于索引查找数据，不支持全表扫描。

# D9.TBM

Parser类实现SQL语句的结构化解析

```sql
<begin statement>
    begin [isolation level (read committedrepeatable read)]
        begin isolation level read committed

<commit statement>
    commit

<abort statement>
    abort

<create statement>
    create table <table name>
    <field name> <field type>
    <field name> <field type>
    ...
    <field name> <field type>
    [(index <field name list>)]
        create table students
        id int32,
        name string,
        age int32,
        (index id name)

<drop statement>
    drop table <table name>
        drop table students

<select statement>
    select (*<field name list>) from <table name> [<where statement>]
        select * from student where id = 1
        select name from student where id > 1 and id < 4
        select name, age, id from student where id = 12

<insert statement>
    insert into <table name> values <value list>
        insert into student values 5 "Zhang Yuanjia" 22

<delete statement>
    delete from <table name> <where statement>
        delete from student where name = "Zhang Yuanjia"

<update statement>
    update <table name> set <field name>=<value> [<where statement>]
        update student set name = "ZYJ" where id = 5

<where statement>
    where <field name> (><=) <value> [(andor) <field name> (><=) <value>]
        where age > 10 or age < 3

<field name> <table name>
    [a-zA-Z][a-zA-Z0-9_]*

<field type>
    int32 int64 string

<value>
    .*
```

启动信息管理

* MYDB的启动信息存储在**bt**文件中，其中所需的信息只有一个，即头表的UID。
* **Booter**类提供了**load**和**update**两个方法，用于加载和更新启动信息。
* **update**方法在修改**bt**文件内容时，采取了一种保证原子性的策略，即先将内容写入一个临时文件**bt_tmp**中，然后通过操作系统的重命名操作将临时文件重命名为**bt**文件。
* 通过这种方式，利用操作系统重命名文件的原子性，来确保对**bt**文件的修改操作是原子的，从而保证了启动信息的一致性和正确性。

# D10.C/S

MYDB 被设计为 C/S 结构，类似于 MySQL。支持启动一个服务器，并有多个客户端去连接，通过 socket 通信，执行 SQL 返回结果。

网络编程

完结

