## How to Join a dimension table in flink sql





## Late events in streaming using SQL API

Hi Juan,  currently, there is no way of handling late events in SQL. This feature got requested multiple times so it is likely that some contributor will pick it up soon. I filed FLINK-10031 [1] for it. There is also [2] that aims for improving the situation with time windows.  Regards, Timo  [1] <https://issues.apache.org/jira/browse/FLINK-10031> [2] <https://issues.apache.org/jira/browse/FLINK-6969> Am 02.08.18 um 14:36 schrieb Juan Gentile:



## Support enrichment joins in Flink SQL/Table API 

https://issues.apache.org/jira/browse/FLINK-9712



## SQL Do Not Support Custom Trigger

Hi,

Although this solution looks straight-forward, custom triggers cannot be added that easily.

The problem is that a window operator with a Trigger that emit early results produces updates, i.e., results that have been emitted might be updated later.

The default Trigger only emits the final result and hence does not produce updates.

This is an important difference, because all following operators need to be aware of the updates and be able to process them to prevent incorrect results.

Therefore, the query planner needs to be aware of the semantics of the Trigger. This would not be the case if it would be set via the StreamExecutionEnvironment.

There is a proposal to add an EMIT clause to SQL queries to control the rate at which results are emitted [1] that might be interesting.

Best,

Fabian

[1] <https://docs.google.com/document/d/1wrla8mF_mmq-NW9sdJHYVgMyZsgCmHumJJ5f5WUzTiM/edit?ts=59816a8b#heading=h.1zg4jlmqwzlr>



## Slide Window Compute Optimization

 Hi，      I want to use slide windows of 1 hour window size and 1 second step  size. I found that once a element arrives, it will be processed in 3600  windows serially through one thread. It takes serveral seconds to finish one  element processing，much more than my expection. Do I have any way to  optimizate it？      Thank you very much for your reply.  

Hi Yennie,

You might want to have a look at the OVER windows of Flink's Table API or SQL [1].

An OVER window computes an aggregate (such as a count) for each incoming record over a range of previous events.

For example the query:

SELECT ip, successful, COUNT(*) OVER (PARTITION BY ip, successful ORDER BY loginTime RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND CURRENT ROW) 

  FROM logins;

computes for each login attempt the number of login attempts of the previous hour.

There is no corresponding built-in operator in the DataStream API but SQL and Table API queries can be very easily integrated with DataStream programs [2].

Best, Fabian

[1] <https://ci.apache.org/projects/flink/flink-docs-release-1.5/dev/table/sql.html#aggregations>

[2] <https://ci.apache.org/projects/flink/flink-docs-release-1.5/dev/table/common.html#integration-with-datastream-and-dataset-api>



## SQL DDL

## SQL数据类型

### 用于描述地理位置的数据类型

calcite社区：
Support OpenGIS Simple Feature Access SQL<https://issues.apache.org/jira/browse/CALCITE-1968>
Implement more OpenGIS functions<https://issues.apache.org/jira/browse/CALCITE-2031>
Spatial Indexes<https://issues.apache.org/jira/browse/CALCITE-1861>

OpenGIS(Open Geodata Interoperation Specification,OGIS-开放的地理数据互操作规范
Support OpenGIS Simple Feature Access SQL. This is standard core functionality for spatial/geographical/geometry data, implemented by PostGIS and others.

It basically consists of a GEOMETRY data type and about 150 functions whose names start with 'ST_', for example ST_GeomFromText and ST_Distance. H2 GIS has a good reference. (Look for the functions labeled 'OpenGIS 1.2.1'.)

Adds a SqlConformance.allowGeometry() method to allow people to enable/disable this functionality.

First commit will probably add a dozen or so functions, backed by a library such as ESRI Geometry API. I'd appreciate help implementing the rest.

Spatial indexes are covered by CALCITE-1861 and are out of scope of this case. So with this feature, spatial queries will work, but predicates will be applied row-by-row.

flink社区：
Add support for OpenGIS features in Table & SQL API<https://issues.apache.org/jira/browse/FLINK-9219>

## Milestone of Storm SQL

<https://cwiki.apache.org/confluence/display/STORM/Milestone+of+Storm+SQL>

## Allow to set the parallelism of table queries
<https://issues.apache.org/jira/browse/FLINK-8236>


## Question about Timestamp in Flink SQL
<http://mail-archives.apache.org/mod_mbox/flink-user/201711.mbox/%3C351FD9AB-7A28-4CE0-BD9C-C2A15E5372D6@163.com%3E>
<https://stackoverflow.com/questions/47392363/change-timezone-in-flink-sql>

