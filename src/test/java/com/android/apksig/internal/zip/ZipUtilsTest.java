/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.apksig.internal.zip;

import static com.android.apksig.internal.zip.ZipUtils.UINT16_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.UINT32_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_COMPRESSED_SIZE_FIELD_NAME;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_LFH_OFFSET_FIELD_NAME;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_RECORD_ID;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_UNCOMPRESSED_SIZE_FIELD_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.apksig.internal.zip.ZipUtils.Zip64Fields;
import com.android.apksig.zip.ZipFormatException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(JUnit4.class)
public class ZipUtilsTest {
    private static final long EXPECTED_UNCOMPRESSED_VALUE = 0x80008000L;
    private static final long EXPECTED_COMPRESSED_VALUE = 0x80004000L;
    private static final long EXPECTED_LFH_OFFSET_VALUE = 0x80002000L;

    @Test
    public void parseExtraField_onlyZip64RecordAllValuesInZip64_correctValuesReturned()
            throws Exception {
        // This test verifies that all the fields affected by the 32-bit limitation can be parsed
        // in the Zip64 record when it is the only record in the extra field.
        Zip64Fields zip64Fields =
                new Zip64Fields(UINT32_MAX_VALUE, UINT32_MAX_VALUE, UINT32_MAX_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder()
                        .setUncompressedSize(EXPECTED_UNCOMPRESSED_VALUE)
                        .setCompressedSize(EXPECTED_COMPRESSED_VALUE)
                        .setLfhOffset(EXPECTED_LFH_OFFSET_VALUE)
                        .build();

        ZipUtils.parseExtraField(extra, zip64Fields);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, zip64Fields.uncompressedSize);
        assertEquals(EXPECTED_COMPRESSED_VALUE, zip64Fields.compressedSize);
        assertEquals(EXPECTED_LFH_OFFSET_VALUE, zip64Fields.localFileHeaderOffset);
    }

    @Test
    public void parseExtraField_zip64AndPriorRecordAllValuesInZip64_correctValuesReturned()
            throws Exception {
        // This test verifies that all the fields affected by the 32-bit limitation can be parsed
        // in the Zip64 record when it is preceded by another record in the extra field.
        Zip64Fields zip64Fields =
                new Zip64Fields(UINT32_MAX_VALUE, UINT32_MAX_VALUE, UINT32_MAX_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder()
                        .setPriorRecordSize(12)
                        .setUncompressedSize(EXPECTED_UNCOMPRESSED_VALUE)
                        .setCompressedSize(EXPECTED_COMPRESSED_VALUE)
                        .setLfhOffset(EXPECTED_LFH_OFFSET_VALUE)
                        .build();

        ZipUtils.parseExtraField(extra, zip64Fields);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, zip64Fields.uncompressedSize);
        assertEquals(EXPECTED_COMPRESSED_VALUE, zip64Fields.compressedSize);
        assertEquals(EXPECTED_LFH_OFFSET_VALUE, zip64Fields.localFileHeaderOffset);
    }

    @Test
    public void parseExtraField_zip64PriorAndNextRecordsAllValuesInZip64_correctValuesReturned()
            throws Exception {
        // This test verifies that all the fields affected by the 32-bit limitation can be parsed
        // in the Zip64 record when there is both a prior and subsequent record.
        Zip64Fields zip64Fields =
                new Zip64Fields(UINT32_MAX_VALUE, UINT32_MAX_VALUE, UINT32_MAX_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder()
                        .setPriorRecordSize(12)
                        .setUncompressedSize(EXPECTED_UNCOMPRESSED_VALUE)
                        .setCompressedSize(EXPECTED_COMPRESSED_VALUE)
                        .setLfhOffset(EXPECTED_LFH_OFFSET_VALUE)
                        .setNextRecordSize(8)
                        .build();

        ZipUtils.parseExtraField(extra, zip64Fields);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, zip64Fields.uncompressedSize);
        assertEquals(EXPECTED_COMPRESSED_VALUE, zip64Fields.compressedSize);
        assertEquals(EXPECTED_LFH_OFFSET_VALUE, zip64Fields.localFileHeaderOffset);
    }

    @Test
    public void parseExtraField_uncompressedInZip64_correctValuesReturned() throws Exception {
        // This test verifies that the uncompressed size can be parsed in the Zip64 record when
        // it is the only field in the record.
        Zip64Fields zip64Fields =
                new Zip64Fields(
                        UINT32_MAX_VALUE, EXPECTED_COMPRESSED_VALUE, EXPECTED_LFH_OFFSET_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder()
                        .setPriorRecordSize(12)
                        .setUncompressedSize(EXPECTED_UNCOMPRESSED_VALUE)
                        .setNextRecordSize(8)
                        .build();

        ZipUtils.parseExtraField(extra, zip64Fields);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, zip64Fields.uncompressedSize);
        assertEquals(EXPECTED_COMPRESSED_VALUE, zip64Fields.compressedSize);
        assertEquals(EXPECTED_LFH_OFFSET_VALUE, zip64Fields.localFileHeaderOffset);
    }

    @Test
    public void parseExtraField_bothSizeFieldsInZip64_correctValuesReturned() throws Exception {
        // This test verifies that the uncompressed and compressed sizes can be parsed in the Zip64
        // record when they are the only two fields in the record.
        Zip64Fields zip64Fields =
                new Zip64Fields(UINT32_MAX_VALUE, UINT32_MAX_VALUE, EXPECTED_LFH_OFFSET_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder()
                        .setPriorRecordSize(12)
                        .setUncompressedSize(EXPECTED_UNCOMPRESSED_VALUE)
                        .setCompressedSize(EXPECTED_COMPRESSED_VALUE)
                        .setNextRecordSize(8)
                        .build();

        ZipUtils.parseExtraField(extra, zip64Fields);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, zip64Fields.uncompressedSize);
        assertEquals(EXPECTED_COMPRESSED_VALUE, zip64Fields.compressedSize);
        assertEquals(EXPECTED_LFH_OFFSET_VALUE, zip64Fields.localFileHeaderOffset);
    }

    @Test
    public void parseExtraField_lfhOffsetInZip64_correctValuesReturned() throws Exception {
        // This test verifies that the LFH offset can be parsed in the Zip64 record when
        // it is the only field in the Zip64 record.
        Zip64Fields zip64Fields =
                new Zip64Fields(
                        EXPECTED_UNCOMPRESSED_VALUE, EXPECTED_COMPRESSED_VALUE, UINT32_MAX_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder()
                        .setPriorRecordSize(12)
                        .setLfhOffset(EXPECTED_LFH_OFFSET_VALUE)
                        .setNextRecordSize(8)
                        .build();

        ZipUtils.parseExtraField(extra, zip64Fields);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, zip64Fields.uncompressedSize);
        assertEquals(EXPECTED_COMPRESSED_VALUE, zip64Fields.compressedSize);
        assertEquals(EXPECTED_LFH_OFFSET_VALUE, zip64Fields.localFileHeaderOffset);
    }

    @Test
    public void parseExtraField_notAllFieldsInZip64_throwsException() throws Exception {
        // This test verifies that if multiple fields are expected in the Zip64 record, but the
        // record doesn't have enough space for all the data, the method throws an exception to
        // report the invalid zip format.
        Zip64Fields zip64Fields =
                new Zip64Fields(UINT32_MAX_VALUE, UINT32_MAX_VALUE, UINT32_MAX_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder()
                        .setPriorRecordSize(12)
                        .setUncompressedSize(EXPECTED_UNCOMPRESSED_VALUE)
                        .setCompressedSize(EXPECTED_COMPRESSED_VALUE)
                        .setNextRecordSize(8)
                        .build();

        assertThrows(ZipFormatException.class, () -> ZipUtils.parseExtraField(extra, zip64Fields));
    }

    @Test
    public void parseExtraField_noZip64RecordOtherRecords_valuesNotChanged() throws Exception {
        // This test verifies that if there is no Zip64 record in the extra field, then the
        // original values in the zip64Fields remain unchanged.
        Zip64Fields zip64Fields =
                new Zip64Fields(UINT32_MAX_VALUE, UINT32_MAX_VALUE, UINT32_MAX_VALUE);
        ByteBuffer extra =
                new ExtraBufferBuilder().setPriorRecordSize(12).setNextRecordSize(8).build();

        ZipUtils.parseExtraField(extra, zip64Fields);

        assertEquals(UINT32_MAX_VALUE, zip64Fields.uncompressedSize);
        assertEquals(UINT32_MAX_VALUE, zip64Fields.compressedSize);
        assertEquals(UINT32_MAX_VALUE, zip64Fields.localFileHeaderOffset);
    }

    @Test
    public void checkAndReturnZip64Value_headerValueNotInZip64_returnsExpectedValue()
            throws Exception {
        // This test verifies when a header value does not need to be stored in the Zip64 record,
        // then the original value for the header field is returned.
        long result =
                ZipUtils.checkAndReturnZip64Value(
                        EXPECTED_UNCOMPRESSED_VALUE,
                        -1,
                        "testRecord",
                        ZIP64_UNCOMPRESSED_SIZE_FIELD_NAME);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, result);
    }

    @Test
    public void checkAndReturnZip64Value_allValuesInZip64_returnsExpectedValues() throws Exception {
        // This test simulates the behavior when all of the header fields are expected to be in the
        // Zip64 record by checking and obtaining all values from the specified Zip64 value.
        final String recordName = "testRecord";
        long uncompressedSize =
                ZipUtils.checkAndReturnZip64Value(
                        UINT32_MAX_VALUE,
                        EXPECTED_UNCOMPRESSED_VALUE,
                        recordName,
                        ZIP64_UNCOMPRESSED_SIZE_FIELD_NAME);
        long compressedSize =
                ZipUtils.checkAndReturnZip64Value(
                        UINT32_MAX_VALUE,
                        EXPECTED_COMPRESSED_VALUE,
                        recordName,
                        ZIP64_COMPRESSED_SIZE_FIELD_NAME);
        long lfhOffset =
                ZipUtils.checkAndReturnZip64Value(
                        UINT32_MAX_VALUE,
                        EXPECTED_LFH_OFFSET_VALUE,
                        recordName,
                        ZIP64_LFH_OFFSET_FIELD_NAME);

        assertEquals(EXPECTED_UNCOMPRESSED_VALUE, uncompressedSize);
        assertEquals(EXPECTED_COMPRESSED_VALUE, compressedSize);
        assertEquals(EXPECTED_LFH_OFFSET_VALUE, lfhOffset);
    }

    @Test
    public void checkAndReturnZip64Value_valueNotObtainedFromZip64Record_throwsException()
            throws Exception {
        // If a header field indicates the value should be stored in the Zip64 record, but a value
        // cannot be obtained from this record, then an exception should be thrown to notify the
        // caller.
        assertThrows(
                ZipFormatException.class,
                () ->
                        ZipUtils.checkAndReturnZip64Value(
                                UINT32_MAX_VALUE,
                                UINT32_MAX_VALUE,
                                "testRecord",
                                ZIP64_UNCOMPRESSED_SIZE_FIELD_NAME));
    }

    private static class ExtraBufferBuilder {
        private int mPriorRecordSize = 0;
        private int mNextRecordSize = 0;
        private long mUncompressedSize = 0;
        private long mCompressedSize = 0;
        private long mLfhOffset = 0;

        ByteBuffer build() {
            int bufferCapacity = mPriorRecordSize + mNextRecordSize;
            // If any of the Zip64 fields are set, then include the headers and specified blocks
            // in the capacity.
            short zip64PayloadSize = 0;
            if (mUncompressedSize != 0 || mCompressedSize != 0 || mLfhOffset != 0) {
                bufferCapacity += 4;
                if (mUncompressedSize != 0) zip64PayloadSize += 8;
                if (mCompressedSize != 0) zip64PayloadSize += 8;
                if (mLfhOffset != 0) zip64PayloadSize += 8;
            }
            ByteBuffer extra = ByteBuffer.allocate(bufferCapacity + zip64PayloadSize);
            extra.order(ByteOrder.LITTLE_ENDIAN);
            if (mPriorRecordSize != 0) {
                short priorRecordPayloadSize = (short) (mPriorRecordSize - 4);
                extra.putShort((short) 0xabcd);
                extra.putShort(priorRecordPayloadSize);
                byte[] priorRecordPayload = new byte[priorRecordPayloadSize];
                extra.put(priorRecordPayload);
            }
            if (zip64PayloadSize > 0) {
                extra.putShort((short) ZIP64_RECORD_ID);
                extra.putShort(zip64PayloadSize);
                if (mUncompressedSize != 0) extra.putLong(mUncompressedSize);
                if (mCompressedSize != 0) extra.putLong(mCompressedSize);
                if (mLfhOffset != 0) extra.putLong(mLfhOffset);
            }
            if (mNextRecordSize != 0) {
                short nextRecordPayloadSize = (short) (mNextRecordSize - 4);
                // ApkSigner defines a header for alignment of the extra field; use that as the
                // last field here since it will likely be seen in most APKs signed by apksig.
                extra.putShort((short) 0xd935);
                extra.putShort(nextRecordPayloadSize);
                byte[] nextRecordPayload = new byte[nextRecordPayloadSize];
                extra.put(nextRecordPayload);
            }
            extra.position(0);
            return extra;
        }

        ExtraBufferBuilder setPriorRecordSize(int priorRecordSize) {
            if (priorRecordSize < 4 || priorRecordSize > UINT16_MAX_VALUE) {
                throw new RuntimeException(
                        "A prior record size must be between 4 and " + UINT16_MAX_VALUE + " bytes");
            }
            mPriorRecordSize = priorRecordSize;
            return this;
        }

        ExtraBufferBuilder setNextRecordSize(int nextRecordSize) {
            if (nextRecordSize < 4 || nextRecordSize > UINT16_MAX_VALUE) {
                throw new RuntimeException(
                        "A next record size must be between 4 and " + UINT16_MAX_VALUE + " bytes");
            }
            mNextRecordSize = nextRecordSize;
            return this;
        }

        ExtraBufferBuilder setUncompressedSize(long uncompressedSize) {
            mUncompressedSize = uncompressedSize;
            return this;
        }

        ExtraBufferBuilder setCompressedSize(long compressedSize) {
            mCompressedSize = compressedSize;
            return this;
        }

        ExtraBufferBuilder setLfhOffset(long lfhOffset) {
            mLfhOffset = lfhOffset;
            return this;
        }
    }
}
