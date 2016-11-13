package org.eclipse.jgit.internal.storage.dfs;

import java.util.UUID;

public class CassandraPack extends DfsPackDescription {
    
    private final UUID id;
    
    public CassandraPack(UUID id, DfsRepositoryDescription repoDesc) {
        super(repoDesc, id.toString());
        this.id = id;
    }
    
    public UUID getId() {
        return id;
    }
}
