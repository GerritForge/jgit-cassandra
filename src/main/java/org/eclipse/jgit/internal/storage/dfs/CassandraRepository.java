package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;

import org.eclipse.jgit.lib.RefDatabase;

import com.datastax.driver.core.Cluster;
import com.google.gson.Gson;

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
        this(cluster, new Builder().setRepositoryDescription(repoDesc));
    }
    
    protected CassandraRepository(Cluster cluster, DfsRepositoryBuilder builder) {
        super(builder);
        this.cluster = cluster;
        this.cassandraObjDatabase = new CassandraObjDatabase(cluster.newSession(), this, new DfsReaderOptions());
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
