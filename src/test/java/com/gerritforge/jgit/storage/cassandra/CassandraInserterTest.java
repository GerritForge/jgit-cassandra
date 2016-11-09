package com.gerritforge.jgit.storage.cassandra;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.datastax.driver.core.Cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Test copied from org.eclipse.jgit.internal.storage.dfs.DfsInserterTest
 */
public class CassandraInserterTest {
    
    CassandraRepository db;
    
    @Rule
    public CassandraCQLUnit cassandraCQLUnit = new CassandraCQLUnit(new ClassPathCQLDataSet("test.cqlsh", "git_store"));
    
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
    
    @Test
    public void testReadFromInserterSmallObjects() throws IOException {
        ObjectInserter ins = db.newObjectInserter();
        ObjectId id1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
        ObjectId id2 = ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
        assertEquals(0, ((CassandraObjDatabase)db.getObjectDatabase()).listPacks().size());
        
        ObjectReader reader = ins.newReader();
        assertSame(ins, reader.getCreatedFromInserter());
        assertEquals("foo", readString(reader.open(id1)));
        assertEquals("bar", readString(reader.open(id2)));
        assertEquals(0, ((CassandraObjDatabase)db.getObjectDatabase()).listPacks().size());
        ins.flush();
        assertEquals(1, ((CassandraObjDatabase)db.getObjectDatabase()).listPacks().size());
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