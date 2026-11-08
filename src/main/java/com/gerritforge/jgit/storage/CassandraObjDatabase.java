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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream;
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;
import org.eclipse.jgit.internal.storage.pack.PackExt;

public class CassandraObjDatabase extends DfsObjDatabase {

  //    CREATE TABLE git_store.pack( id UUID, offset DOUBLE, value BLOB, PRIMARY KEY ((id,offset)));

  //    CREATE TABLE git_store.pack_list( id TEXT, packs FROZEN<SET<FROZEN<TUPLE<UUID, double>>>>,
  // PRIMARY KEY (id));

  private final Session session;
  private final PreparedStatement readPack;
  private final PreparedStatement insertPack;
  private final PreparedStatement readPackList;
  private final PreparedStatement updatePackList;

  protected CassandraObjDatabase(
      Session session, DfsRepository repository, DfsReaderOptions options) {
    super(repository, options);
    this.session = session;
    readPack =
        session.prepare(
            QueryBuilder.select("value")
                .from("git_store", "pack")
                .where(QueryBuilder.eq("id", QueryBuilder.bindMarker()))
                .and(QueryBuilder.eq("offset", QueryBuilder.bindMarker()))
                .toString());

    insertPack =
        session.prepare(
            QueryBuilder.insertInto("git_store", "pack")
                .value("id", QueryBuilder.bindMarker())
                .value("offset", QueryBuilder.bindMarker())
                .value("value", QueryBuilder.bindMarker())
                .toString());

    readPackList =
        session.prepare(
            QueryBuilder.select("packs")
                .from("git_store", "pack_list")
                .where(QueryBuilder.eq("id", QueryBuilder.bindMarker()))
                .toString());

    updatePackList =
        session.prepare(
            QueryBuilder.insertInto("git_store", "pack_list")
                .value("id", QueryBuilder.bindMarker())
                .value("packs", QueryBuilder.bindMarker())
                .toString());
  }

  @Override
  protected DfsPackDescription newPack(PackSource packSource) throws IOException {
    return null;
  }

  @Override
  protected void commitPackImpl(
      Collection<DfsPackDescription> collection, Collection<DfsPackDescription> collection1)
      throws IOException {}

  @Override
  protected void rollbackPack(Collection<DfsPackDescription> collection) {}

  @Override
  protected List<DfsPackDescription> listPacks() throws IOException {
    return null;
  }

  @Override
  protected ReadableChannel openFile(DfsPackDescription dfsPackDescription, PackExt packExt)
      throws FileNotFoundException, IOException {
    return null;
  }

  @Override
  protected DfsOutputStream writeFile(DfsPackDescription dfsPackDescription, PackExt packExt)
      throws IOException {
    return null;
  }
}
