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

package org.eclipse.jgit.internal.storage.dfs.pgm;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.eclipse.jgit.internal.ketch.KetchLeader;
import org.eclipse.jgit.internal.ketch.KetchLeaderCache;
import org.eclipse.jgit.internal.ketch.KetchPreReceive;
import org.eclipse.jgit.internal.ketch.KetchSystem;
import org.eclipse.jgit.internal.ketch.KetchText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.DaemonClient;
import org.eclipse.jgit.transport.DaemonService;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_exportRepositoriesOverGit")
class CassandraDaemon extends TextBuiltin {
  @Option(name = "--config-file", metaVar = "metaVar_configFile", usage = "usage_configFile")
  File configFile;

  @Option(name = "--port", metaVar = "metaVar_port", usage = "usage_portNumberToListenOn")
  int port = org.eclipse.jgit.transport.Daemon.DEFAULT_PORT;

  @Option(name = "--listen", metaVar = "metaVar_hostName", usage = "usage_hostnameOrIpToListenOn")
  String host;

  @Option(
      name = "--timeout",
      metaVar = "metaVar_seconds",
      usage = "usage_abortConnectionIfNoActivity")
  int timeout = -1;

  @Option(
      name = "--enable",
      metaVar = "metaVar_service",
      usage = "usage_enableTheServiceInAllRepositories",
      multiValued = true)
  final List<String> enable = new ArrayList<String>();

  @Option(
      name = "--disable",
      metaVar = "metaVar_service",
      usage = "usage_disableTheServiceInAllRepositories",
      multiValued = true)
  final List<String> disable = new ArrayList<String>();

  @Option(
      name = "--allow-override",
      metaVar = "metaVar_service",
      usage = "usage_configureTheServiceInDaemonServicename",
      multiValued = true)
  final List<String> canOverride = new ArrayList<String>();

  @Option(
      name = "--forbid-override",
      metaVar = "metaVar_service",
      usage = "usage_configureTheServiceInDaemonServicename",
      multiValued = true)
  final List<String> forbidOverride = new ArrayList<String>();

  @Option(name = "--export-all", usage = "usage_exportWithoutGitDaemonExportOk")
  boolean exportAll;

  @Option(name = "--ketch")
  CassandraDaemon.KetchServerType ketchServerType;

  enum KetchServerType {
    LEADER;
  }

  @Argument(required = true, metaVar = "metaVar_directory", usage = "usage_directoriesToExport")
  final List<File> directory = new ArrayList<File>();

  @Override
  protected boolean requiresRepository() {
    return false;
  }

  @Override
  protected void run() throws Exception {
    PackConfig packConfig = new PackConfig();

    if (configFile != null) {
      if (!configFile.exists()) {
        throw die(
            MessageFormat.format(
                CLIText.get().configFileNotFound, //
                configFile.getAbsolutePath()));
      }

      FileBasedConfig cfg = new FileBasedConfig(configFile, FS.DETECTED);
      cfg.load();
      new WindowCacheConfig().fromConfig(cfg).install();
      packConfig.fromConfig(cfg);
    }

    int threads = packConfig.getThreads();
    if (threads <= 0) {
      threads = Runtime.getRuntime().availableProcessors();
    }
    if (1 < threads) {
      packConfig.setExecutor(Executors.newFixedThreadPool(threads));
    }

    final FileResolver<DaemonClient> resolver = new FileResolver<DaemonClient>();
    for (final File f : directory) {
      outw.println(MessageFormat.format(CLIText.get().exporting, f.getAbsolutePath()));
      resolver.exportDirectory(f);
    }
    resolver.setExportAll(exportAll);

    final org.eclipse.jgit.transport.Daemon d;
    d =
        new org.eclipse.jgit.transport.Daemon(
            host != null ? new InetSocketAddress(host, port) : new InetSocketAddress(port));
    d.setPackConfig(packConfig);
    d.setRepositoryResolver(resolver);
    if (0 <= timeout) {
      d.setTimeout(timeout);
    }

    for (final String n : enable) {
      service(d, n).setEnabled(true);
    }
    for (final String n : disable) {
      service(d, n).setEnabled(false);
    }

    for (final String n : canOverride) {
      service(d, n).setOverridable(true);
    }
    for (final String n : forbidOverride) {
      service(d, n).setOverridable(false);
    }
    if (ketchServerType == CassandraDaemon.KetchServerType.LEADER) {
      startKetchLeader(d);
    }
    d.start();
    outw.println(MessageFormat.format(CLIText.get().listeningOn, d.getAddress()));
  }

  private static DaemonService service(final org.eclipse.jgit.transport.Daemon d, final String n) {
    final DaemonService svc = d.getService(n);
    if (svc == null) {
      throw die(MessageFormat.format(CLIText.get().serviceNotSupported, n));
    }
    return svc;
  }

  private void startKetchLeader(org.eclipse.jgit.transport.Daemon daemon) {
    KetchSystem system = new KetchSystem();
    final KetchLeaderCache leaders = new KetchLeaderCache(system);
    final ReceivePackFactory<DaemonClient> factory;

    factory = daemon.getReceivePackFactory();
    daemon.setReceivePackFactory(
        new ReceivePackFactory<DaemonClient>() {
          @Override
          public ReceivePack create(DaemonClient req, Repository repo)
              throws ServiceNotEnabledException, ServiceNotAuthorizedException {
            ReceivePack rp = factory.create(req, repo);
            KetchLeader leader;
            try {
              leader = leaders.get(repo);
            } catch (URISyntaxException err) {
              throw new ServiceNotEnabledException(KetchText.get().invalidFollowerUri, err);
            }
            rp.setPreReceiveHook(new KetchPreReceive(leader));
            return rp;
          }
        });
  }
}
