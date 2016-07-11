/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.jdbc.impl.actions;

import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static java.time.format.DateTimeFormatter.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
final class JDBCStatementHelper {

  private static final Logger log = LoggerFactory.getLogger(JDBCStatementHelper.class);

  private static final JsonArray EMPTY = new JsonArray(Collections.unmodifiableList(new ArrayList<>()));

  private static final Pattern DATETIME = Pattern.compile("^\\d{4}-(?:0[0-9]|1[0-2])-[0-9]{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z$");
  private static final Pattern DATE = Pattern.compile("^\\d{4}-(?:0[0-9]|1[0-2])-[0-9]{2}$");
  private static final Pattern TIME = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
  private static final Pattern UUID = Pattern.compile("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");

  private JDBCStatementHelper() {}

  public static void fillStatement(PreparedStatement statement, JsonArray in) throws SQLException {
    if (in == null) {
      in = EMPTY;
    }

    for (int i = 0; i < in.size(); i++) {
      Object value = in.getValue(i);

      if (value != null) {
        if (value instanceof String) {
          statement.setObject(i + 1, optimisticCast((String) value));
        } else {
          statement.setObject(i + 1, value);
        }
      } else {
        statement.setNull(i + 1, Types.NULL);
      }
    }
  }

  public static void fillStatement(CallableStatement statement, JsonArray in, JsonArray out) throws SQLException {
    if (in == null) {
      in = EMPTY;
    }

    if (out == null) {
      out = EMPTY;
    }

    int max = Math.max(in.size(), out.size());

    for (int i = 0; i < max; i++) {
      Object value = null;
      boolean set = false;

      if (i < in.size()) {
        value = in.getValue(i);
      }

      // found a in value, use it as a input parameter
      if (value != null) {
        if (value instanceof String) {
          statement.setObject(i + 1, optimisticCast((String) value));
        } else {
          statement.setObject(i + 1, value);
        }
        set = true;
      }

      // reset
      value = null;

      if (i < out.size()) {
        value = out.getValue(i);
      }

      // found a out value, use it as a output parameter
      if (value != null) {
        // We're using the int from the enum instead of the enum itself to allow working with Drivers
        // that have not been upgraded to Java8 yet.
        if (value instanceof String) {
          statement.registerOutParameter(i + 1, JDBCType.valueOf((String) value).getVendorTypeNumber());
        } else if (value instanceof Number) {
          // for cases where vendors have special codes (e.g.: Oracle)
          statement.registerOutParameter(i + 1, ((Number) value).intValue());
        }
        set = true;
      }

      if (!set) {
        // assume null input
        statement.setNull(i + 1, Types.NULL);
      }
    }
  }

  public static io.vertx.ext.sql.ResultSet asList(ResultSet rs) throws SQLException {

    List<String> columnNames = new ArrayList<>();
    ResultSetMetaData metaData = rs.getMetaData();
    int cols = metaData.getColumnCount();
    for (int i = 1; i <= cols; i++) {
      columnNames.add(metaData.getColumnLabel(i));
    }

    List<JsonArray> results = new ArrayList<>();

    while (rs.next()) {
      JsonArray result = new JsonArray();
      for (int i = 1; i <= cols; i++) {
        Object res = convertSqlValue(rs.getObject(i));
        if (res != null) {
          result.add(res);
        } else {
          result.addNull();
        }
      }
      results.add(result);
    }

    return new io.vertx.ext.sql.ResultSet(columnNames, results);
  }

  public static Object convertSqlValue(Object value) throws SQLException {
    if (value == null) {
      return null;
    }

    // valid json types are just returned as is
    if (value instanceof Boolean || value instanceof String || value instanceof byte[]) {
      return value;
    }

    // numeric values
    if (value instanceof Number) {
      if (value instanceof BigDecimal) {
        BigDecimal d = (BigDecimal) value;
        if (d.scale() == 0) {
          return ((BigDecimal) value).toBigInteger();
        } else {
          // we might loose precision here
          return ((BigDecimal) value).doubleValue();
        }
      }

      return value;
    }

    // temporal values
    /*if (value instanceof Time) {
      return ((Time) value).toLocalTime().atOffset(ZoneOffset.UTC).format(ISO_LOCAL_TIME);
    }

    if (value instanceof Date) {
      return ((Date) value).toLocalDate().format(ISO_LOCAL_DATE);
    }

    if (value instanceof Timestamp) {
      return OffsetDateTime.ofInstant(Instant.ofEpochMilli(((java.util.Date) value).getTime()), ZoneOffset.UTC).format(ISO_OFFSET_DATE_TIME);
    }*/

    // large objects
    if (value instanceof Clob) {
      Clob c = (Clob) value;
      try {
        // result might be truncated due to downcasting to int
        return c.getSubString(1, (int) c.length());
      } finally {
        c.free();
      }
    }

    if (value instanceof Blob) {
      Blob b = (Blob) value;
      try {
        // result might be truncated due to downcasting to int
        return b.getBytes(1, (int) b.length());
      } finally {
        b.free();
      }
    }

    // arrays
    if (value instanceof Array) {
      Array a = (Array) value;
      try {
        Object[] arr = (Object[]) a.getArray();
        if (arr != null) {
          JsonArray jsonArray = new JsonArray();
          for (Object o : arr) {
            jsonArray.add(convertSqlValue(o));
          }
          return jsonArray;
        }
      } finally {
        a.free();
      }
    }

    // fallback to String
    return value.toString();
  }

  public static Object optimisticCast(String value) {
    if (value == null) {
      return null;
    }

    try {
      /*// sql time
      if (TIME.matcher(value).matches()) {
        // convert from local time to instant
        Instant instant = LocalTime.parse(value).atDate(LocalDate.of(1970, 1, 1)).toInstant(ZoneOffset.UTC);
        // calculate the timezone offset in millis
        int offset = TimeZone.getDefault().getOffset(instant.toEpochMilli());
        // need to remove the offset since time has no TZ component
        return new Time(instant.toEpochMilli() - offset);
      }

      // sql date
      if (DATE.matcher(value).matches()) {
        // convert from local date to instant
        Instant instant = LocalDate.parse(value).atTime(LocalTime.of(0, 0, 0, 0)).toInstant(ZoneOffset.UTC);
        // calculate the timezone offset in millis
        int offset = TimeZone.getDefault().getOffset(instant.toEpochMilli());
        // need to remove the offset since time has no TZ component
        return new Date(instant.toEpochMilli() - offset);
      }

      // sql timestamp
      if (DATETIME.matcher(value).matches()) {
        Instant instant = Instant.from(ISO_INSTANT.parse(value));
        return new Timestamp(instant.toEpochMilli());
      }*/

      /*// sql uuid
      if (UUID.matcher(value).matches()) {
        return java.util.UUID.fromString(value);
      }*/

    } catch (RuntimeException e) {
      log.debug(e);
    }

    // unknown
    return value;
  }
}
