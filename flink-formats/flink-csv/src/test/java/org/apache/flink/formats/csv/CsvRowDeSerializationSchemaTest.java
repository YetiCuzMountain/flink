/*
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

package org.apache.flink.formats.csv;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.types.Row;
import org.apache.flink.util.InstantiationUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.apache.flink.connector.testutils.formats.SchemaTestUtils.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for {@link CsvRowSerializationSchema} and {@link CsvRowDeserializationSchema}. */
class CsvRowDeSerializationSchemaTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testSerializeDeserialize() throws Exception {

        testNullableField(Types.LONG, "null", null);
        testNullableField(Types.STRING, "null", null);
        testNullableField(Types.VOID, "null", null);
        testNullableField(Types.STRING, "\"This is a test.\"", "This is a test.");
        testNullableField(Types.STRING, "\"This is a test\n\r.\"", "This is a test\n\r.");
        testNullableField(Types.BOOLEAN, "true", true);
        testNullableField(Types.BOOLEAN, "null", null);
        testNullableField(Types.BYTE, "124", (byte) 124);
        testNullableField(Types.SHORT, "10000", (short) 10000);
        testNullableField(Types.INT, "1234567", 1234567);
        testNullableField(Types.LONG, "12345678910", 12345678910L);
        testNullableField(Types.FLOAT, "0.33333334", 0.33333334f);
        testNullableField(Types.DOUBLE, "0.33333333332", 0.33333333332d);
        testNullableField(
                Types.BIG_DEC,
                "\"1234.0000000000000000000000001\"",
                new BigDecimal("1234.0000000000000000000000001"));
        testNullableField(
                Types.BIG_INT,
                "\"123400000000000000000000000000\"",
                new BigInteger("123400000000000000000000000000"));
        testNullableField(Types.SQL_DATE, "2018-10-12", Date.valueOf("2018-10-12"));
        testNullableField(Types.SQL_TIME, "12:12:12", Time.valueOf("12:12:12"));
        testNullableField(
                Types.SQL_TIMESTAMP,
                "\"2018-10-12 12:12:12.0\"",
                Timestamp.valueOf("2018-10-12 12:12:12"));
        testNullableField(Types.LOCAL_DATE, "2018-10-12", Date.valueOf("2018-10-12").toLocalDate());
        testNullableField(Types.LOCAL_TIME, "12:12:12", Time.valueOf("12:12:12").toLocalTime());
        testNullableField(
                Types.LOCAL_DATE_TIME,
                "\"2018-10-12 12:12:12\"",
                LocalDateTime.parse("2018-10-12T12:12:12"));
        testNullableField(
                Types.INSTANT,
                "\"1970-01-01 00:00:01.123456789Z\"",
                Instant.ofEpochMilli(1123).plusNanos(456789));
        testNullableField(Types.INSTANT, "\"1970-01-01 00:00:12Z\"", Instant.ofEpochSecond(12));
        testNullableField(
                Types.ROW(Types.STRING, Types.INT, Types.BOOLEAN),
                "Hello;42;false",
                Row.of("Hello", 42, false));
        testNullableField(Types.OBJECT_ARRAY(Types.STRING), "a;b;c", new String[] {"a", "b", "c"});
        testNullableField(Types.OBJECT_ARRAY(Types.BYTE), "12;4;null", new Byte[] {12, 4, null});
        testNullableField(
                (TypeInformation<byte[]>) Types.PRIMITIVE_ARRAY(Types.BYTE),
                "awML",
                new byte[] {107, 3, 11});
    }

    @Test
    void testSerializeDeserializeCustomizedProperties() throws Exception {

        final Consumer<CsvRowSerializationSchema.Builder> serConfig =
                (serSchemaBuilder) ->
                        serSchemaBuilder
                                .setEscapeCharacter('*')
                                .setQuoteCharacter('\'')
                                .setArrayElementDelimiter(":")
                                .setFieldDelimiter(';');

        final Consumer<CsvRowDeserializationSchema.Builder> deserConfig =
                (deserSchemaBuilder) ->
                        deserSchemaBuilder
                                .setEscapeCharacter('*')
                                .setQuoteCharacter('\'')
                                .setArrayElementDelimiter(":")
                                .setFieldDelimiter(';');

        testField(Types.STRING, "123*'4**", "123'4*", deserConfig, ";");
        testField(Types.STRING, "'123''4**'", "123'4*", serConfig, deserConfig, ";");
        testField(Types.STRING, "'a;b*'c'", "a;b'c", deserConfig, ";");
        testField(Types.STRING, "'a;b''c'", "a;b'c", serConfig, deserConfig, ";");
        testField(Types.INT, "       12          ", 12, deserConfig, ";");
        testField(Types.INT, "12", 12, serConfig, deserConfig, ";");
        testField(
                Types.LOCAL_DATE_TIME,
                "    2018-10-12 12:12:12     ",
                LocalDateTime.parse("2018-10-12T12:12:12"),
                deserConfig,
                ";");
        testField(
                Types.ROW(Types.STRING, Types.STRING),
                "1:hello",
                Row.of("1", "hello"),
                deserConfig,
                ";");
        testField(
                Types.ROW(Types.STRING, Types.STRING),
                "'1:hello'",
                Row.of("1", "hello"),
                serConfig,
                deserConfig,
                ";");
        testField(
                Types.ROW(Types.STRING, Types.STRING),
                "'1:hello world'",
                Row.of("1", "hello world"),
                serConfig,
                deserConfig,
                ";");
        testField(
                Types.STRING,
                "null",
                "null",
                serConfig,
                deserConfig,
                ";"); // string because null literal has not been set
    }

    @Test
    void testDeserializeParseError() {
        assertThatThrownBy(() -> testDeserialization(false, false, "Test,null,Test"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testDeserializeUnsupportedNull() throws Exception {
        // unsupported null for integer
        assertThat(testDeserialization(true, false, "Test,null,Test"))
                .isEqualTo(Row.of("Test", null, "Test"));
    }

    @Test
    void testDeserializeIncompleteRow() throws Exception {
        // last two columns are missing
        assertThat(testDeserialization(true, false, "Test")).isEqualTo(Row.of("Test", null, null));
    }

    @Test
    void testDeserializeMoreColumnsThanExpected() throws Exception {
        // one additional string column
        assertThat(testDeserialization(true, false, "Test,12,Test,Test")).isNull();
    }

    @Test
    void testDeserializeIgnoreComment() throws Exception {
        // # is part of the string
        assertThat(testDeserialization(false, false, "#Test,12,Test"))
                .isEqualTo(Row.of("#Test", 12, "Test"));
    }

    @Test
    void testDeserializeAllowComment() throws Exception {
        // entire row is ignored
        assertThat(testDeserialization(true, true, "#Test,12,Test")).isNull();
    }

    @Test
    void testSerializationProperties() throws Exception {
        final TypeInformation<Row> rowInfo = Types.ROW(Types.STRING, Types.INT, Types.STRING);
        final CsvRowSerializationSchema.Builder serSchemaBuilder =
                new CsvRowSerializationSchema.Builder(rowInfo).setLineDelimiter("\r");

        assertThat(serialize(serSchemaBuilder, Row.of("Test", 12, "Hello")))
                .isEqualTo("Test,12,Hello\r".getBytes());

        serSchemaBuilder.setQuoteCharacter('#');

        assertThat(serialize(serSchemaBuilder, Row.of("Test", 12, "2019-12-26 12:12:12")))
                .isEqualTo("Test,12,#2019-12-26 12:12:12#\r".getBytes());

        serSchemaBuilder.disableQuoteCharacter();

        assertThat(serialize(serSchemaBuilder, Row.of("Test", 12, "2019-12-26 12:12:12")))
                .isEqualTo("Test,12,2019-12-26 12:12:12\r".getBytes());
    }

    @Test
    void testEmptyLineDelimiter() throws Exception {
        final TypeInformation<Row> rowInfo = Types.ROW(Types.STRING, Types.INT, Types.STRING);
        final CsvRowSerializationSchema.Builder serSchemaBuilder =
                new CsvRowSerializationSchema.Builder(rowInfo).setLineDelimiter("");

        assertThat(serialize(serSchemaBuilder, Row.of("Test", 12, "Hello")))
                .isEqualTo("Test,12,Hello".getBytes());
    }

    @Test
    void testInvalidNesting() {
        assertThatThrownBy(
                        () ->
                                testNullableField(
                                        Types.ROW(Types.ROW(Types.STRING)),
                                        "FAIL",
                                        Row.of(Row.of("FAIL"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidType() {
        assertThatThrownBy(
                        () ->
                                testNullableField(
                                        Types.GENERIC(java.util.Date.class),
                                        "FAIL",
                                        new java.util.Date()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSerializeDeserializeNestedTypes() throws Exception {
        final TypeInformation<Row> subDataType0 =
                Types.ROW(
                        Types.STRING,
                        Types.INT,
                        Types.STRING,
                        Types.LOCAL_DATE_TIME,
                        Types.INSTANT);
        final TypeInformation<Row> subDataType1 =
                Types.ROW(
                        Types.STRING,
                        Types.INT,
                        Types.STRING,
                        Types.LOCAL_DATE_TIME,
                        Types.INSTANT);
        final TypeInformation<Row> rowInfo = Types.ROW(subDataType0, subDataType1);

        // serialization
        CsvRowSerializationSchema.Builder serSchemaBuilder =
                new CsvRowSerializationSchema.Builder(rowInfo);
        // deserialization
        CsvRowDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDeserializationSchema.Builder(rowInfo);

        Row normalRow =
                Row.of(
                        Row.of(
                                "hello",
                                1,
                                "This is 1st top column",
                                LocalDateTime.parse("1970-01-01T01:02:03"),
                                Instant.ofEpochMilli(1000)),
                        Row.of(
                                "world",
                                2,
                                "This is 2nd top column",
                                LocalDateTime.parse("1970-01-01T01:02:04"),
                                Instant.ofEpochMilli(2000)));
        testSerDeConsistency(normalRow, serSchemaBuilder, deserSchemaBuilder);

        Row nullRow =
                Row.of(
                        null,
                        Row.of(
                                "world",
                                2,
                                "This is 2nd top column after null",
                                LocalDateTime.parse("1970-01-01T01:02:05"),
                                Instant.ofEpochMilli(3000)));
        testSerDeConsistency(nullRow, serSchemaBuilder, deserSchemaBuilder);
    }

    private <T> void testNullableField(TypeInformation<T> fieldInfo, String string, T value)
            throws Exception {
        testField(
                fieldInfo,
                string,
                value,
                (deserSchema) -> deserSchema.setNullLiteral("null"),
                (serSchema) -> serSchema.setNullLiteral("null"),
                ",");
    }

    private <T> void testField(
            TypeInformation<T> fieldInfo,
            String csvValue,
            T value,
            Consumer<CsvRowSerializationSchema.Builder> serializationConfig,
            Consumer<CsvRowDeserializationSchema.Builder> deserializationConfig,
            String fieldDelimiter)
            throws Exception {
        final TypeInformation<Row> rowInfo = Types.ROW(Types.STRING, fieldInfo, Types.STRING);
        final String expectedCsv = "BEGIN" + fieldDelimiter + csvValue + fieldDelimiter + "END\n";
        final Row expectedRow = Row.of("BEGIN", value, "END");

        // serialization
        final CsvRowSerializationSchema.Builder serSchemaBuilder =
                new CsvRowSerializationSchema.Builder(rowInfo);
        serializationConfig.accept(serSchemaBuilder);
        final byte[] serializedRow = serialize(serSchemaBuilder, expectedRow);
        assertThat(new String(serializedRow)).isEqualTo(expectedCsv);

        // deserialization
        final CsvRowDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDeserializationSchema.Builder(rowInfo);
        deserializationConfig.accept(deserSchemaBuilder);
        final Row deserializedRow = deserialize(deserSchemaBuilder, expectedCsv);
        assertThat(deserializedRow).isEqualTo(expectedRow);
    }

    private <T> void testField(
            TypeInformation<T> fieldInfo,
            String csvValue,
            T value,
            Consumer<CsvRowDeserializationSchema.Builder> deserializationConfig,
            String fieldDelimiter)
            throws Exception {
        final TypeInformation<Row> rowInfo = Types.ROW(Types.STRING, fieldInfo, Types.STRING);
        final String csv = "BEGIN" + fieldDelimiter + csvValue + fieldDelimiter + "END\n";
        final Row expectedRow = Row.of("BEGIN", value, "END");

        // deserialization
        final CsvRowDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDeserializationSchema.Builder(rowInfo);
        deserializationConfig.accept(deserSchemaBuilder);
        final Row deserializedRow = deserialize(deserSchemaBuilder, csv);
        assertThat(deserializedRow).isEqualTo(expectedRow);
    }

    private Row testDeserialization(
            boolean allowParsingErrors, boolean allowComments, String string) throws Exception {
        final TypeInformation<Row> rowInfo = Types.ROW(Types.STRING, Types.INT, Types.STRING);
        final CsvRowDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDeserializationSchema.Builder(rowInfo)
                        .setIgnoreParseErrors(allowParsingErrors)
                        .setAllowComments(allowComments);
        return deserialize(deserSchemaBuilder, string);
    }

    private void testSerDeConsistency(
            Row originalRow,
            CsvRowSerializationSchema.Builder serSchemaBuilder,
            CsvRowDeserializationSchema.Builder deserSchemaBuilder)
            throws Exception {
        Row deserializedRow =
                deserialize(
                        deserSchemaBuilder, new String(serialize(serSchemaBuilder, originalRow)));
        assertThat(originalRow).isEqualTo(deserializedRow);
    }

    private static byte[] serialize(CsvRowSerializationSchema.Builder serSchemaBuilder, Row row)
            throws Exception {
        // we serialize and deserialize the schema to test runtime behavior
        // when the schema is shipped to the cluster
        final CsvRowSerializationSchema schema =
                InstantiationUtil.deserializeObject(
                        InstantiationUtil.serializeObject(serSchemaBuilder.build()),
                        CsvRowDeSerializationSchemaTest.class.getClassLoader());
        open(schema);
        return schema.serialize(row);
    }

    private static Row deserialize(
            CsvRowDeserializationSchema.Builder deserSchemaBuilder, String csv) throws Exception {
        // we serialize and deserialize the schema to test runtime behavior
        // when the schema is shipped to the cluster
        final CsvRowDeserializationSchema schema =
                InstantiationUtil.deserializeObject(
                        InstantiationUtil.serializeObject(deserSchemaBuilder.build()),
                        CsvRowDeSerializationSchemaTest.class.getClassLoader());
        open(schema);
        return schema.deserialize(csv.getBytes());
    }
}
