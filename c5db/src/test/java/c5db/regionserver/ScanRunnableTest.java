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
package c5db.regionserver;

import c5db.AsyncChannelAsserts;
import c5db.client.generated.Call;
import c5db.client.generated.RegionSpecifier;
import c5db.client.generated.Response;
import c5db.client.generated.Scan;
import c5db.client.generated.ScanRequest;
import c5db.regionserver.scan.ScanRunnable;
import c5db.tablet.Region;
import io.netty.channel.ChannelHandlerContext;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.util.Bytes;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;
import org.jmock.Expectations;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static c5db.AsyncChannelAsserts.assertEventually;
import static c5db.AsyncChannelAsserts.listenTo;
import static c5db.regionserver.AddElementsActionReturnTrue.addElements;


public class ScanRunnableTest {
  private final RegionScanner regionScanner;
  @Rule
  public JUnitRuleMockery context = new JUnitRuleMockery() {{
    setThreadingPolicy(new Synchroniser());
  }};
  private final ChannelHandlerContext ctx = context.mock(ChannelHandlerContext.class);
  private final Region region = context.mock(Region.class);
  private ScanRunnable scanRunnable;
  Matcher matcher = new TypeSafeDiagnosingMatcher() {
    @Override
    protected boolean matchesSafely(Object item, Description mismatchDescription) {
      return true;
    }

    @Override
    public void describeTo(Description description) {

    }
  };

  public ScanRunnableTest() {
    regionScanner = context.mock(RegionScanner.class);
  }

  @Before
  public void before() throws IOException {
    RegionSpecifier regionSpecifier = new RegionSpecifier();

    Scan scan = new Scan();
    long scannerId = 1000;
    int numberOfRows = 100;
    ScanRequest scanRequest = new ScanRequest(regionSpecifier, scan, scannerId, numberOfRows, false, 0);
    long commandId = 1000;
    Call call = new Call(Call.Command.SCAN, commandId, null, null, scanRequest, null);
    context.checking(new Expectations() {
      {
        oneOf(region).getScanner(with(any(Scan.class)));
        will(returnValue(regionScanner));

      }
    });

    scanRunnable = new ScanRunnable(ctx, call, scannerId, region);

  }

  @Test
  public void scannerCanDeliverASingleMessageOnlyOnce() throws InterruptedException, IOException {
    byte[] row = Bytes.toBytes("row");
    byte[] cf = Bytes.toBytes("cf");
    byte[] cq = Bytes.toBytes("cq");
    byte[] value = Bytes.toBytes("value");
    KeyValue keyValue = new KeyValue(row, cf, cq, value);

    context.checking(new Expectations() {
      {
        oneOf(regionScanner).nextRaw(with(any(List.class)), with(any(Integer.class)));
        will(addElements(keyValue));
        oneOf(ctx).writeAndFlush(with(any(Response.class)));
      }
    });
    scanRunnable.onMessage(1);
  }


  @Test
  public void scannerCanDeliverWithMultipleOnMessageInvocation() throws InterruptedException, IOException {
    ArrayList<KeyValue> kvs = new ArrayList<>();
    for (int i = 0; i != 10000; i++) {
      byte[] row = Bytes.toBytes(i);
      byte[] cf = Bytes.toBytes(i);
      byte[] cq = Bytes.toBytes(i);
      byte[] value = Bytes.toBytes(i);
      KeyValue keyValue = new KeyValue(row, cf, cq, value);
      kvs.add(keyValue);
    }

    context.checking(new Expectations() {
      {
        oneOf(regionScanner).nextRaw(with(any(List.class)), with(any(Integer.class)));
        will(addElements(kvs.toArray()));
        oneOf(ctx).writeAndFlush(with(any(Response.class)));
      }
    });

    scanRunnable.onMessage(3);
  }

  @Test
  public void scanRunnableCanReactOnChannel() throws Throwable {
    ArrayList<KeyValue> kvs = new ArrayList<>();

    byte[] cf = Bytes.toBytes("cf");
    byte[] cq = Bytes.toBytes("cq");
    byte[] value = Bytes.toBytes("value");

    Integer count = 3;
    for (int i = 0; i != count; i++) {
      kvs.add(new KeyValue(Bytes.toBytes(i), cf, cq, value));
    }

    MemoryChannel<Integer> memoryChannel = new MemoryChannel<>();
    Fiber fiber = new ThreadFiber();
    fiber.start();
    memoryChannel.subscribe(fiber, scanRunnable);
    MemoryChannel testChannel = new MemoryChannel();
    AsyncChannelAsserts.ChannelListener listener = listenTo(testChannel);

    context.checking(new Expectations() {
      {
        oneOf(regionScanner).nextRaw(with(any(List.class)), with(any(Integer.class)));
        will(addElements(kvs.toArray()));

        oneOf(ctx).writeAndFlush(with(any(Response.class)));
        will(new CustomAction("desc") {
          @Override
          public Object invoke(Invocation invocation) throws Throwable {
            testChannel.publish(123);
            return null;
          }
        });
      }
    });
    memoryChannel.publish(count);
    assertEventually(listener, matcher);

    int bigCount = 1000;
    kvs.clear();

    for (int i = 0; i != bigCount / 100; i++) {
      kvs.add(new KeyValue(Bytes.toBytes(i), cf, cq, value));
    }

    context.checking(new Expectations() {
      {
        exactly(100).of(regionScanner).nextRaw(with(any(List.class)), with(any(Integer.class)));
        will(addElements(kvs.toArray()));

        exactly(10).of(ctx).writeAndFlush(with(any(Response.class)));
        will(new CustomAction("desc") {
          @Override
          public Object invoke(Invocation invocation) throws Throwable {
            testChannel.publish(123);
            return null;
          }
        });
      }
    });
    memoryChannel.publish(bigCount);
    for (int i =0; i!= 10; i++) {
      assertEventually(listener, matcher);
    }
  }
}