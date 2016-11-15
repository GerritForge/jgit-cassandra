package org.eclipse.jgit.internal.storage.dfs;

import org.eclipse.jgit.lib.Config;

public class ClusterConfig {
    
    private String[] contactPoints;
    
    private String clusterName = "jgit_store";
    
    private int blockSize = 1024;
    private int port = 9042;
    
    public ClusterConfig fromConfig(final Config rc) {
        setClusterName(rc.getString("cassandra", null, "cluster"));
        setContactPoints(rc.getStringList("cassandra", null, "hosts"));
        setPort(rc.getInt("cassandra", null, "port", getPort()));
        setBlockSize(rc.getInt("cassandra", null, "blocksize", getBlockSize()));
        return this;
    }
    
    public String[] getContactPoints() {
        return contactPoints;
    }
    
    public void setContactPoints(String[] contactPoints) {
        this.contactPoints = contactPoints;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
    
    public int getBlockSize() {
        return blockSize;
    }
    
    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public int getPort() {
        return port;
    }
}
