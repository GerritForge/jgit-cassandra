package com.gerritforge.jgit.storage.cassandra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream;
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;
import org.eclipse.jgit.internal.storage.pack.PackExt;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.UUIDs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class CassandraObjDatabase extends DfsObjDatabase {
    
    final Session session;
    final int blockSize;
    final PreparedStatement readPack;
    final PreparedStatement insertPack;
    final PreparedStatement readPackList;
    final PreparedStatement updatePackList;
    
    final Gson gson = (new GsonBuilder()).registerTypeHierarchyAdapter(CassandraPack.class, new CassandraPackTypeAdapter()).create();
    
    protected CassandraObjDatabase(Session session, DfsRepository repository,
                    DfsReaderOptions options, int blockSize) {
        super(repository, options);
        this.session = session;
        this.blockSize = blockSize;
        readPack = session.prepare(
                        QueryBuilder
                                        .select("value")
                                        .from("git_store", "packs")
                                        .where(QueryBuilder.eq("id", QueryBuilder.bindMarker()))
                                        .and(QueryBuilder.eq("offset", QueryBuilder.bindMarker()))
                                        .toString());
        
        insertPack = session.prepare(
                        QueryBuilder
                                        .insertInto("git_store", "packs")
                                        .value("id", QueryBuilder.bindMarker())
                                        .value("offset", QueryBuilder.bindMarker())
                                        .value("value", QueryBuilder.bindMarker())
                                        .toString());
        
        readPackList = session.prepare(
                        QueryBuilder
                                        .select("packs")
                                        .from("git_store", "pack_list")
                                        .where(QueryBuilder.eq("id", QueryBuilder.bindMarker()))
                                        .toString());
        
        updatePackList = session.prepare(
                        QueryBuilder.insertInto("git_store", "pack_list")
                                        .value("id", QueryBuilder.bindMarker())
                                        .value("packs", QueryBuilder.bindMarker())
                                        .toString());
        
    }
    
    public int getBlockSize() {
        return blockSize;
    }
    
    @Override
    protected DfsPackDescription newPack(PackSource packSource) throws IOException {
        UUID id = UUIDs.timeBased();
        DfsPackDescription desc = new CassandraPack(id.toString(), getRepository().getDescription());
        return desc.setPackSource(packSource);
    }
    
    @Override
    protected void commitPackImpl(Collection<DfsPackDescription> desc, Collection<DfsPackDescription> replace) throws IOException {
        List<String> descJson = new ArrayList<>(desc.size());
        for (DfsPackDescription dfsPackDescription : desc) {
            descJson.add(gson.toJson(dfsPackDescription));
        }
        session.execute(updatePackList.bind(getRepository().getDescription().getRepositoryName(), descJson));
        
        if (replace != null) {
            //TODO replace pack descriptions
        }
    }
    
    @Override
    protected List<DfsPackDescription> listPacks() throws IOException {
        List<DfsPackDescription> packList = new ArrayList<>();
        ResultSet packListRst = session.execute(readPackList.bind(getRepository().getDescription().getRepositoryName()));
        if (!packListRst.isExhausted()) {
            Row row = packListRst.one();
            List<String> listJsonPacks = row.getList("packs", String.class);
            for (String packDescriptionJson : listJsonPacks) {
                DfsPackDescription dfsPackDescription = gson.fromJson(packDescriptionJson, CassandraPack.class);
                packList.add(dfsPackDescription);
            }
        }
        return packList;
    }
    
    @Override
    protected ReadableChannel openFile(DfsPackDescription dfsPackDescription, PackExt packExt) throws FileNotFoundException, IOException {
        
        return new CassandraPackReader(dfsPackDescription.getFileSize(packExt), dfsPackDescription, packExt);
    }
    
    private class CassandraPackReader implements ReadableChannel {
        
        private long size;
        private DfsPackDescription dfsPackDescription;
        private PackExt packExt;
        private long position;
        
        public CassandraPackReader(long size, DfsPackDescription dfsPackDescription, PackExt packExt) {
            this.size = size;
            this.dfsPackDescription = dfsPackDescription;
            this.packExt = packExt;
        }
        
        @Override
        public long position() throws IOException {
            return position;
        }
        
        @Override
        public void position(long l) throws IOException {
            //TODO check that position is set as a multiple of BlockSize
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
            //No read ahead bytes
        }
        
        @Override
        public int read(ByteBuffer dst) throws IOException {
            long n = Math.min(dst.remaining(), size - position);
            if (n == 0) {
                return -1;
            }
            long offset = position + blockSize();
            
            ResultSet rst = session.execute(readPack.bind(dfsPackDescription.getFileName(packExt), offset));
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
        public void close() throws IOException {
            
        }
    }
    
    @Override
    protected DfsOutputStream writeFile(final DfsPackDescription dfsPackDescription, final PackExt packExt) throws IOException {
        return new Out() {
            @Override
            public void flush() {
                String name = dfsPackDescription.getFileName(packExt);
                ByteArrayInputStream bis = new ByteArrayInputStream(getData());
                int size = Math.min(blockSize(), bis.available());
                byte[] value = new byte[size];
                long offset = 0;
                while (size > 0 &&  bis.read(value, 0, size) != -1) {
                    offset = offset + blockSize;
                    session.execute(insertPack.bind(name, offset, ByteBuffer.wrap(value)));
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
