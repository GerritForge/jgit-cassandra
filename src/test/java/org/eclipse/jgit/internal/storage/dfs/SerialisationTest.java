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

import static org.junit.Assert.assertEquals;

import com.datastax.driver.core.utils.UUIDs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.UUID;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class SerialisationTest {

  Gson gson =
      (new GsonBuilder())
          .registerTypeAdapter(CassandraPack.class, new CassandraPackTypeAdapter())
          .create();

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
