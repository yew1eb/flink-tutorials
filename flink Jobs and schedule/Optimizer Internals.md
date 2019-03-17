This page gives an overview of the Flink program optimizer for batch programs.

The Flink optimizer works similarly to a relational Database Optimizer, but applies these optimizations to the Flink programs (that are written in general purpose languages), rather than SQL queries.

 

# Optimizations

The following optimizations are performed

- Joins: partitioning (shuffling) both inputs vs broadcasting one input
- Joins: hash-join vs. sort-merge join
- Reusing of partitionings and sort orders across operators. If one operator leaves the data in partitioned fashion (and or sorted order), the next operator will automatically try and reuse these characteristics. The planning for this is done holistically and can cause earlier operators to pick more expensive algorithms, if they allow for better reusing of sort-order and partitioning.
- Iterations: Caching of loop-invariant data
- Iterations: Trying to move expensive parts of operations outside the loop
- Iterations: Reusing data characteristics (partitioning / sort order) across supersteps
- Planning of pipeline and batch shuffles. Analysis which parts are deadlock-prone, if executed in a pipelined fashion.

 

The following optimizations are not performed

- Join reordering (or operator reordering in general): Joins / Filters / Reducers are not re-ordered in Flink. This is a high opportunity optimization, but with high risk in the absence of good estimates about the data characteristics. Flink is not doing these optimizations at this point.
- Index vs. Table Scan selection: In Flink, all data sources are always scanned. The data source (the input format) may apply clever mechanism to not scan all the data, but pre-select and project. Examples are the RCFile / ORCFile / Parquet input formats.

 

# Data Structures

Optimizer DAG

Execution Plan and Execution Plan candidates

 

# Properties and property matching

Global properties and local properties

Operators defined via properties

 

# Role of Semantic Properties

 

# Iterations

Static vs. dynamic paths

 