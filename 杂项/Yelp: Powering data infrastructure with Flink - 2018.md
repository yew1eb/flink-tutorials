
## Data pipeline stack
![](img/yelp-data-pipeline-stack.png)

## Yelp'Data pipeline stack
![](img/yelp-data-pipeline-stack-2.png)

### Data Sources
Log Event
+ Low latency
+ Unordered
+ High throughput
+ Highly partitioned
+ Can have data loss
+ Can have duplicates
web service -> flume -> kafka

Data store changelog
+ offline
+ globally ordered
+ low throughput
+ single compacted partition
+ exactly-once delivery
+ supports insert/update/delete operations

### Data transformation in Flink,  DSL
![](img/yep-data-transformation-in-flink.png)


 data connector 
 审计接口、schema
## processing graph
![](img/yelp-process-graph.png)


## Flink Superivisor
![](img/yelp-flink-superivisor.png)