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

import static org.junit.Assert.*;

import com.datastax.driver.core.Cluster;
import java.io.IOException;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test copied from org.eclipse.jgit.internal.storage.dfs.DfsInserterTest */
public class CassandraInserterTest {

  CassandraRepository db;

  @Rule
  public CassandraCQLUnit cassandraCQLUnit =
      new CassandraCQLUnit(new ClassPathCQLDataSet("test.cqlsh", "git_store"));

  @Before
  public void setup() {
    Cluster cluster = cassandraCQLUnit.getCluster();
    db = new CassandraRepository(cluster, new DfsRepositoryDescription("test"));
  }

  @Test
  public void testInserterDiscardsPack() throws IOException {
    try (ObjectInserter ins = db.newObjectInserter()) {
      ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
      ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
      assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
    }
    assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
  }
}
