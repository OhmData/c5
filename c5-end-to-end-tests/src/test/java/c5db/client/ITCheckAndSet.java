/*
 * Copyright 2014 WANdisco
 *
 *  WANdisco licenses this file to you under the Apache License,
 *  version 2.0 (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

package c5db.client;

import c5db.ClusterOrPseudoCluster;
import org.junit.Test;

import java.io.IOException;

import static c5db.client.DataHelper.checkAndDeleteRowAndValueIntoDatabase;
import static c5db.client.DataHelper.checkAndPutRowAndValueIntoDatabase;
import static c5db.client.DataHelper.putRowAndValueIntoDatabase;
import static c5db.client.DataHelper.valueReadFromDB;
import static c5db.testing.BytesMatchers.equalTo;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class ITCheckAndSet extends ClusterOrPseudoCluster {

  @Test
  public void shouldFailCheckAndPutNullRow() throws IOException {
    assertFalse(checkAndPutRowAndValueIntoDatabase(table, row, value, notEqualToValue));
  }

  @Test
  public void shouldFailCheckAndPutWrongRow() throws IOException {
    putRowAndValueIntoDatabase(table, row, notEqualToValue);
    assertFalse(checkAndPutRowAndValueIntoDatabase(table, row, value, value));
    assertThat(valueReadFromDB(table, row), is(equalTo(notEqualToValue)));
  }

  @Test
  public void simpleCheckAndPutCanSucceed() throws IOException {
    putRowAndValueIntoDatabase(table, row, value);
    assertTrue(checkAndPutRowAndValueIntoDatabase(table, row, value, notEqualToValue));
    assertThat(valueReadFromDB(table, row), is(equalTo(notEqualToValue)));
  }

  @Test
  public void shouldFailCheckAndDeleteNullRow() throws IOException {
    assertFalse(checkAndDeleteRowAndValueIntoDatabase(table, row, value));
  }

  @Test
  public void shouldFailCheckAndDeleteWrongRow() throws IOException {
    putRowAndValueIntoDatabase(table, row, notEqualToValue);
    assertFalse(checkAndDeleteRowAndValueIntoDatabase(table, row, value));
    assertThat(valueReadFromDB(table, row), is(equalTo(notEqualToValue)));
  }

  @Test
  public void testSimpleCheckAndDeleteCanSucceed() throws IOException {
    putRowAndValueIntoDatabase(table, row, value);
    assertTrue(checkAndDeleteRowAndValueIntoDatabase(table, row, value));
    assertThat(valueReadFromDB(table, row), not(equalTo(value)));
  }
}