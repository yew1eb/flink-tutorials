Hi Piotr,

thanks for bumping this thread and thanks for Xingcan for the comments.

I think the first step would be to separate the flink-table module into
multiple sub modules. These could be:

- flink-table-api: All API facing classes. Can be later divided further
into Java/Scala Table API/SQL
- flink-table-planning: involves all planning (basically everything we do
with Calcite)
- flink-table-runtime: the runtime code

IMO, a realistic mid-term goal is to have the runtime module and certain
parts of the planning module ported to Java.
The api module will be much harder to port because of several dependencies
to Scala core classes (the parser framework, tree iterations, etc.). I'm
not saying we should not port this to Java, but it is not clear to me (yet)
how to do it.

I think flink-table-runtime should not be too hard to port. The code does
not make use of many Scala features, i.e., it's writing very Java-like.
Also, there are not many dependencies and operators can be individually
ported step-by-step.

For flink-table-planning, we can have certain packages that we port to Java
like planning rules or plan nodes.
 The related classes mostly extend
Calcite's Java interfaces/classes and would be natural choices for being
ported. 
The code generation classes will require more effort to port. There
are also some dependencies in planning on the api module that we would need
to resolve somehow.

For SQL most work when adding new features is done in the planning and
runtime modules. So, this separation should already reduce "technological
dept" quite a lot.
The Table API depends much more on Scala than SQL.

Cheers, Fabian

2018-07-02 16:26 GMT+02:00 Xingcan Cui <[EMAIL PROTECTED]>: