/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.common.monitor;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.roda.core.data.common.RodaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchDir implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(WatchDir.class);
  private final boolean recursive;
  private final Path watched;
  private ReindexTransferredResourcesRunnable reindexRunnable;
  private boolean watchInitialized;
  private Thread threadReindex;
  private ExecutorService executor;
  private Date indexDate;
  private SolrClient index;
  private List<FolderObserver> observers;
  private Commiter commiter;
  private Thread threadCommit;
  private boolean running;

  public WatchDir(Path dir, boolean recursive, Date indexDate, SolrClient index, List<FolderObserver> observers)
    throws IOException {
    this.recursive = recursive;
    this.watched = dir;
    this.reindexRunnable = null;
    this.watchInitialized = false;
    this.indexDate = indexDate;
    this.index = index;
    this.observers = observers;
    this.executor = Executors.newSingleThreadExecutor();
    this.commiter = new Commiter();

  }

  public ReindexTransferredResourcesRunnable getReindexRunnable() {
    return reindexRunnable;
  }

  public void setReindexRunnable(ReindexTransferredResourcesRunnable reindexRunnable) {
    this.reindexRunnable = reindexRunnable;
  }

  @SuppressWarnings("unchegetInstancecked")
  <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }

  @Override
  public void run() {
    this.running = true;
    long startTime = System.currentTimeMillis();
    try {
      if (recursive) {
        MonitorVariables.getInstance().registerAll(watched);
      } else {
        MonitorVariables.getInstance().register(watched);
      }
    } catch (IOException e) {
      LOGGER.error("Error initialing watch thread: " + e.getMessage(), e);
    }
    LOGGER.debug("Time elapsed (initialize watch): " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");

    watchInitialized = true;
    if (index != null) {
      reindexRunnable = new ReindexTransferredResourcesRunnable(watched, indexDate, index);
      threadReindex = new Thread(reindexRunnable, "ReindexThread");
      threadReindex.start();
    }

    processEvents();
  }

  public void stop() {
    this.running = false;
    try {
      MonitorVariables.getInstance().getWatcher().close();
    } catch (IOException e) {
      LOGGER.error("Error stopping WatchDir", e);
    }
    if (threadReindex != null) {
      threadReindex.interrupt();
    }
    if (threadCommit != null) {
      threadCommit.interrupt();
    }
    executor.shutdownNow();
  }

  void processEvents() {
    this.threadCommit = new Thread(commiter, "Commiter");
    this.threadCommit.start();
    for (;;) {
      WatchKey key;
      try {
        key = MonitorVariables.getInstance().getWatcher().take();
      } catch (InterruptedException x) {
        return;
      }
      Path dir = MonitorVariables.getInstance().getKeys().get(key);
      if (dir == null) {
        continue;
      }
      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == OVERFLOW) {
          LOGGER.debug("OVERFLOW...");
          continue;
        }
        WatchEvent<Path> ev = cast(event);
        Path name = ev.context();
        Path child = dir.resolve(name);

        NotifierThread nt = new NotifierThread(observers, watched, child, kind, recursive);
        executor.execute(nt);

      }
      boolean valid = key.reset();
      if (!valid) {
        MonitorVariables.getInstance().getKeys().remove(key);
        if (MonitorVariables.getInstance().getKeys().isEmpty()) {
          break;
        }
      }
    }
  }

  public boolean isFullyInitialized() {
    return watchInitialized && threadReindex != null && !threadReindex.isAlive();
  }

  public void setObservers(List<FolderObserver> obs) {
    this.observers = obs;
  }

  class Commiter implements Runnable {
    @Override
    public void run() {
      while (running) {
        try {
          Thread.sleep(5000);
          index.commit(RodaConstants.INDEX_TRANSFERRED_RESOURCE);
        } catch (SolrException | SolrServerException | IOException e) {
          LOGGER.error("Error commiting: " + e.getMessage(), e);
        } catch (InterruptedException e) {
          // interrupted... probably stopping watchdir...
        }
      }

    }

  }
}