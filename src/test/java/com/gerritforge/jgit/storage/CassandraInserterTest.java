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
