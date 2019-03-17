Hi Flink Community!

The release of Apache Flink 1.5 has happened (yay!) - so it is a good time to start talking about what to do for release 1.6.

== Suggested release timeline ==

I would propose to release around end of July (that is 8-9 weeks from now).

The rational behind that: There was a lot of effort in release testing automation (end-to-end tests, scripted stress tests) as part of release 1.5. You may have noticed the big set of new modules under "flink-end-to-end-tests" in the Flink repository. It delayed the 1.5 release a bit, and needs to continue as part of the coming release cycle, but should help make releasing more lightweight from now on.

(Side note: There are also some nightly stress tests that we created and run at data Artisans, and where we are looking whether and in which way it would make sense to contribute them to Flink.)

== Features and focus areas ==

We had a lot of big and heavy features in Flink 1.5, with FLIP-6, the new network stack, recovery, SQL joins and client, ... Following something like a "tick-tock-model", I would suggest to focus the next release more on integrations, tooling, and reducing user friction. 

Of course, this does not mean that no other pull request gets reviewed, an no other topic will be examined - it is simply meant as a help to understand where to expect more activity during the next release cycle. Note that these are really the coarse focus areas - don't read this as a comprehensive list.

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
     

Hi all!

A late follow-up with some thoughts:

In principle, all these are good suggestions and are on the roadmap. We are trying to make the release "by time", meaning for it at a certain date (roughly in two weeks) and take what features are ready into the release.

Looking at the status of the features that you suggested (please take this with a grain of salt, I don't know the status of all issues perfectly):

  - Proper sliding window joins: This is happening, see https://issues.apache.org/jira/browse/FLINK-8478
    At least inner joins will most likely make it into 1.6. Related to that is enrichment joins against time versioned tables, which are being worked on in the Table API: https://issues.apache.org/jira/browse/FLINK-9712

  - Bucketing Sink on S3 and ORC / Parquet Support: There are multiple efforts happening here.
    The biggest one is that the bucketing sink is getting a complete overhaul to support all of the mentioned features, see https://issues.apache.org/jira/browse/FLINK-9749 https://issues.apache.org/jira/browse/FLINK-9752 https://issues.apache.org/jira/browse/FLINK-9753
    Hard to say how much will make it until the feature freeze of 1.6, but this is happening and will be merged soon.

  - ElasticBloomFilters: Quite a big feature, I added a reply to the discussion thread, looking at whether this can be realized in a more loosely coupled way from the core state abstraction.

  - Per partition Watermark idleness: Noted, let's look at this more. There should also be a way to implement this today, with periodic watermarks (that realize when no records came for a while).
  
  - Dynamic CEP patterns: I don't know if anyone if working on this.
   CEP is getting some love at the moment, though, with SQL integration and better performance on RocksDB for larger patterns. We'll take a note that there are various requests for dynamic patterns.
  
  - Kubernetes Integration: There are some developments going on both for a "passive" k8s integration (jobs as docker images, Flink beine a transparent k8s citizen) and a more "active" integration, where Flink directly talks to k8s to start TaskManagers dynamically. I think the former has a good chance that a first version goes into 1.6, the latter needs more work.

  - Atomic Cancel With Savepoint: Not enough progress on this, yet. It is important, but needs more work.
  - Synchronizing streams: Same as above, acknowledged that this is important, but needs more work.

  - Standalone cluster job isolation: No work on this, yet, as far as I know.

  - Sharing state across operators: This is an interesting and tricky one, I left some questions on the JIRA issue https://issues.apache.org/jira/browse/FLINK-6239

Best,
Stephan