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
package ohmdb.regionserver;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class RegistryFileTest {

  RegistryFile registryFile;

  @Before
  public void setup() {
    try {
      registryFile = new RegistryFile("/tmp");
      registryFile.truncate();
    } catch (FileNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
  }

  @Test
  public void addToFileAndReadBack() throws IOException {
    for (int i = 0; i != 10; i++) {
      registryFile.addEntry(getFakeHRegionInfo(i), getCF(i));
    }
    Map<HRegionInfo, List<HColumnDescriptor>> registry = registryFile.getRegistry();
    assertTrue(registry.size() == 7);
    assertTrue(registry.get(getFakeHRegionInfo(3)).size() == 4);
  }

  private HColumnDescriptor getCF(Integer i) {
    return new HColumnDescriptor(Bytes.toBytes(i.toString()));
  }

  private HRegionInfo getFakeHRegionInfo(Integer i) {
    if (i % 3 == 0) {
      byte[] tableName = Bytes.toBytes("fake");
      byte[] startKey = Bytes.toBytes("astart");
      byte[] endKey = Bytes.toBytes("zend");
      long regionId = 0;
      return new HRegionInfo(tableName, startKey, endKey, false, regionId);
    } else {
      byte[] tableName = Bytes.toBytes("fake" + i.toString());
      byte[] startKey = Bytes.toBytes("astart" + i.toString());
      byte[] endKey = Bytes.toBytes("zend" + i.toString());
      return new HRegionInfo(tableName, startKey, endKey);
    }
  }

}
