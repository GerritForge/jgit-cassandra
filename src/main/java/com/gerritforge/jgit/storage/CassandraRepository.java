// Copyright (C) 2026 GerritForge, Inc.
//
// Licensed under the BSL 1.1 (the "License");
// you may not use this file except in compliance with the License.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.jgit.storage;

import com.datastax.driver.core.Cluster;
import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryBuilder;
import org.eclipse.jgit.lib.RefDatabase;

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
