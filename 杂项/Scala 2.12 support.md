This is the umbrella issue for Scala 2.12 support. As Stephan pointed out, the ClosureCleaner and SAMs are currently the main problems. The first is also a problem for Spark, which track their respective progress here: <https://issues.apache.org/jira/browse/SPARK-14540>.

Hi Hao Sun!

This is work in progress, but Scala 2.12 is a bit tricky. I think the Scala folks have messed this version up a bit, to be honest.

The main blockers is that Scala 2.12 breaks some classes through its addition of SAM interface lambdas (similar to Java). Many of the DataStream API classes have two method variants (one with a Scala Function, one with a Java SAM interface) which now become ambiguously overloaded methods in Scala 2.12.

In addition, Scala 2.12 also needs a different closure cleaner, because Scala 2.12 compiles differently.

I am adding Aljoscha, who has started working on this...

  