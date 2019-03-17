参考 Mac OS X EI Captain 安装 Hadoop 3.0.0-alpha2 <https://www.jianshu.com/p/0e7f16469d87> 

NameNode - <http://localhost:9870>

ResourceManager - <http://localhost:8088>

 HADOOP_HOME=/usr/local/hadoop-3.1.0/

cd    ${HADOOP_HOME}

启动

sbin/start-dfs.sh
sbin/start-yarn.sh

```
jps查看进程情况：
97371 DataNode
97642 SecondaryNameNode
98266 ResourceManager
98366 NodeManager
```




停止
sbin/stop-all.sh