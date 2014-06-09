/*
 * Copyright (C) 2013  Ohm Data
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

/** Incorporates changes licensed under:
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package c5db.client.scanner;

import c5db.client.C5Constants;
import c5db.client.ProtobufUtil;
import c5db.client.RequestConverter;
import c5db.client.generated.RegionSpecifier;
import c5db.client.generated.Result;
import c5db.client.generated.ScanRequest;
import c5db.client.generated.ScanResponse;
import io.netty.channel.Channel;
import org.jetlang.fibers.Fiber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class C5QueueBasedClientScanner implements C5ClientScanner {
  private final Channel ch;
  private final long scannerId;
  private final ArrayBlockingQueue<Optional<c5db.client.generated.Result>> scanResults
      = new ArrayBlockingQueue<>(C5Constants.MAX_CACHE_SZ);
  private final long commandId;
  private final Fiber fiber;
  private boolean isClosed = false;
  private static final Logger LOG = LoggerFactory.getLogger(C5QueueBasedClientScanner.class);
  private int requestSize = C5Constants.DEFAULT_INIT_SCAN;

  /**
   * Create a new ClientScanner for the specified table
   * Note that the passed {@link org.apache.hadoop.hbase.client.Scan}'s start row maybe changed changed.
   */
  public C5QueueBasedClientScanner(Channel channel, Fiber fiber, final long scannerId, final long commandId) {
    ch = channel;
    this.scannerId = scannerId;
    this.commandId = commandId;
    this.fiber = fiber;
    this.fiber.start();
    long scannerUpdateRate = 100;

    this.fiber.scheduleWithFixedDelay(() -> {
      if (!isClosed) {
        optimizeRequestRate();
        getMoreRowsIfSpace();
      }
    }, 0l, scannerUpdateRate, TimeUnit.MILLISECONDS);

  }

  @Override
  public Result next() throws InterruptedException {
    if (this.isClosed && this.scanResults.isEmpty()) {
      return null;
    }

    c5db.client.generated.Result result;
    result = scanResults.take().orElse(null);
    return result == null ? null : result;
  }

  private void getMoreRowsIfSpace() {
    final int queueSpace = C5Constants.MAX_CACHE_SZ - this.scanResults.size();
    // If we have plenty of room for another request
    if (queueSpace > (requestSize + this.scanResults.size())) {
      getMoreRows();
    }
  }

  private void optimizeRequestRate() {
    if (this.scanResults.size() < .5 * requestSize && requestSize < C5Constants.MAX_REQUEST_SIZE) {
      requestSize = requestSize * 2;
    }
  }

  private void getMoreRows() {
    //TODO getRegion shouldn't be needed and currently is hardcoded
    final RegionSpecifier regionSpecifier = RequestConverter.buildRegionSpecifier(new byte[]{});

    final ScanRequest scanRequest = new ScanRequest(regionSpecifier, null, scannerId, requestSize, false, 0);
    ch.writeAndFlush(ProtobufUtil.getScanCall(commandId, scanRequest));
  }

  @Override
  public Result[] next(final int nbRows) throws IOException, InterruptedException {
    final ArrayList<Result> resultSets = new ArrayList<>(nbRows);
    for (int i = 0; i < nbRows; i++) {
      final Result next = next();
      if (next != null) {
        resultSets.add(next);
      } else {
        break;
      }
    }
    return resultSets.toArray(new Result[resultSets.size()]);
  }

  @Override
  public void close() throws InterruptedException {
    this.scanResults.put(Optional.ofNullable(null));
    this.fiber.dispose();
    this.isClosed = true;
  }

  @Override
  public void add(ScanResponse response) throws InterruptedException {
    for (c5db.client.generated.Result result : response.getResultsList()) {
      scanResults.put(Optional.of(result));
    }
    if (!this.isClosed && !response.getMoreResults()) {
      this.close();
    }

  }
}
