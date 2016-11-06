### JGIT - Cassandra
JGit with Apache Cassandra database as storage backend. 
  
Have an instance of Cassandra running or use ccm Cassandra Cluster 
Manager https://github.com/pcmanus/ccm.

Create one cassandra instance with latest version 
`ccm create jgit -v 3.0.9`
populate 1 instance `ccm populate -n 1`
start instance `ccm start`

To access cqlsh via `ccm node1 cqlsh`


