package com.gerritforge.jgit.storage.cassandra;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryBuilder;
import org.eclipse.jgit.lib.RefDatabase;

import com.datastax.driver.core.Cluster;

public class CassandraRepository extends DfsRepository {
    
    public static class Builder extends DfsRepositoryBuilder<Builder, CassandraRepository> {
    
        @Override
        public CassandraRepository build() throws IOException {
            return null;
        }
    }
    
    private final Cluster cluster;
    
    protected CassandraRepository(Cluster cluster, DfsRepositoryBuilder builder) {
        super(builder);
        this.cluster = cluster;
    }
    
    @Override
    public DfsObjDatabase getObjectDatabase() {
        return null;
    }
    
    @Override
    public RefDatabase getRefDatabase() {
        return null;
    }
}
