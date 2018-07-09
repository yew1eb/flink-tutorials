This list is my first suggestion, based on discussions with committers, users, and mailing list questions.

  - Support Java 9 and Scala 2.12
  
  - Smoothen the integration in Container environment, like "Flink as a Library", and easier integration with Kubernetes services and other proxies.
  
  - Polish the remaing parts of the FLIP-6 rewrite

  - Improve state backends with asynchronous timer snapshots, efficient timer deletes, state TTL, and broadcast state support in RocksDB.

  - Extends Streaming Sinks:
     - Bucketing Sink should support S3 properly (compensate for eventual consistency), work with Flink's shaded S3 file systems, and efficiently support formats that compress/index arcoss individual rows (Parquet, ORC, ...)
     - Support ElasticSearch's new REST API

  - Smoothen State Evolution to support type conversion on snapshot restore
  
  - Enhance Stream SQL and CEP
     - Add support for "update by key" Table Sources
     - Add more table sources and sinks (Kafka, Kinesis, Files, K/V stores)
     - Expand SQL client
     - Integrate CEP and SQL, through MATCH_RECOGNIZE clause
     - Improve CEP Performance of SharedBuffer on RocksDB
     
     
Since wishes are free: 

- Standalone cluster job isolation: 
https://issues.apache.org/jira/browse/FLINK-8886
- Proper sliding window joins (not overlapping hoping window joins): 
https://issues.apache.org/jira/browse/FLINK-6243
- Sharing state across operators: 
https://issues.apache.org/jira/browse/FLINK-6239
- Synchronizing streams: https://issues.apache.org/jira/browse/FLINK-4558

Seconded: 
- Atomic cancel-with-savepoint: 
https://issues.apache.org/jira/browse/FLINK-7634
- Support dynamically changing CEP patterns : 
https://issues.apache.org/jira/browse/FLINK-7129