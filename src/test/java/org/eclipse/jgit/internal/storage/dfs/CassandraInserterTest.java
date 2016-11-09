package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.datastax.driver.core.Cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Test copied from org.eclipse.jgit.internal.storage.dfs.DfsInserterTest
 */
public class CassandraInserterTest {
    
    CassandraRepository db;
    Cluster cluster;
    
    @Rule
    public CassandraCQLUnit cassandraCQLUnit = new CassandraCQLUnit(new ClassPathCQLDataSet("test.cqlsh", "git_store"));
    
    @Before
    public void setup() {
        cluster = cassandraCQLUnit.getCluster();
        //        Cluster cluster = Cluster.builder().addContactPoint("127.0.0.1").withClusterName("git_store").build();
        db = new CassandraRepository(cluster, new CassandraRepository.Builder().setRepositoryDescription(new DfsRepositoryDescription("test")),
                        new DfsBlockCacheConfig()
                                        .setBlockSize(1024));
    }
    
    @Test
    public void testInserterDiscardsPack() throws IOException {
        try (ObjectInserter ins = db.newObjectInserter()) {
            ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
            ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
            Assert.assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
        }
        assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
    }
    
    @Test
    public void testReadFromInserterSmallObjects() throws IOException {
        ObjectInserter ins = db.newObjectInserter();
        ObjectId id1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
        ObjectId id2 = ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
        assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
        
        ObjectReader reader = ins.newReader();
        assertSame(ins, reader.getCreatedFromInserter());
        assertEquals("foo", readString(reader.open(id1)));
        assertEquals("bar", readString(reader.open(id2)));
        assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
        ins.flush();
        assertEquals(1, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
    }
    
    @Test
    public void testReadFromInserterLargerObjects() throws IOException {
        db = new CassandraRepository(cluster,
                        new CassandraRepository.Builder().setRepositoryDescription(new DfsRepositoryDescription("test")),
                        new DfsBlockCacheConfig()
                                        .setBlockSize(512)
                                        .setBlockLimit(2048));
        db.getObjectDatabase().getReaderOptions().setStreamFileThreshold(512);
        
        DfsBlockCache.reconfigure(new DfsBlockCacheConfig()
                        .setBlockSize(512)
                        .setBlockLimit(2048));
        
        byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(8192);
        DfsInserter ins = (DfsInserter) db.newObjectInserter();
        ins.setCompressionLevel(Deflater.NO_COMPRESSION);
        ObjectId id1 = ins.insert(Constants.OBJ_BLOB, data);
        assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
        
        ObjectReader reader = ins.newReader();
        assertSame(ins, reader.getCreatedFromInserter());
        assertTrue(Arrays.equals(data, readStream(reader.open(id1))));
        assertEquals(0, ((CassandraObjDatabase) db.getObjectDatabase()).listPacks().size());
        ins.flush();
        
        List<DfsPackDescription> packs = ((CassandraObjDatabase) db.getObjectDatabase()).listPacks();
        assertEquals(1, packs.size());
        assertTrue(packs.get(0).getFileSize(PackExt.PACK) > 2048);
    }
    
    private static String readString(ObjectLoader loader) throws IOException {
        return RawParseUtils.decode(readStream(loader));
    }
    
    private static byte[] readStream(ObjectLoader loader) throws IOException {
        ByteBuffer bb = IO.readWholeStream(loader.openStream(), 64);
        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        return buf;
    }
}