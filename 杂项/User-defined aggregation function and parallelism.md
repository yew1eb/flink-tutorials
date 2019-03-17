Flink's built-in aggregation functions are implemented against the same
interface as UDAGGs and are applied in parallel.
The performance depends of course on the implementation of the UDAGG. For
example, you should try to keep the size of the accumulator as small as
possible because it will be stored in the state backend.
If you are using the RocksDBStatebackend, this means that the accumulator
is de/serialized for every records.

Best, Fabian


unfortunately, selecting the parallelism for parts of a SQL query is not 
supported yet. By default, tumbling window operators use the default 
parallelism of the environment. Simple project and select operations 
have the same parallelism as the inputs they are applied on.

I think the easiest solution so far is to explicilty set the parallelism 
of operators that are not part of the Table API and use the 
environment's parallelism to scale the SQL query.

I hope that helps.

Regards,
Timo