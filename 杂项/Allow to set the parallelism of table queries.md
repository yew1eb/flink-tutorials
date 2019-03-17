Right now the parallelism of a table program is determined by the parallelism of the stream/batch environment.
E.g., by default, tumbling window operators use the default parallelism of the environment.
Simple project and select operations have the same parallelism as the inputs they are applied on.

While we cannot change forwarding operations because this would change the results when using retractions,
 it should be possible to change the parallelism for operators after shuffling operations.

It should be possible to specify the default parallelism of a table program in the TableConfig and/or QueryConfig.
The configuration per query has higher precedence that the configuration per table environment.


<https://issues.apache.org/jira/browse/FLINK-8236>