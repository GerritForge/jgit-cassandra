package org.eclipse.jgit.internal.storage.dfs;

import java.util.UUID;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

import com.datastax.driver.core.utils.UUIDs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.junit.Assert.assertEquals;

public class SerialisationTest {
    
    Gson gson = (new GsonBuilder()).registerTypeAdapter(CassandraPack.class, new CassandraPackTypeAdapter()).create();
    
    @Test
    public void testDeserialiseDfsPackDescription() {
        UUID id = UUIDs.timeBased();
        CassandraPack cassandraPack = new CassandraPack(id, new DfsRepositoryDescription("test"));
        cassandraPack.getLastModified();
        cassandraPack.setFileSize(PackExt.PACK, 1024);
        String dfsDescriptionString = gson.toJson(cassandraPack);
        CassandraPack dfsPackDescription = gson.fromJson(dfsDescriptionString, CassandraPack.class);
        assertEquals("test", dfsPackDescription.getRepositoryDescription().getRepositoryName());
        assertEquals(id + ".pack", dfsPackDescription.getFileName(PackExt.PACK));
        assertEquals(1024, dfsPackDescription.getFileSize(PackExt.PACK));
        
    }
}
