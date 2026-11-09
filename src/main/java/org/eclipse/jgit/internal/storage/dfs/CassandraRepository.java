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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RefList;

public class CassandraRepository extends DfsRepository {

  public static class Builder extends DfsRepositoryBuilder<Builder, CassandraRepository> {

    private final CassandraCluster cassandraCluster;
    private final int blockSize;

    public Builder(CassandraCluster cassandraCluster, int blockSize) {
      this.cassandraCluster = cassandraCluster;
      this.blockSize = blockSize;
    }

    @Override
    public CassandraRepository build() throws IOException {
      return new CassandraRepository(this, blockSize);
    }
  }

  private final CassandraObjDatabase cassandraObjDatabase;
  private final RefDatabase refdb;
  private boolean performsAtomicTransactions = true;

  protected CassandraRepository(Builder builder, int blockSize) {
    super(builder);
    this.cassandraObjDatabase =
        new CassandraObjDatabase(
            builder.cassandraCluster.getCluster().newSession(),
            this,
            new DfsReaderOptions(),
            blockSize);
    this.refdb = new MemRefDatabase();
  }

  @Override
  public DfsObjDatabase getObjectDatabase() {
    return cassandraObjDatabase;
  }

  @Override
  public RefDatabase getRefDatabase() {
    return refdb;
  }

  public void setGitwebDescription(@Nullable String description) throws IOException {
    return;
  }

  /**
   * Enable (or disable) the atomic reference transaction support.
   *
   * <p>Useful for testing atomic support enabled or disabled.
   *
   * @param atomic
   */
  public void setPerformsAtomicTransactions(boolean atomic) {
    performsAtomicTransactions = atomic;
  }

  /**
   * Copy from org.eclipse.jgit.internal.storage.dfs - hackathon gerrit 2016 A ref database storing
   * all refs in-memory.
   *
   * <p>This class is protected (and not private) to facilitate testing using subclasses of
   * InMemoryRepository.
   */
  protected class MemRefDatabase extends DfsRefDatabase {
    private final ConcurrentMap<String, Ref> refs = new ConcurrentHashMap<String, Ref>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true /* fair */);

    /** Initialize a new in-memory ref database. */
    protected MemRefDatabase() {
      super(CassandraRepository.this);
    }

    @Override
    public boolean performsAtomicTransactions() {
      return performsAtomicTransactions;
    }

    @Override
    public BatchRefUpdate newBatchUpdate() {
      return new BatchRefUpdate(this) {
        @Override
        public void execute(RevWalk walk, ProgressMonitor monitor) throws IOException {
          if (performsAtomicTransactions() && isAtomic()) {
            try {
              lock.writeLock().lock();
              batch(getCommands());
            } finally {
              lock.writeLock().unlock();
            }
          } else {
            super.execute(walk, monitor);
          }
        }
      };
    }

    @Override
    protected RefCache scanAllRefs() throws IOException {
      RefList.Builder<Ref> ids = new RefList.Builder<Ref>();
      RefList.Builder<Ref> sym = new RefList.Builder<Ref>();
      try {
        lock.readLock().lock();
        for (Ref ref : refs.values()) {
          if (ref.isSymbolic()) {
            sym.add(ref);
          }
          ids.add(ref);
        }
      } finally {
        lock.readLock().unlock();
      }
      ids.sort();
      sym.sort();
      cassandraObjDatabase.getCurrentPackList().markDirty();
      return new RefCache(ids.toRefList(), sym.toRefList());
    }

    private void batch(List<ReceiveCommand> cmds) {
      // Validate that the target exists in a new RevWalk, as the RevWalk
      // from the RefUpdate might be reading back unflushed objects.
      Map<ObjectId, ObjectId> peeled = new HashMap<>();
      try (RevWalk rw = new RevWalk(getRepository())) {
        for (ReceiveCommand c : cmds) {
          if (c.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED) {
            ReceiveCommand.abort(cmds);
            return;
          }

          if (!ObjectId.zeroId().equals(c.getNewId())) {
            try {
              RevObject o = rw.parseAny(c.getNewId());
              if (o instanceof RevTag) {
                peeled.put(o, rw.peel(o).copy());
              }
            } catch (IOException e) {
              c.setResult(ReceiveCommand.Result.REJECTED_MISSING_OBJECT);
              ReceiveCommand.abort(cmds);
              return;
            }
          }
        }
      }

      // Check all references conform to expected old value.
      for (ReceiveCommand c : cmds) {
        Ref r = refs.get(c.getRefName());
        if (r == null) {
          if (c.getType() != ReceiveCommand.Type.CREATE) {
            c.setResult(ReceiveCommand.Result.LOCK_FAILURE);
            ReceiveCommand.abort(cmds);
            return;
          }
        } else {
          ObjectId objectId = r.getObjectId();
          if (r.isSymbolic() || objectId == null || !objectId.equals(c.getOldId())) {
            c.setResult(ReceiveCommand.Result.LOCK_FAILURE);
            ReceiveCommand.abort(cmds);
            return;
          }
        }
      }

      // Write references.
      for (ReceiveCommand c : cmds) {
        if (c.getType() == ReceiveCommand.Type.DELETE) {
          refs.remove(c.getRefName());
          c.setResult(ReceiveCommand.Result.OK);
          continue;
        }

        ObjectId p = peeled.get(c.getNewId());
        Ref r;
        if (p != null) {
          r = new ObjectIdRef.PeeledTag(Ref.Storage.PACKED, c.getRefName(), c.getNewId(), p);
        } else {
          r = new ObjectIdRef.PeeledNonTag(Ref.Storage.PACKED, c.getRefName(), c.getNewId());
        }
        refs.put(r.getName(), r);
        c.setResult(ReceiveCommand.Result.OK);
      }
      clearCache();
    }

    @Override
    protected boolean compareAndPut(Ref oldRef, Ref newRef) throws IOException {
      try {
        lock.writeLock().lock();
        ObjectId id = newRef.getObjectId();
        if (id != null) {
          try (RevWalk rw = new RevWalk(getRepository())) {
            // Validate that the target exists in a new RevWalk, as the RevWalk
            // from the RefUpdate might be reading back unflushed objects.
            rw.parseAny(id);
          }
        }
        String name = newRef.getName();
        if (oldRef == null) {
          return refs.putIfAbsent(name, newRef) == null;
        }

        Ref cur = refs.get(name);
        if (cur != null) {
          if (eq(cur, oldRef)) {
            return refs.replace(name, cur, newRef);
          }
        }

        if (oldRef.getStorage() == Ref.Storage.NEW) {
          return refs.putIfAbsent(name, newRef) == null;
        }

        return false;
      } finally {
        lock.writeLock().unlock();
      }
    }

    @Override
    protected boolean compareAndRemove(Ref oldRef) throws IOException {
      try {
        lock.writeLock().lock();
        String name = oldRef.getName();
        Ref cur = refs.get(name);
        if (cur != null && eq(cur, oldRef)) {
          return refs.remove(name, cur);
        } else {
          return false;
        }
      } finally {
        lock.writeLock().unlock();
      }
    }

    private boolean eq(Ref a, Ref b) {
      if (!Objects.equals(a.getName(), b.getName())) {
        return false;
      }
      if (a.isSymbolic() != b.isSymbolic()) {
        return false;
      }
      if (a.isSymbolic()) {
        return Objects.equals(a.getTarget().getName(), b.getTarget().getName());
      } else {
        return Objects.equals(a.getObjectId(), b.getObjectId());
      }
    }
  }
}
