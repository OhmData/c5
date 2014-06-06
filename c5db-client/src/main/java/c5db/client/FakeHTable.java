/*
 * Copyright (C) 2014  Ohm Data
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package c5db.client;

import c5db.client.generated.ByteArrayComparable;
import c5db.client.generated.Comparator;
import c5db.client.generated.CompareType;
import c5db.client.generated.Condition;
import c5db.client.generated.GetRequest;
import c5db.client.generated.MultiRequest;
import c5db.client.generated.MutateRequest;
import c5db.client.generated.MutationProto;
import c5db.client.generated.RegionAction;
import c5db.client.generated.RegionSpecifier;
import c5db.client.generated.Response;
import c5db.client.generated.ScanRequest;
import c5db.client.scanner.ClientScanner;
import c5db.client.scanner.ClientScannerManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.protostuff.ByteString;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;


/**
 * The main client entry point for putting data into C5. Equivalent to HTablet from HBase.
 */
public class FakeHTable implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(FakeHTable.class);
  private long bufferSize = 100;
  private final ClientScannerManager clientScannerManager = ClientScannerManager.INSTANCE;
  private byte[] regionName;
  private RegionSpecifier regionSpecifier;
  private TableInterface c5AsyncDatabase;
  private byte[] tableName;
  private final AtomicLong outstandingMutations = new AtomicLong(0);
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final List<Throwable> throwablesToThrow = new ArrayList<>();
  private boolean autoFlush = true;
  private boolean clearBufferOnFail = false;

  /**
   * A mock HTable Client
   *
   * @param tableName The name of the table to connect to.
   */
  public FakeHTable(String hostname, int port, ByteString tableName)
      throws InterruptedException, TimeoutException, ExecutionException {
    c5AsyncDatabase = new SingleNodeTableInterface(hostname, port);

    this.tableName = tableName.toByteArray();
    regionName = tableName.toByteArray();
    regionSpecifier = new RegionSpecifier(RegionSpecifier.RegionSpecifierType.REGION_NAME, ByteBuffer.wrap(regionName));
  }

  FakeHTable(TableInterface c5AsyncDatabase, ByteString tableName) {
    this.c5AsyncDatabase = c5AsyncDatabase;
    this.tableName = tableName.toByteArray();
    regionName = tableName.toByteArray();
    regionSpecifier = new RegionSpecifier(RegionSpecifier.RegionSpecifierType.REGION_NAME, ByteBuffer.wrap(regionName));
  }

  public static Comparator toComparator(ByteArrayComparable comparator) {
    return new Comparator(comparator.getClass().getName(), comparator.getValue());
  }

  public Result get(final Get get) throws IOException {
    GetRequest getRequest = RequestConverter.buildGetRequest(regionName, get, false);
    try {
      return ProtobufUtil.toResult(c5AsyncDatabase.get(getRequest).get().getGet().getResult());
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public Result[] get(List<Get> gets) throws IOException {
    //TODO Batch get
    final List<Result> results = new ArrayList<>();
    for (Get get : gets) {
      results.add(this.get(get));
    }
    return results.toArray(new Result[results.size()]);
  }

  public boolean exists(final Get get) throws IOException {
    GetRequest getRequest = RequestConverter.buildGetRequest(regionName, get, true);
    try {
      return c5AsyncDatabase.get(getRequest).get().getGet().getResult().getExists();
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
  }

  public ResultScanner getScanner(final Scan scan) throws IOException {
    if (scan.getStartRow() != null && scan.getStartRow().length > 0
        && scan.getStopRow() != null && scan.getStopRow().length > 0
        && Bytes.compareTo(scan.getStartRow(), scan.getStopRow()) > 0) {
      throw new IOException("StopRow needs to be greater than StartRow");
    }

    final ScanRequest scanRequest = new ScanRequest(regionSpecifier,
        ProtobufUtil.toScan(scan),
        0L,
        C5Constants.DEFAULT_INIT_SCAN,
        false,
        0L);
    ListenableFuture<ClientScanner> scanner;
    try {
      ListenableFuture<Long> scanResultFuture = c5AsyncDatabase.scan(scanRequest);
      long scanResult = scanResultFuture.get(C5Constants.CREATE_SCANNER_TIMEOUT, TimeUnit.MILLISECONDS);
      scanner = clientScannerManager.get(scanResult);
    } catch (ExecutionException | TimeoutException | InterruptedException e) {
      throw new IOException(e);
    }

    if (scanner == null) {
      throw new IOException("The scanner disappeared from the clientScannerManager");
    }

    try {
      return scanner.get(C5Constants.CREATE_SCANNER_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IOException("The scanner disappeared from the clientScannerManager");
    }
  }

  public ResultScanner getScanner(byte[] family) throws IOException {
    final Scan scan = new Scan();
    scan.addFamily(family);
    return getScanner(scan);
  }

  public ResultScanner getScanner(byte[] family, byte[] qualifier) throws IOException {
    final Scan scan = new Scan();
    scan.addColumn(family, qualifier);
    return getScanner(scan);
  }

  public Boolean[] exists(List<Get> gets) throws IOException {
    //TODO Batch get
    final List<Boolean> results = new ArrayList<>();
    for (Get get : gets) {
      results.add(this.exists(get));
    }
    return results.toArray(new Boolean[results.size()]);
  }

  public void flushCommits() throws IOException {
    waitForRunningPutsToComplete();
    checkBufferedThrowables();
    if (!this.autoFlush) {
      this.autoFlush = true;
      while (this.outstandingMutations.get() > 0) {
        waitForRunningPutsToComplete();
      }
      this.autoFlush = false;
    }
  }

  private void waitForRunningPutsToComplete() throws IOException {
    try {
      executor.awaitTermination(C5Constants.TIME_TO_WAIT_FOR_MUTATIONS_TO_CLEAR, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  private void checkBufferedThrowables() {
    if (throwablesToThrow.size() > 0) {
      IOException exception = new IOException("We built up some exceptions while buffering writes");
      throwablesToThrow.forEach(exception::addSuppressed);
      throwablesToThrow.clear();
      clearBufferIfSet();
    }
  }

  public void setAutoFlush(boolean autoFlush) {
    this.clearBufferOnFail = true;
    this.autoFlush = autoFlush;
  }

  public boolean isAutoFlush() {
    return this.autoFlush;
  }

  public void setAutoFlush(boolean autoFlush, boolean clearBufferOnFail) {
    setAutoFlush(autoFlush);
    this.clearBufferOnFail = clearBufferOnFail;
  }

  public void setAutoFlushTo(boolean autoFlush) {
    this.autoFlush = autoFlush;
  }

  public void put(Put put) throws IOException {
    if (this.autoFlush) {
      syncPut(put);
    } else {
      bufferPut(put);
    }
  }

  private void syncPut(Put put) throws IOException {
    MutateRequest mutateRequest = RequestConverter.buildMutateRequest(regionName, MutationProto.MutationType.PUT, put);
    ListenableFuture<Response> mutationFuture = c5AsyncDatabase.mutate(mutateRequest);
    try {
      Response result = mutationFuture.get();
      if (result == null || result.getMutate() == null || !result.getMutate().getProcessed()) {
        throw new IOException("Mutation not processed");
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
  }

  private void bufferPut(Put put) throws IOException {
    MutateRequest mutateRequest = RequestConverter.buildMutateRequest(regionName, MutationProto.MutationType.PUT, put);
    while (outstandingMutations.get() > bufferSize) {
      waitForRunningPutsToComplete();
      checkBufferedThrowables();
    }
    ListenableFuture<Response> mutationFuture = c5AsyncDatabase.mutate(mutateRequest);
    outstandingMutations.incrementAndGet();
    Futures.addCallback(mutationFuture, new FutureCallback<Response>() {
      @Override
      public void onSuccess(@NotNull Response result) {
        outstandingMutations.decrementAndGet();
        if (!result.getMutate().getProcessed()) {
          executor.shutdownNow();
          throwablesToThrow.add(new IOException("Mutation not processed:" + result));
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        throwablesToThrow.add(t);
        clearBufferIfSet();
      }

    }, executor);
  }
  
  private void clearBufferIfSet() {
    if (clearBufferOnFail){
      LOG.error("Had a failure clearing buffer");
      executor.shutdownNow();
      outstandingMutations.set(0);
    }
  }

  public void put(List<Put> puts) throws IOException {
    for (Put put : puts) {
      this.put(put);
    }
  }

  public void delete(Delete delete) throws IOException {
    MutateRequest mutateRequest = RequestConverter.buildMutateRequest(regionName,
        MutationProto.MutationType.DELETE,
        delete);
    try {
      if (!c5AsyncDatabase.mutate(mutateRequest).get().getMutate().getProcessed()) {
        throw new IOException("Not processed");
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
  }

  public void delete(List<Delete> deletes) throws IOException {
    for (Delete delete : deletes) {
      this.delete(delete);
    }
  }

  public void mutateRow(RowMutations rm) throws IOException {
    RegionAction regionAction = RequestConverter.buildRegionAction(regionName, rm);
    List<RegionAction> regionActions = Arrays.asList(regionAction);
    try {
      c5AsyncDatabase.multiRequest(new MultiRequest(regionActions)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
  }

  public boolean checkAndPut(byte[] row, byte[] family, byte[] qualifier, byte[] value, Put put) throws IOException {

    Condition condition = new Condition(ByteBuffer.wrap(row),
        ByteBuffer.wrap(family),
        ByteBuffer.wrap(qualifier),
        CompareType.EQUAL,
        toComparator(new ByteArrayComparable(ByteBuffer.wrap(value))));
    MutateRequest mutateRequest = RequestConverter.buildMutateRequest(regionName,
        MutationProto.MutationType.PUT,
        put,
        condition);
    try {
      if (!c5AsyncDatabase.mutate(mutateRequest).get().getMutate().getProcessed()) {
        return false;
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
    return true;
  }

  public boolean checkAndDelete(byte[] row, byte[] family, byte[] qualifier, byte[] value, Delete delete)
      throws IOException {
    Condition condition = new Condition(ByteBuffer.wrap(row),
        ByteBuffer.wrap(family),
        ByteBuffer.wrap(qualifier),
        CompareType.EQUAL,
        toComparator(new ByteArrayComparable(ByteBuffer.wrap(value))));
    MutateRequest mutateRequest = RequestConverter.buildMutateRequest(regionName,
        MutationProto.MutationType.DELETE,
        delete,
        condition);
    try {
      if (!c5AsyncDatabase.mutate(mutateRequest).get().getMutate().getProcessed()) {
        return false;
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
    return true;
  }


  @Override
  public void close() throws IOException {
    flushCommits();
    if (throwablesToThrow.size() > 0) {
      IOException exception = new IOException();
      this.throwablesToThrow.stream().forEach(exception::addSuppressed);
      throw exception;
    }

    c5AsyncDatabase.close();
  }


  public long getWriteBufferSize() {
    return bufferSize;
  }

  public void setWriteBufferSize(long writeBufferSize) throws IOException {
    flushCommits();
    this.bufferSize = writeBufferSize;
  }


  public byte[] getTableName() {
    return this.tableName;
  }

}
