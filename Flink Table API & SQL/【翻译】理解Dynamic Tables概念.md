## Why Dynamic Tables ?
动态表直观上看是一个类似于数据库中的Materialized View概念。动态表随着时间改变；
类似静态的batch table一样可以用标准SQL进行查询然后一个新的动态表；可以和流无损地互相转换(对偶的)。对现有的API最大的改进关键在表的内容随着时间改变，
![](https://dn-mtunique.qbox.me/dyn_table_pipline.jpg)

## Stream --> Dynamic Table
流被转换成Table时决定选择哪种模式是依据表的schema是否定义primary key。

### AppendStreamTableSink
如果表的schema没有包括key的定义那转换成表时采用append模式。把流中每条新来的record当做新的row append到表中。
一旦数据加到表中就不能再被更新和删除(指当前表中，不考虑转换成新表)。
![](https://dn-mtunique.qbox.me/stream2t_append.jpg)
```
AppendStreamTableSink<T> extends TableSink<T> {

  public void emitDataStream(DataStream<T> dataStream);
}
```
If the table is also modified by update or delete changes, a TableException will be thrown.


### RetractStreamTableSink
Defines an external TableSink to emit a streaming table with insert, update, and delete changes.
```
RetractStreamTableSink<T> extends TableSink<Tuple2<Boolean, T>> {

  public TypeInformation<T> getRecordType();

  public void emitDataStream(DataStream<Tuple2<Boolean, T>> dataStream);
}
```
The table will be converted into a stream of accumulate and retraction messages which are encoded as Java Tuple2. 
The first field is a boolean flag to indicate the message type (true indicates insert, false indicates delete). 
The second field holds the record of the requested type T.


### UpsertStreamTableSink
Defines an external TableSink to emit a streaming table with insert, update, and delete changes.
![](https://dn-mtunique.qbox.me/stream2t_replace.jpg)
```
UpsertStreamTableSink<T> extends TableSink<Tuple2<Boolean, T>> {

  public void setKeyFields(String[] keys);

  public void setIsAppendOnly(boolean isAppendOnly);

  public TypeInformation<T> getRecordType();

  public void emitDataStream(DataStream<Tuple2<Boolean, T>> dataStream);
}
```
The table must be have unique key fields (atomic or composite) or be append-only. 
If the table does not have a unique key and is not append-only, a TableException will be thrown. 
The unique key of the table is configured by the UpsertStreamTableSink#setKeyFields() method.

The table will be converted into a stream of upsert and delete messages which are encoded as a Java Tuple2. 
The first field is a boolean flag to indicate the message type. The second field holds the record of the requested type T.

A message with true boolean field is an upsert message for the configured key. 
A message with false flag is a delete message for the configured key. 
If the table is append-only, all messages will have a true flag and must be interpreted as insertions.


### Dynamic Table 到 流
表到流的操作是把表的所有change以changelog stream的方式发送到下游。这一步也有两种模式。
类似DBMS里面概念undo, redo

对于动态表只有下面两种模式

#### Retraction模式：(Undo+Redo)
traction模式中对于Dynamic Table的insert和delete的change分别产生insert或delete event。
如果是update的change会产生两种change event，对于之前发送出去的同样key的record会产生delete event，对于当前的record是产生insert event。如下图所示：
![](https://dn-mtunique.qbox.me/t2stream_retract.jpg)




#### Update模式：（redo)
update模式依赖Dynamic Table定义了key。所有的change event是一个kv对。
key对应表的key在当前record中的值；对于insert和change value对应新的record。
对于delete value是空表示该可以已经被删除。如下图所示：
![](https://dn-mtunique.qbox.me/t2stream_replace.jpg)


### example
表的内容随着时间改变意味着对表的query结果也是随着时间改变的。我们定义：

A[t]: 时间t时的表A
q(A[t])：时间t时对表A执行query q
举个例子来理解动态表的概念：
![](https://dn-mtunique.qbox.me/t2stream_replace.jpg)


### query的限制
由于流是无限的，相对应 Dynamic Table 也是无界的。当查询无限的表的时候我们需要保证query的定时是良好的，有意义可行的。

1.在实践中Flink将查询转换成持续的流式应用，执行的query仅针对当前的逻辑时间，所以不支持对于任意时间点的查询(A[t])。
2.最直观的原则是query可能的状态和计算必须是有界的，所以可以支持可增量计算的查询：

不断更新当前结果的查询：查询可以产生insert，update和delete更改。查询可以表示为 Q(t+1) = q'(Q(t), c(T, t, t+1))，其中Q(t)是query q的前一次查询结果，c(T, t, t_+1) 是表T从t+1到t的变化, q’是q的增量版本。
产生append-only的表，可以从输入表的尾端直接计算出新数据。查询可以表示为 Q(t+1) = q''(c(T, t-x, t+1)) ∪ Q(t)，q’’是不需要时间t时q的结果增量版本query q。c(T, t-x, t+1)是表T尾部的x+1个数据，x取决于语义。例如最后一小时的window aggregation至少需要最后一小时的数据作为状态。其他能支持的查询类型还有：单独在每一行上操作的SELECT WHERE；rowtime上的GROUP BY子句（比如基于时间的window aggregate）；ORDER BY rowtime的OVER windows(row-windows)；ORDER BY rowtime。
3.当输入表足够小时，对表的每条数据进行访问。比如对两个大小固定的流表(比如key的个数固定)进行join。

### 中间状态有界
如上文所说的，某些增量查询需要保留一些数据（部分输入数据或者中间结果）作为状态。
为了保证query不会失败，保证查询所需要的空间是有界的不随着时间无限增长很重要。主要有两个原因使得状态增长：

不受时间谓词约束的中间计算状态的增长（比如 聚合key的膨胀）
时间有界但是需要迟到的数据（比如 window 的聚合）
虽然第二种情况可有通过下文提到的”Last Result Offset”参数解决，但是第一种情况需要优化器检测。我们应该拒绝不受时间限制的中间状态增长的查询。优化器应该提供如何修复查询且要求有适当的时间谓词。比如下面这个查询：

SELECT user, page, COUNT(page) AS pCnt
FROM pageviews
GROUP BY user, page
随着用户数和页面数的增长，中间状态会数据随着时间推移而增长。对于存储空间的要求可以通过添加时间谓词来限制：

SELECT user, page, COUNT(page) AS pCnt
FROM pageviews
WHERE rowtime BETWEEN now() - INTERVAL '1' HOUR AND now() // only last hour
GROUP BY user, page
因为不是所有属性都是不断增长的, 因此可以告诉优化器domain的size, 就可以推断中间状态不会随着时间推移而增长，然后接受没有时间谓词的查询。

val sensorT: Table = sensors
  .toTable('id, 'loc, 'stime, 'temp)
  .attributeDomain('loc, Domain.constant) // domain of 'loc is not growing 
env.registerTable("sensors", sensorT)
SELECT loc, AVG(temp) AS avgTemp
FROM sensors
GROUP BY loc

### 结果的计算和细化时序
一些关系运算符必须等数据到达才能计算最终结果。例如：在10：30关闭的窗口至少要等到10：30才能计算出最终的结果。Flink的logical clock（即 决定何时才是10：30）取决于使用event time 还是 processing time。在processing time的情况下，logical time是每个机器的wallclock；在event time的情况下，logical clock time是由源头提供的watermark决定的。由于数据的乱序和延迟当在event time模式下时等待一段时间来减小计算结果不完整性。另一方面某些情况下希望得到不断改进的早期结果。因此对于结果被计算、改进或者做出最终结果时有不同的要求、

下图描绘了不同的配置参数如何用于控制早期结果和细化计算结果的。
![](https://dn-mtunique.qbox.me/time_conf.jpg)



“First Result Offset” 指第一个早期结果被计算的结果的时间。时间是相对于第一次可以计算完整结果的时间（比如相对于window的结束时间10:30）。如果设置的是-10分钟，对于结束时间是10：30的window那么第一个被发出去的结果是在逻辑时间10：20计算的。这个参数的默认值是0，即在window结束的时候才计算结果。
“Complete Result Offset” 指完整的结果被计算的时间。时间是相对于第一次可以计算完整的时间。如果设置的是+5分钟，对于结束时间是10：30的window那么产生完整结果的时间是10：35。这个参数可以减轻延迟数据造成的影响。默认是0，即在window结束的时候计算的结果就是完整结果。
“Update Rate” 指计算完整结果之前一次次更新结果的时间间隔（可以是时间和次数）。如果设为5分钟，窗口大小是30分钟的tumbling window，开始时间是10：300，”First Result Offset”是-15分钟， “Complete Result Offset”是2分钟，那么将在10:20, 10:25, 10：30更新结果，10:15禅城寄一个结果，10：32产生完整结果。
“Last Updates Switch” 指完整结果发出后对于延迟的数据是否计算延迟更新，直到计算状态被清除。
“Last Result Offset” 指可计算的最后一个结果的时间。这是内部状态被清除的时间，清除状态后再到达的数据将被丢弃。Last Result Offset 意味着计算的结果是近似值，不能保证精确。

## 参考文献
+ [Continuous Queries on Dynamic Tables - 2017](http://flink.apache.org/news/2017/04/04/dynamic-tables.html)
+ [Add support for Retraction in Table API / SQL - 2017 - done](https://issues.apache.org/jira/browse/FLINK-6047)
