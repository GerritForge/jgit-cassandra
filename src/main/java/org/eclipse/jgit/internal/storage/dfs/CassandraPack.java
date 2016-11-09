package org.eclipse.jgit.internal.storage.dfs;

import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;

public class CassandraPack extends DfsPackDescription {
    public CassandraPack(String name, DfsRepositoryDescription repoDesc) {
        super(repoDesc, name);
    }
}
