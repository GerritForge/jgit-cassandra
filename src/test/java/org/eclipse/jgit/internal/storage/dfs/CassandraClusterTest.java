package org.eclipse.jgit.internal.storage.dfs;

import java.util.Set;

import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.datastax.driver.core.Cluster;

import static org.junit.Assert.assertEquals;

public class CassandraClusterTest {
    
    CassandraCluster cassandraCluster;
    
    @Rule
    public CassandraCQLUnit cassandraCQLUnit = new CassandraCQLUnit(new ClassPathCQLDataSet("test.cqlsh", "git_store"));
    
    @Before
    public void setup() {
        Cluster cluster = cassandraCQLUnit.getCluster();
        ClusterConfig config = new ClusterConfig();
        config.setClusterName(cluster.getClusterName());
        config.setContactPoints(new String[]{"localhost"});
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
