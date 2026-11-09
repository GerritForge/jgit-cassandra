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

package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertEquals;

import com.datastax.driver.core.Cluster;
import java.util.Set;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CassandraClusterTest {

  CassandraCluster cassandraCluster;

  @Rule
  public CassandraCQLUnit cassandraCQLUnit =
      new CassandraCQLUnit(new ClassPathCQLDataSet("test.cqlsh", "git_store"));

  @Before
  public void setup() {
    Cluster cluster = cassandraCQLUnit.getCluster();
    ClusterConfig config = new ClusterConfig();
    config.setClusterName(cluster.getClusterName());
    config.setContactPoints(new String[] {"localhost"});
    config.setPort(9142);
    cassandraCluster = new CassandraCluster(config);
  }

  @Test
  public void testDescription() {
    Set<String> projectList = cassandraCluster.list();
    assertEquals(projectList.size(), 0);
    cassandraCluster.setProjectDescription("myproject", "a description");
    String projectDescription = cassandraCluster.getProjectDescription("myproject");
    assertEquals(projectDescription, "a description");
    projectList = cassandraCluster.list();
    assertEquals(projectList.size(), 1);
    assertEquals(projectList.iterator().next(), "myproject");
  }
}
