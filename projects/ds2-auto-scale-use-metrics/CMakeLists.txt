cmake_minimum_required(VERSION 3.4)
project(ds2_auto_scale_use_metrics)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES
    ds2/config/ds2.toml
    ds2/examples/source_rates/source-rates.csv
    ds2/examples/topology/flink_topology.csv
    ds2/examples/topology/heron_topology.csv
    ds2/src/dataflow/mod.rs
    ds2/src/dataflow/parse.rs
    ds2/src/dataflow/topology.rs
    ds2/src/manager/mod.rs
    ds2/src/manager/monitor.rs
    ds2/src/policy/mod.rs
    ds2/src/policy/scaling.rs
    ds2/src/lib.rs
    ds2/src/main.rs
    ds2/src/monitor.rs
    ds2/tests/scaling_policy_test.rs
    ds2/Cargo.toml
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-0.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-1.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-10.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-2.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-3.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-4.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-5.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-6.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-7.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-8.log"
    "flink-examples/example-wc-rates/Count -> Latency Sink-1-9.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-0.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-1.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-10.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-2.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-3.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-4.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-5.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-6.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-7.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-8.log"
    "flink-examples/example-wc-rates/Source: Custom Source-1-9.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-0.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-1.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-10.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-2.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-3.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-4.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-5.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-6.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-7.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-8.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-1-9.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-0.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-1.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-10.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-2.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-3.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-4.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-5.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-6.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-7.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-8.log"
    "flink-examples/example-wc-rates/Splitter FlatMap-2-9.log"
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/common/RandomSentenceGenerator.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/queries/Query1.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/queries/Query11.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/queries/Query2.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/queries/Query3.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/queries/Query3Stateful.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/queries/Query5.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/queries/Query8.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/sinks/DummyLatencyCountingSink.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/sinks/DummySink.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/sources/AuctionSourceFunction.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/sources/BidSourceFunction.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/nexmark/sources/PersonSourceFunction.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/wordcount/sources/DynamicRateSourceFunction.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/wordcount/sources/RateControlledSourceFunction.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/wordcount/RateControlledWordCount.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/wordcount/StatefulWordCount.java
    flink-examples/src/main/java/ch/ethz/systems/strymon/ds2/flink/wordcount/TwoInputsWordCount.java
    flink-examples/src/main/resources/words.txt
    flink-examples/pom.xml
    flink-instrumentation/ds2.patch
    flink-instrumentation/README.md
    flink-scaling-scripts/change-wordcount-parallelism.sh
    flink-scaling-scripts/README.md
    flink-scaling-scripts/start-wordcount.sh
    AUTHORS
    LICENSE-APACHE
    LICENSE-MIT
    README.md)

add_executable(ds2_auto_scale_use_metrics ${SOURCE_FILES})