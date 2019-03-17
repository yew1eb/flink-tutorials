原文链接：<https://ci.apache.org/projects/flink/flink-docs-release-1.4/internals/filesystems.html>
[TOC]

# File Systems
Flink通过org.apache.flink.core.fs.FileSystem类来抽象自己的文件系统，这个抽象提供了各类文件系统实现的通用操作和最低保证。

此文件系统的可用操作非常有限，以支持广泛的其它文件系统，例如追加或者变更已有文件就不被支持。

文件系统由其格式来区别，例如file://, hdfs://，等等。

## Implementations(实现)
Flink使用以下文件系统方案直接实现其文件系统：

* file, 表示机器的本地文件系统

其它文件系统类型由一个实现来桥接到apache hadoop支持文件系统，下面是一个不完整的示例列表(都很好理解)：

* hdfs: Hadoop Distributed File System
* s3, s3n, and s3a: Amazon S3 file system
* gcs: Google Cloud Storage
* maprfs: The MapR distributed file system
* …

如果Flink从class patch中找到hadoop文件系统并找到hadoop配置，就会透明的加载hadoop文件系统。默认情况下，Flink会从class patch中查找hadopp配置，也可以通过fs.hdfs.hadoopconf来指定配置的位置。

## Persistence Guarantees(持久化保证)
这些FileSystem和其FsDataOutputStream实例用于持久存储数据，无论是应用程序的结果还是容错和恢复的数据。因此，这些流的持久性语义被很好地定义是至关重要的。

Definition of Persistence Guarantees(持久化保证的定义)
写入输出流的数据被认为是持久的，如果满足两个要求：

* 可见性要求：当给定文件绝对路径时，必须保证所有其他进程，计算机，虚拟机，容器等能够访问文件并一致地查看数据。这个要求类似于 POSIX定义的close-to-open语义，但是限于文件本身（由于其绝对路径）。
* 待久性要求：必须满足文件系统特定的耐久性/持久性要求，指一些特定的文件系统，例如机器LocalFileSystem不会为硬件和操作系统的崩溃提供任何持久性保证，而具有备份的分布式文件系统（如HDFS）通常在n个节点发生故障的情况下仍能保证可用性。

Updates to the file’s parent directory (such that the file shows up when listing the directory contents) are not required to be complete for the data in the file stream to be considered persistent. This relaxation is important for file systems where updates to directory contents are only eventually consistent.

上面这段原文不知道怎么翻译，我就说一下我的理解哈。

file stream的写入只保证最终的一致性，而不能保证完全一致性。举个例子，当update一个file，这个file可能正写入，此时用ls用查看这个file，可能看不到，因为正在写入，但最终这个file会写完，file写完后就能看到了。这种方式对文件系统非常重要。

FSDataOutputStream一旦调用FSDataOutputStream.close()返回，就必须保证写入数据的持久性。

## Examples

* 对于容错的分布式文件系统，数据被文件系统接收和确认后，就能确定会被复制到哪些机器上（待久性要求），此外，这些文件的绝对路径必须对所有其他机器都可见且可访问（可见性要求）。
  数据是否会对存储节点的持久存储器造成影响取决于文件系统的具体保证。
  文件的父目录的元数据更新不需要达到一致状态。列出父目录的内容时，允许一些机器看到该文件，而其他机器则不会通过绝对路径访问到此文件。

* 本地文件系统必须支持POSIX close-to-open语义。由于本地文件系统没有任何容错保证，因此没有更高的要求。

  上述内容表明，从本地文件系统的角度来看，当数据被持久时，数据可能仍然在系统缓存中（而不是马上就被写到磁盘）。一些不能预料的冲击可能对系统缓存产生影响，导致数据丢失，这对系统来说有可能是致命的，并且不被Flink定义的本地文件系统的保证所涵盖。

  上述具体意味着当从本地文件系统的角度考虑持续时，。导致操作系统缓存松动数据的崩溃对于本地机器而言是致命的，并且不被Flink定义的本地文件系统的保证所涵盖。

这意味着将仅仅将计算结果、checkpoints、savepoints写入本地文件系统是无法保证机器能从故障中恢复，因此本地文件系统并不适合实现生产设定。

## Updating File Contents(更新文件内容)
许多文件系统根本不支持覆盖现有文件的内容，或者在这种情况下不支持更新内容的一致可见性。因此，Flink的FileSystem不支持已有文件的追加操作，但支持找到之前写数据的输出流，并修改其中的内容。

## Overwriting Files(文件覆盖)
通常覆盖文件的方式是先删除，再创建，但是某些文件系统无法使这种改变对所有访问方都同步可见。例如，Amazom S3仅保证替换文件的可见性为最终一致性，这意味着有些机器可能看到的是旧文件 ，而有些机器可能看到新文件。

为了避免这些一致性问题，Flink中的故障/恢复机制实现严格避免多次写入相同的文件路径。

## Thread Safety(线程安全)
FileSystem的实现必须是线程安全的：Flink的FileSystem通常在多个线程中共享相同的实例，而且必须能够同时创建输入/输出流并列出文件元数据。

FSDataOutputStream和FSDataOutputStream的实现都不是严格线程安全的。 流的实例也不应在读和写线程之间传递，因为不能保证跨线程的操作的可见性。

