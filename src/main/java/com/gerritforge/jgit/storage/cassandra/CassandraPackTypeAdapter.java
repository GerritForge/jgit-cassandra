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

package com.gerritforge.jgit.storage.cassandra;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.pack.PackExt;

public class CassandraPackTypeAdapter extends TypeAdapter<CassandraPack> {
  @Override
  public void write(JsonWriter out, CassandraPack cassandraPack) throws IOException {
    out.beginObject();
    out.name("repositoryName").value(cassandraPack.getRepositoryDescription().getRepositoryName());
    String nameExt = cassandraPack.getFileName(PackExt.PACK);
    int dot = nameExt.lastIndexOf('.');
    String name = nameExt.substring(0, dot);
    out.name("name").value(name);
    out.name("sizeMap").beginArray();
    for (PackExt packExt : PackExt.values()) {
      out.beginObject();
      out.name("packExt").value(packExt.getExtension());
      out.name("size").value(cassandraPack.getFileSize(packExt));
      out.endObject();
    }
    out.endArray();
    out.endObject();
  }

  @Override
  public CassandraPack read(JsonReader in) throws IOException {
    String repositoryName = "";
    String name = "";
    Map<PackExt, Long> sizeMap = new HashMap<>();
    in.beginObject();
    while (in.hasNext()) {
      switch (in.nextName()) {
        case "repositoryName":
          repositoryName = in.nextString();
          break;
        case "name":
          name = in.nextString();
          break;
        case "sizeMap":
          in.beginArray();
          while (in.hasNext()) {
            in.beginObject();
            String packExt = "";
            long size = 0l;
            while (in.hasNext()) {
              switch (in.nextName()) {
                case "packExt":
                  packExt = in.nextString();
                  break;
                case "size":
                  size = in.nextLong();
                  break;
              }
            }
            sizeMap.put(PackExt.newPackExt(packExt), size);
            in.endObject();
          }
          in.endArray();
          break;
      }
    }
    in.endObject();
    CassandraPack cassandraPack =
        new CassandraPack(name, new DfsRepositoryDescription(repositoryName));
    for (PackExt packExt : sizeMap.keySet()) {
      cassandraPack.setFileSize(packExt, sizeMap.get(packExt));
    }
    return cassandraPack;
  }
}
