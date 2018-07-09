Copied from my earlier response to some similar question:

"Here is a short description for how it works: there are totally 3 threads working together, one for reading, one for sorting partial data in memory, and the last one is responsible for spilling. Flink will first figure out how many memory it can use during the in-memory sort, and manage them as MemorySegments. Once these memory runs out, the sorting thread will take over these memory and do the in-memory sorting (For more details about in-memory sorting, you can see NormalizedKeySorter). After this, the spilling thread will write this sorted data to disk and make these memory available again for reading. This will repeated until all data has been processed. 
Normally, the data will be read twice (one from source, and one from disk) and write once, but if you spilled too much files, flink will first merge some all the files and make sure the last merge step will not exceed some limit (default 128). Hope this can help you."

Best,
Kurt