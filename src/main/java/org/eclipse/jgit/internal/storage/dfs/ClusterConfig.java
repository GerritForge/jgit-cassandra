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

import org.eclipse.jgit.lib.Config;

public class ClusterConfig {

  private String[] contactPoints;

  private String clusterName = "jgit_store";

  private int blockSize = 1024;
  private int port = 9042;

  public ClusterConfig fromConfig(final Config rc) {
    setClusterName(rc.getString("cassandra", null, "cluster"));
    setContactPoints(rc.getStringList("cassandra", null, "hosts"));
    setPort(rc.getInt("cassandra", null, "port", getPort()));
    setBlockSize(rc.getInt("cassandra", null, "blocksize", getBlockSize()));
    return this;
  }

  public String[] getContactPoints() {
    return contactPoints;
  }

  public void setContactPoints(String[] contactPoints) {
    this.contactPoints = contactPoints;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public int getBlockSize() {
    return blockSize;
  }

  public void setBlockSize(int blockSize) {
    this.blockSize = blockSize;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }
}
