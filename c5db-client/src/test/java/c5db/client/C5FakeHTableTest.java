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


import c5db.client.generated.Call;
import c5db.client.generated.Cell;
import c5db.client.generated.CellType;
import c5db.client.generated.MutateResponse;
import c5db.client.generated.Response;
import c5db.client.generated.ScanResponse;
import c5db.client.scanner.ClientScannerManager;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.protostuff.ByteString;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;

public class C5FakeHTableTest {

  @Rule
  public JUnitRuleMockery context = new JUnitRuleMockery() {{
    setThreadingPolicy(new Synchroniser());
  }};

  private final MessageHandler messageHandler = context.mock(MessageHandler.class);
  private final ChannelPipeline channelPipeline = context.mock(ChannelPipeline.class);
  private final C5ConnectionManager c5ConnectionManager = context.mock(C5ConnectionManager.class);
  private final Channel channel = context.mock(Channel.class);
  private final byte[] row = Bytes.toBytes("row");
  private final byte[] cf = Bytes.toBytes("cf");
  private final byte[] cq = Bytes.toBytes("cq");
  private final byte[] value = Bytes.toBytes("value");
  private ExplicitNodeCaller singleNodeTableInterface;
  private SettableFuture<Response> callFuture;
  private FakeHTable hTable;

  @Before
  public void before() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    context.checking(new Expectations() {
      {
        oneOf(c5ConnectionManager).getOrCreateChannel(with(any(String.class)), with(any(int.class)));
        will(returnValue(channel));

        oneOf(channel).pipeline();
        will(returnValue(channelPipeline));

        oneOf(channelPipeline).get(with(any(Class.class)));
        will(returnValue(messageHandler));

      }
    });

    singleNodeTableInterface = new ExplicitNodeCaller("fake", 0, c5ConnectionManager);
    hTable = new FakeHTable(singleNodeTableInterface, ByteString.copyFromUtf8("Does Not Exist"));
    callFuture = SettableFuture.create();
  }

  @After
  public void after() throws InterruptedException, IOException {

    context.checking(new Expectations() {
      {
        oneOf(c5ConnectionManager).close();
      }
    });

    singleNodeTableInterface.close();
  }

  @Test(expected = IOException.class)
  public void putShouldErrorOnInvalidResponse() throws IOException, InterruptedException, ExecutionException, TimeoutException, MutationFailedException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    callFuture.set(new Response());
    hTable.put(new Put(row));

  }

  @Test(expected = IOException.class)
  public void putShouldThrowErrorIfMutationFailed()
      throws InterruptedException, ExecutionException, TimeoutException, MutationFailedException, IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, false), null, null);
    callFuture.set(response);
    hTable.put(new Put(row));
  }

  @Test
  public void putCanSucceed()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
    callFuture.set(response);
    hTable.put(new Put(row));
  }

  @Test(expected = TimeoutException.class)
  public void manyPutsBlocksIfNotEnoughRoom()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    hTable.setAutoFlush(false);

    try {
      long messagesToPut = hTable.getWriteBufferSize() + 1;
      for (int i = 0; i != messagesToPut; i++) {
        SettableFuture<Response> response = SettableFuture.create();
        context.checking(new Expectations() {
          {
            allowing(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
            will(returnValue(response));
          }
        });
        executorService.submit(() -> {
          hTable.put(new Put(row));
          return null;
        }).get(2, TimeUnit.SECONDS);
      }
      // We should never get here
      assertTrue(1 == 0);
    } finally {
      hTable.setAutoFlush(true);
    }
  }

  @Test(expected = TimeoutException.class)
  public void testManyPutsFailsWithoutResponseWithAutoFlush()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    long messagesToPut = 2;
    for (int i = 0; i != messagesToPut; i++) {
      SettableFuture<Response> response = SettableFuture.create();
      context.checking(new Expectations() {
        {
          allowing(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
          will(returnValue(response));
        }
      });
      executorService.submit(() -> {
        hTable.put(new Put(row));
        return null;
      }).get(2, TimeUnit.SECONDS);
    }
    // We should never get here
    assertTrue(1 == 0);
  }

  @Test(expected = TimeoutException.class)
  public void testFlushCommittedWillBlock()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    hTable.setAutoFlush(false);
    try {
      long messagesToPut = 100;
      hTable.setWriteBufferSize(messagesToPut);
      ArrayBlockingQueue<SettableFuture<Response>> futures = new ArrayBlockingQueue<>((int) messagesToPut);

      for (int i = 0; i != messagesToPut; i++) {
        SettableFuture<Response> response = SettableFuture.create();
        context.checking(new Expectations() {
          {
            allowing(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
            will(returnValue(response));
          }
        });
        hTable.put(new Put(row));
        futures.add(response);
      }
      Future<Object> flushFuture = executorService.submit(() -> {
        hTable.flushCommits();
        return null;
      });

      // Remove one entry from the top
      futures.remove();
      Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
      futures.parallelStream().forEach(responseSettableFuture -> responseSettableFuture.set(response));

      flushFuture.get(2, TimeUnit.SECONDS);
    } finally {
      hTable.setAutoFlush(true);
    }
  }

  @Test
  public void testFlushWillClear()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    hTable.setAutoFlush(false);
    try {
      long messagesToPut = 100;
      hTable.setWriteBufferSize(messagesToPut);
      ArrayBlockingQueue<SettableFuture<Response>> futures = new ArrayBlockingQueue<>((int) messagesToPut);
      for (int i = 0; i != messagesToPut; i++) {
        SettableFuture<Response> response = SettableFuture.create();
        context.checking(new Expectations() {
          {
            allowing(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
            will(returnValue(response));
          }
        });
        hTable.put(new Put(row));
        futures.add(response);
      }

      Future<Object> flushFuture = executorService.submit(() -> {
        hTable.flushCommits();
        return null;
      });

      Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
      futures.parallelStream().forEach(responseSettableFuture -> responseSettableFuture.set(response));
      flushFuture.get();

    } finally {
      hTable.setAutoFlush(true);
    }
  }


  @Test
  public void manyPutsDoNotBlockIfRoom()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {

    hTable.setAutoFlush(false);

    try {
      int messagesToPut = 999;
      ArrayBlockingQueue<SettableFuture<Response>> futures = new ArrayBlockingQueue<>(messagesToPut);
      hTable.setWriteBufferSize(messagesToPut);
      for (int i = 0; i != messagesToPut; i++) {
        SettableFuture<Response> response = SettableFuture.create();

        context.checking(new Expectations() {
          {
            oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
            will(returnValue(response));
          }
        });
        hTable.put(new Put(row));
        futures.add(response);
      }
      Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
      futures.parallelStream().forEach(responseSettableFuture -> responseSettableFuture.set(response));
    } finally {
      hTable.setAutoFlush(true);
    }
  }


  @Test
  public void putsCanSucceed()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
    callFuture.set(response);
    hTable.put(Arrays.asList(new Put(row)));
  }

  @Test
  public void deleteCanSucceed()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
    callFuture.set(response);
    hTable.delete(new Delete(row));
  }

  @Test
  public void deletesCanSucceed()
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
    callFuture.set(response);
    hTable.delete(Arrays.asList(new Delete(row)));
  }


  @Test(expected = IOException.class)
  public void getShouldErrorWithNullResponse() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    callFuture.set(new Response());
    hTable.get(new Get(row));
  }

  @Test
  public void canScan() throws IOException, InterruptedException, ExecutionException {
    SettableFuture<Long> callFuture = SettableFuture.create();
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).callScan(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });

    long scannerId = 10l;
    ClientScannerManager.INSTANCE.createAndGet(channel, scannerId, 1);
    callFuture.set(scannerId);
    ResultScanner scanner = hTable.getScanner(new Scan());


    List<Integer> cellsPerResult = Arrays.asList(1);

    Cell cell = new Cell(
        ByteBuffer.wrap(Bytes.toBytes("row")),
        ByteBuffer.wrap(Bytes.toBytes("cf")),
        ByteBuffer.wrap(Bytes.toBytes("cq")),
        0l,
        CellType.PUT,
        ByteBuffer.wrap(Bytes.toBytes("value")));
    List<Cell> kv = Arrays.asList(cell);
    List<c5db.client.generated.Result> scanResults = Arrays.asList(new c5db.client.generated.Result(kv, 1, true));
    ScanResponse scanResponse = new ScanResponse(cellsPerResult, scannerId, true, 0, scanResults);

    ClientScannerManager.INSTANCE.get(scannerId).get().add(scanResponse);

    kv = Arrays.asList(cell);
    scanResults = Arrays.asList(new c5db.client.generated.Result(kv, 1, true));
    scanResponse = new ScanResponse(cellsPerResult, scannerId, false, 0, scanResults);

    ClientScannerManager.INSTANCE.get(scannerId).get().add(scanResponse);
    scanResponse = new ScanResponse(Arrays.asList(0), scannerId, false, 0, new ArrayList<>());
    ClientScannerManager.INSTANCE.get(scannerId).get().add(scanResponse);
    Result result;
    int counter = 0;
    do {
      result = scanner.next();
      counter++;
    } while (result != null);

    assertThat(counter, is(3));
  }

  @Test
  public void canMutateRow() throws IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });

    RowMutations rm = new RowMutations(Bytes.toBytes("row"));
    callFuture.set(new Response());
    hTable.mutateRow(rm);
  }

  @Test
  public void canCheckAndPut() throws IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
    callFuture.set(response);
    hTable.checkAndPut(row, cf, cq, value, new Put(row));
  }

  @Test
  public void canCheckAndDelete() throws IOException {
    context.checking(new Expectations() {
      {
        oneOf(messageHandler).call(with(any(Call.class)), with(any((Channel.class))));
        will(returnValue(callFuture));
      }
    });
    Response response = new Response(Response.Command.MUTATE, 1l, null, new MutateResponse(null, true), null, null);
    callFuture.set(response);
    hTable.checkAndDelete(row, cf, cq, value, new Delete(row));
  }
}