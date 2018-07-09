By default, Flink allocates a fraction of 0.7 (taskmanager.memory.fraction) of 
the free memory (total memory configured via taskmanager.heap.mb minus memory 
used for network buffers) for its managed memory. An absolute value may be set 
using taskmanager.memory.size (overrides the fraction parameter). [1]
In general, [1] is a good read for how the different memory settings work 
together.

The 10000 network buffers of size taskmanager.memory.segment-<wbr style="color: rgb(34, 34, 34); font-family: arial, sans-serif; font-size: 14px; font-style: normal; font-variant-ligatures: normal; font-variant-caps: normal; font-weight: 400; letter-spacing: normal; orphans: 2; text-align: start; text-indent: 0px; text-transform: none; white-space: normal; widows: 2; word-spacing: 0px; -webkit-text-stroke-width: 0px; background-color: rgb(255, 255, 255); text-decoration-style: initial; text-decoration-color: initial;">size (default: 
32k) will this remove 320MB from your taskmanager.heap.mb (which in your 
example is set to 563GB?!) and therefore do not affect the managed memory size 
much.

Whether the size used by the JVM is preallocated--and thus immediately 
visible--depends on taskmanager.memory.<wbr style="color: rgb(34, 34, 34); font-family: arial, sans-serif; font-size: 14px; font-style: normal; font-variant-ligatures: normal; font-variant-caps: normal; font-weight: 400; letter-spacing: normal; orphans: 2; text-align: start; text-indent: 0px; text-transform: none; white-space: normal; widows: 2; word-spacing: 0px; -webkit-text-stroke-width: 0px; background-color: rgb(255, 255, 255); text-decoration-style: initial; text-decoration-color: initial;">preallocate.

But actually what are trying to fix? Is your node flushing data to disk yet? Or 
has it just not accumulated that much operator state yet?

Nico

[1] [https://ci.apache.org/<wbr>projects/flink/flink-docs-<wbr>release-1.3/setup/
config.html#managed-memory](https://ci.apache.org/projects/flink/flink-docs-release-1.3/setup/config.html#managed-memory)



configuring managed memory and taskmanager heap memory also depends on your jobs. If your jobs involve much sorting, then a high managed memory is helpful. If you are creating a lot of objects in your functions, that need some time until they get garbage collected (ideally you should not use `new` at all), then a larger Java heap might be useful instead of managed memory.
