package com.gerritforge.jgit.storage.cassandra;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

public class CassandraResolver<C> implements RepositoryResolver<C> {
  @Override
  public Repository open(C c, String s)
      throws RepositoryNotFoundException,
          ServiceNotAuthorizedException,
          ServiceNotEnabledException,
          ServiceMayNotContinueException {
    return null;
  }
}
