package org.eclipse.jgit.internal.storage.dfs;


import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.Config;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

public class CassandraCluster {
    private final ClusterConfig clusterConfig;
    
    private final Cluster cluster;
    final PreparedStatement listProjectsStmt;
    final PreparedStatement setProjectDescStmt;
    final PreparedStatement getProjectDescStmt;
    final Session session;
    
    public CassandraCluster(Config serverConfig) {
        this(new ClusterConfig().fromConfig(serverConfig));
    }
    
    public CassandraCluster(ClusterConfig clusterConfig){
        this.clusterConfig = clusterConfig;
        this.cluster = Cluster.builder().addContactPoints(clusterConfig.getContactPoints())
            .withPort(clusterConfig.getPort())
            .withClusterName(clusterConfig.getClusterName()).build();
        this.session = cluster.newSession();
        this.listProjectsStmt = session.prepare("SELECT name FROM git_store.project_description");
        this.setProjectDescStmt = session.prepare(
            insertInto("git_store", "project_description")
                .value("name", bindMarker("name"))
                .value("description", bindMarker("description")).toString());
        this.getProjectDescStmt =
            session.prepare(select("description")
                .from("git_store", "project_description")
                .where(eq("name", bindMarker("name"))).toString());
    }
    
    public Cluster getCluster() {
        return cluster;
    }
    
    public Set<String> list() {
        ResultSet rst = session.execute(listProjectsStmt.bind());
        Set<String> projects = new HashSet<>();
        if (!rst.isExhausted()) {
            projects = rst.all().stream().map(r -> r.getString("name")).collect(Collectors.toSet());
        }
        return projects;
    }
    
    public String getProjectDescription(String name) {
        ResultSet rst = session.execute(getProjectDescStmt.bind().setString("name", name));
        String description = "";
        if (!rst.isExhausted()) {
            description = rst.one().getString("description");
        }
        return description;
    }
    
    public void setProjectDescription(String name, String description) {
        session.execute(setProjectDescStmt
            .bind()
            .setString("name", name)
            .setString("description", description));
    }
}
