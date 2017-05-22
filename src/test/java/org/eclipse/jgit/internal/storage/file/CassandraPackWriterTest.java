package org.eclipse.jgit.internal.storage.file;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.CassandraCluster;
import org.eclipse.jgit.internal.storage.dfs.CassandraRepository;
import org.eclipse.jgit.internal.storage.dfs.ClusterConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.dfs.DfsPackParser;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.PackParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.datastax.driver.core.Cluster;

import static org.eclipse.jgit.internal.storage.pack.PackWriter.NONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CassandraPackWriterTest {
    CassandraRepository db;
    Cluster cluster;
    
    ByteArrayOutputStream os;
    
    PackFile pack;
    
    ObjectInserter inserter;
    
    PackWriter writer;
    
    PackConfig config;
    
    
//    @Rule
//    public CassandraCQLUnit cassandraCQLUnit = new CassandraCQLUnit(new ClassPathCQLDataSet("test.cqlsh", "git_store"));
//
    @Before
    public void setup() throws IOException {
//        cluster = cassandraCQLUnit.getCluster();
        ClusterConfig clusterConfig = new ClusterConfig();
//        clusterConfig.setClusterName(cluster.getClusterName());
        clusterConfig.setClusterName("git_store");
        clusterConfig.setContactPoints(new String[] {"127.0.0.1"});
        clusterConfig.setPort(9042);
        CassandraCluster cassandraCluster = new CassandraCluster(clusterConfig);
        db = (new CassandraRepository.Builder(cassandraCluster, 1024)).setRepositoryDescription(new DfsRepositoryDescription("test")).build();
        config = new PackConfig(db);
        os = new ByteArrayOutputStream();
    }
    
    /**
     * Test constructor for exceptions, default settings, initialization.
     *
     * @throws IOException
     */
    @Test
    public void testContructor() throws IOException {
        writer = new PackWriter(config, db.newObjectReader());
        assertFalse(writer.isDeltaBaseAsOffset());
        assertTrue(config.isReuseDeltas());
        assertTrue(config.isReuseObjects());
        assertEquals(0, writer.getObjectCount());
    }
    
    /**
     * Change default settings and verify them.
     */
    @Test
    public void testModifySettings() {
        config.setReuseDeltas(false);
        config.setReuseObjects(false);
        config.setDeltaBaseAsOffset(false);
        assertFalse(config.isReuseDeltas());
        assertFalse(config.isReuseObjects());
        assertFalse(config.isDeltaBaseAsOffset());
        
        writer = new PackWriter(config, db.newObjectReader());
        writer.setDeltaBaseAsOffset(true);
        assertTrue(writer.isDeltaBaseAsOffset());
        assertFalse(config.isDeltaBaseAsOffset());
    }
    
    /**
     * Write empty pack by providing empty sets of interesting/uninteresting
     * objects and check for correct format.
     *
     * @throws IOException
     */
    @Test
    public void testWriteEmptyPack1() throws IOException {
        createVerifyOpenPack(NONE, NONE, false, false);
        
        assertEquals(0, writer.getObjectCount());
        assertEquals(0, writer.getObjectCount());
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", writer
            .computeName().name());
    }
    
    /**
     * Create pack basing on only interesting objects, then precisely verify
     * content. No delta reuse here.
     *
     * @throws IOException
     */
    @Test
    public void testWritePack1() throws IOException {
        config.setReuseDeltas(false);
        writeVerifyPack1();
    }
    
    private void writeVerifyPack1() throws IOException {
        final HashSet<ObjectId> interestings = new HashSet<ObjectId>();
        interestings.add(ObjectId
            .fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"));
        createVerifyOpenPack(interestings, NONE, false, false);
        
        final ObjectId expectedOrder[] = new ObjectId[] {
            ObjectId.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"),
            ObjectId.fromString("c59759f143fb1fe21c197981df75a7ee00290799"),
            ObjectId.fromString("540a36d136cf413e4b064c2b0e0a4db60f77feab"),
            ObjectId.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035"),
            ObjectId.fromString("902d5476fa249b7abc9d84c611577a81381f0327"),
            ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904"),
            ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259"),
            ObjectId.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3") };
        
        assertEquals(expectedOrder.length, writer.getObjectCount());
        verifyObjectsOrder(expectedOrder);
        assertEquals("34be9032ac282b11fa9babdc2b2a93ca996c9c2f", writer
            .computeName().name());
    }
    
    
    private void verifyObjectsOrder(final ObjectId objectsOrder[]) {
        final List<PackIndex.MutableEntry> entries = new ArrayList<PackIndex.MutableEntry>();
        
        for (PackIndex.MutableEntry me : pack) {
            entries.add(me.cloneEntry());
        }
        Collections.sort(entries, new Comparator<PackIndex.MutableEntry>() {
            public int compare(PackIndex.MutableEntry o1, PackIndex.MutableEntry o2) {
                return Long.signum(o1.getOffset() - o2.getOffset());
            }
        });
        
        int i = 0;
        for (PackIndex.MutableEntry me : entries) {
            assertEquals(objectsOrder[i++].toObjectId(), me.toObjectId());
        }
    }
    
    private void createVerifyOpenPack(final Set<ObjectId> interestings,
        final Set<ObjectId> uninterestings, final boolean thin,
        final boolean ignoreMissingUninteresting)
        throws MissingObjectException, IOException {
        createVerifyOpenPack(interestings, uninterestings, thin,
            ignoreMissingUninteresting, false);
    }
    
    private void createVerifyOpenPack(final Set<ObjectId> interestings,
        final Set<ObjectId> uninterestings, final boolean thin,
        final boolean ignoreMissingUninteresting, boolean useBitmaps)
        throws MissingObjectException, IOException {
        NullProgressMonitor m = NullProgressMonitor.INSTANCE;
        writer = new PackWriter(config, db.newObjectReader());
        writer.setUseBitmaps(useBitmaps);
        writer.setThin(thin);
        writer.setIgnoreMissingUninteresting(ignoreMissingUninteresting);
        writer.preparePack(m, interestings, uninterestings);
        writer.writePack(m, m, os);
        writer.close();
        verifyOpenPack(thin);
    }
    
    private void verifyOpenPack(final boolean thin) throws IOException {
        final byte[] packData = os.toByteArray();
        
        if (thin) {
            PackParser p = index(packData);
            try {
                p.parse(NullProgressMonitor.INSTANCE);
                fail("indexer should grumble about missing object");
            } catch (IOException x) {
                // expected
            }
        }
    
        DfsPackParser p = (DfsPackParser) index(packData);
//        p.setKeepEmpty(true);
        p.setAllowThin(thin);
//        p.setIndexVersion(2);
        p.parse(NullProgressMonitor.INSTANCE);
//        long packSize = p.getPackSize();
//        DfsPackDescription dfsPackDescription = p.getPackDescription();
//        assertNotNull("have PackFile after parsing", packSize);
        
    }
    
    private PackParser index(final byte[] packData) throws IOException {
        if (inserter == null)
            inserter = db.newObjectInserter();
        return inserter.newPackParser(new ByteArrayInputStream(packData));
    }
}
