



## Limiting in flight data

Yes, Flink 1.5.0 will come with better tools to handle this problem. Namely you will be able to limit the “in flight” data, by controlling the number of assigned credits per channel/input gate. Even without any configuring Flink 1.5.0 will out of the box buffer less data, thus mitigating the problem."

I read this in another email chain. The docs ( may be you can point me to them ) are not very clear on how to do the above. Any pointers will be appreciated.

Thanks much.

Hi Vishal,

Before Flink-1.5.0, the sender tries best to send data on the network until the wire is filled with data. From Flink-1.5.0 the network flow control is improved by credit-based idea. That means the sender transfers data based on how many buffers avaiable on receiver side, so there will be no data accumulated on the wire. From this point, the in-flighting data is less than before.

Also you can further limit the in-flighting data by controling the number of credits on receiver side, and the related parameters are taskmanager.network.memory.buffers-per-channel and taskmanager.network.memory.floating-buffers-per-gate. 

If you have other questions about them, let me know then i can explain for you.