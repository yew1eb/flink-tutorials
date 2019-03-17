


Hi,
There are APIs to convert a dynamic table to retract stream. 
Is there any way to construct a "retract" data stream and convert it into table? 
I want to read the change log of relational database from kafka, 
"apply" the changes within flink( by creating CRow DataStream), 
register/create a table on the CRow DataStream. Is there any way to do this?
Best

Yan 

Hi Yan,

there are no table source interfaces that allow for creating a retract 
stream directly yet. Such an interface has to be carefully designed 
because built-in operators assume that only records that have been 
emitted previously are retracted. However, they are planned for future 
Flink versions.

As a workaround you could implement a custom rule that translates parts 
of your plan into a custom DataStream operator. This might require some 
investigation how the translation is done internally because this is not 
documented. I don't know if it would be worth the effort. You might take 
a look at TableEnvironment.getConfig().setCalciteConfig() where you can 
add additional rules. You can use the available rules in 
org.apache.flink.table.plan.rules.FlinkRuleSets as a reference.

I hope this helps.

Regards,
Timo