package com.gerritforge.jgit.storage.cassandra;

import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.junit.Assert.assertEquals;

public class SerialisationTest {
    
    Gson gson = (new GsonBuilder()).registerTypeAdapter(CassandraPack.class, new CassandraPackTypeAdapter()).create();
    
    @Test
    public void testDeserialiseDfsPackDescription() {
        DfsPackDescription dfsPdActual = new CassandraPack("packName", new DfsRepositoryDescription("test"));
        dfsPdActual.setFileSize(PackExt.PACK, 1024);
        String dfsDescriptionString = gson.toJson(dfsPdActual);
        CassandraPack dfsPackDescription = gson.fromJson(dfsDescriptionString, CassandraPack.class);
        assertEquals("test", dfsPackDescription.getRepositoryDescription().getRepositoryName());
        assertEquals("packName.pack", dfsPackDescription.getFileName(PackExt.PACK));
        assertEquals(1024, dfsPackDescription.getFileSize(PackExt.PACK));
        
    }
}
