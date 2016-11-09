package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;

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
    private final CassandraObjDatabase cassandraObjDatabase;
    

    public CassandraRepository(Cluster cluster, DfsRepositoryDescription repoDesc){
        this(cluster, new Builder().setRepositoryDescription(repoDesc), new DfsBlockCacheConfig());
    }
    
    protected CassandraRepository(Cluster cluster, DfsRepositoryBuilder builder, DfsBlockCacheConfig dfsBlockCacheConfig) {
        super(builder);
        this.cluster = cluster;
        this.cassandraObjDatabase = new CassandraObjDatabase(cluster.newSession(), this, new DfsReaderOptions(), dfsBlockCacheConfig.getBlockSize());
    }
    
    @Override
    public DfsObjDatabase getObjectDatabase() {
        return cassandraObjDatabase;
    }
    
    @Override
    public RefDatabase getRefDatabase() {
        return null;
    }
}
