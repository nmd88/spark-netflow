/*
 * Copyright 2016 sadikovi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.sadikovi.netflowlib.record;

import io.netty.buffer.ByteBuf;

import com.github.sadikovi.netflowlib.predicate.Columns.Column;
import com.github.sadikovi.netflowlib.predicate.Operators.FilterPredicate;

/**
 * [[RecordMaterializer]] interface provides all necessary methods to parse single record and return
 * either array of values that match list and order of pruned columns, or null, if record does not
 * pass predicate. Predicate check is optional and should depend on implementation.
 */
public abstract class RecordMaterializer {
  RecordMaterializer() { }

  public abstract Object[] processRecord(ByteBuf buffer);

  /** Read buffer bytes sequence for column offset */
  public <T extends Comparable<T>> T readField(Column<T> column, ByteBuf buffer) {
    Class<T> type = column.getColumnType();
    if (column.getColumnType() == Byte.class) {
      return type.cast(buffer.getByte(column.getColumnOffset()));
    } else if (column.getColumnType() == Short.class) {
      return type.cast(buffer.getUnsignedByte(column.getColumnOffset()));
    } else if (column.getColumnType() == Integer.class) {
      return type.cast(buffer.getUnsignedShort(column.getColumnOffset()));
    } else if (column.getColumnType() == Long.class) {
      return type.cast(buffer.getUnsignedInt(column.getColumnOffset()));
    } else {
      throw new UnsupportedOperationException("Unsupported read type " + type.toString());
    }
  }
}
