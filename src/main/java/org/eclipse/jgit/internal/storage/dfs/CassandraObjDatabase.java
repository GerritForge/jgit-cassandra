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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.utils.UUIDs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraObjDatabase extends DfsObjDatabase {

  private static final Logger log = LoggerFactory.getLogger(CassandraObjDatabase.class);

  final Session session;
  final int blockSize;
  final PreparedStatement readPack;
  final PreparedStatement insertPack;
  final PreparedStatement readPackList;
  final PreparedStatement insertToPackList;

  final Gson gson =
      (new GsonBuilder())
          .registerTypeHierarchyAdapter(CassandraPack.class, new CassandraPackTypeAdapter())
          .create();

  protected CassandraObjDatabase(
      Session session, DfsRepository repository, DfsReaderOptions options, int blockSize) {
    super(repository, options);
    this.session = session;
    this.blockSize = blockSize;
    readPack =
        session.prepare(
            select("value")
                .from("git_store", "packs")
                .where(eq("id", bindMarker("id")))
                .and(eq("ext", bindMarker("ext")))
                .and(eq("offset", bindMarker("offset")))
                .toString());

    insertPack =
        session.prepare(
            insertInto("git_store", "packs")
                .value("id", bindMarker("id"))
                .value("ext", bindMarker("ext"))
                .value("offset", bindMarker("offset"))
                .value("value", bindMarker("value"))
                .toString());

    readPackList =
        session.prepare(
            select("id", "ext", "size", "source", "lmd")
                .from("git_store", "pack_list")
                .where(eq("name", bindMarker("name")))
                .toString());

    insertToPackList =
        session.prepare(
            insertInto("git_store", "pack_list")
                .value("name", bindMarker("name"))
                .value("id", bindMarker("id"))
                .value("ext", bindMarker("ext"))
                .value("size", bindMarker("size"))
                .value("source", bindMarker("source"))
                .value("lmd", bindMarker("lmd"))
                .toString());
  }

  public int getBlockSize() {
    return blockSize;
  }

  @Override
  protected DfsPackDescription newPack(PackSource packSource) throws IOException {

    UUID id = UUIDs.timeBased();
    DfsPackDescription desc = new CassandraPack(id, getRepository().getDescription());
    return desc.setPackSource(packSource).setLastModified(System.currentTimeMillis());
  }

  @Override
  protected void commitPackImpl(
      Collection<DfsPackDescription> desc, Collection<DfsPackDescription> replace)
      throws IOException {
    BatchStatement batch = new BatchStatement();
    for (DfsPackDescription packDescription : desc) {
      CassandraPack cassandraPack = (CassandraPack) packDescription;
      log.info(
          "[PACK-CASS] repoName=[{}] id=[{}] source=[{}]",
          cassandraPack.getRepositoryDescription().getRepositoryName(),
          cassandraPack.getId(),
          cassandraPack.getPackSource());
      for (PackExt ext : PackExt.values()) {
        long size = cassandraPack.getFileSize(ext);
        if (size != 0) {
          batch.add(
              insertToPackList
                  .bind()
                  .setString("name", cassandraPack.getRepositoryDescription().getRepositoryName())
                  .setUUID("id", cassandraPack.getId())
                  .setString("ext", ext.getExtension())
                  .setLong("size", size)
                  .setString("source", cassandraPack.getPackSource().toString())
                  .setLong("lmd", cassandraPack.getLastModified()));
        }
      }
    }
    if (replace != null) {
      // TODO replace pack descriptions
      throw new IOException("replace not null");
    }
    if (batch.size() > 0) {
      session.execute(batch);
    }
  }

  @Override
  protected List<DfsPackDescription> listPacks() throws IOException {
    if (getRepository() == null
        || getRepository().getDescription() == null
        || getRepository().getDescription().getRepositoryName() == null) {
      return new ArrayList<>();
    }
    List<DfsPackDescription> packList = new ArrayList<>();
    ResultSet packListRst =
        session.execute(
            readPackList
                .bind()
                .setString("name", getRepository().getDescription().getRepositoryName()));
    if (!packListRst.isExhausted()) {
      Map<UUID, DfsPackDescription> packMap = new HashMap<>();
      List<Row> rows = packListRst.all();
      for (Row row : rows) {
        UUID id = row.getUUID("id");
        PackExt ext = PackExt.newPackExt(row.getString("ext"));
        long size = row.getLong("size");
        PackSource source = PackSource.valueOf(row.getString("source"));
        long lastModifiedTime = row.getLong("lmd");
        if (packMap.containsKey(id)) {
          packMap.get(id).setFileSize(ext, size);
        } else {
          DfsPackDescription cassandraPack =
              new CassandraPack(id, getRepository().getDescription());
          cassandraPack.addFileExt(ext);
          cassandraPack.setFileSize(ext, size);
          cassandraPack.setPackSource(source);
          cassandraPack.setLastModified(lastModifiedTime);
          packMap.put(id, cassandraPack);
        }
      }
      packList.addAll(packMap.values());
    }
    return packList;
  }

  @Override
  protected ReadableChannel openFile(DfsPackDescription dfsPackDescription, PackExt packExt)
      throws FileNotFoundException, IOException {

    return new CassandraPackReader((CassandraPack) dfsPackDescription, packExt);
  }

  private class CassandraPackReader implements ReadableChannel {

    private long size;
    private CassandraPack cassandraPack;
    private PackExt packExt;
    private long position;

    public CassandraPackReader(CassandraPack cassandraPack, PackExt packExt) {
      this.cassandraPack = cassandraPack;
      this.size = cassandraPack.getFileSize(packExt);
      this.packExt = packExt;
    }

    @Override
    public long position() throws IOException {
      return position;
    }

    @Override
    public void position(long l) throws IOException {
      // TODO check that position is set as a multiple of BlockSize
    }

    @Override
    public long size() throws IOException {
      return size;
    }

    @Override
    public int blockSize() {
      return getBlockSize();
    }

    @Override
    public void setReadAheadBytes(int i) throws IOException {
      // No read ahead bytes
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      long n = Math.min(dst.remaining(), size - position);
      if (n == 0) {
        return -1;
      }
      long offset = position + blockSize();

      ResultSet rst =
          session.execute(
              readPack
                  .bind()
                  .setUUID("id", cassandraPack.getId())
                  .setString("ext", packExt.toString())
                  .setLong("offset", offset));
      if (rst.isExhausted()) {
        return -1;
      }
      Row row = rst.one();
      byte[] value = row.getBytes("value").array();
      dst.put(value);
      position += value.length;
      return value.length;
    }

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public void close() throws IOException {}
  }

  @Override
  protected DfsOutputStream writeFile(
      final DfsPackDescription dfsPackDescription, final PackExt packExt) throws IOException {

    final CassandraPack cassandraPack = (CassandraPack) dfsPackDescription;
    return new Out() {
      @Override
      public void flush() {
        ByteArrayInputStream bis = new ByteArrayInputStream(getData());
        int size = Math.min(blockSize(), bis.available());
        byte[] value = new byte[size];
        long offset = 0;
        while (size > 0 && bis.read(value, 0, size) != -1) {
          offset = offset + blockSize;
          session.execute(
              insertPack
                  .bind()
                  .setUUID("id", cassandraPack.getId())
                  .setString("ext", packExt.toString())
                  .setLong("offset", offset)
                  .setBytes("value", ByteBuffer.wrap(value)));
          size = Math.min(blockSize(), bis.available());
          value = new byte[size];
        }
      }
    };
  }

  // hold in memory all writer object until a flush is called.
  // similar to jgit org.eclipse.jgit.internal.storage.dfs.InMemoryRepository.Out
  private abstract class Out extends DfsOutputStream {
    private final ByteArrayOutputStream dst = new ByteArrayOutputStream();

    private byte[] data;

    @Override
    public void write(byte[] buf, int off, int len) {
      data = null;
      dst.write(buf, off, len);
    }

    @Override
    public int read(long position, ByteBuffer buf) {
      byte[] d = getData();
      int n = Math.min(buf.remaining(), d.length - (int) position);
      if (n == 0) {
        return -1;
      }
      buf.put(d, (int) position, n);
      return n;
    }

    byte[] getData() {
      if (data == null) {
        data = dst.toByteArray();
      }
      return data;
    }

    @Override
    public abstract void flush();

    @Override
    public void close() {
      flush();
    }

    @Override
    public int blockSize() {
      return blockSize;
    }
  }

  @Override
  protected void rollbackPack(Collection<DfsPackDescription> collection) {
    // do nothing
  }
}
