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

package c5db.tablet;

import c5db.client.generated.Condition;
import c5db.client.generated.Get;
import c5db.client.generated.MutationProto;
import c5db.client.generated.RegionAction;
import c5db.client.generated.RegionActionResult;
import c5db.client.generated.Result;
import c5db.client.generated.Scan;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.regionserver.wal.HLog;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Our interface to a region.
 * <p/>
 * Provides our abstraction to HRegion.
 */
public interface Region {

  ListenableFuture<Boolean> batchMutate(MutationProto mutateProto) throws IOException;

  /**
   * Creates instances of Region.  This exists to make mocking and testing
   * easier.
   * <p/>
   * Mock out the creator interface - then create/return mock region interfaces.
   */

  boolean mutate(MutationProto mutateProto, Condition condition) throws IOException;

  boolean exists(Get get) throws IOException;

  Result get(Get get) throws IOException;

  org.apache.hadoop.hbase.regionserver.RegionScanner getScanner(Scan scan) throws IOException;

  RegionActionResult processRegionAction(RegionAction regionAction);

  boolean rowInRange(byte[] row);

  /**
   * Constructor arguments basically.
   */
  public interface Creator {
    Region getHRegion(
        Path basePath,
        HRegionInfo regionInfo,
        HTableDescriptor tableDescriptor,
        HLog log,
        Configuration conf) throws IOException;
  }
}
