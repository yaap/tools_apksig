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

import static com.android.apksig.internal.zip.CentralDirectoryRecord.MIN_VERSION_SUPPORT_ZIP64;
import static com.android.apksig.internal.zip.EocdRecord.ZIP64_EOCD_REC_MIN_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.UINT16_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.UINT32_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_COMPRESSED_SIZE_FIELD_NAME;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_CENTRAL_DIR_OFFSET_FIELD_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_LOCATOR_SIG;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_LOCATOR_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_LOCATOR_ZIP64_EOCD_OFFSET_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_REC_CD_SIZE_FIELD_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_REC_HEADER_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_REC_SIG;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_REC_TOTAL_RECORD_COUNT_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_SIZE_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_LFH_OFFSET_FIELD_NAME;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_RECORD_ID;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_UNCOMPRESSED_SIZE_FIELD_NAME;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_CENTRAL_DIR_SIZE_FIELD_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_CENTRAL_DIR_TOTAL_RECORD_COUNT_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_REC_MIN_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_REC_SIG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.apksig.internal.zip.ZipUtils.Zip64Fields;
import com.android.apksig.util.DataSink;
import com.android.apksig.util.DataSource;
import com.android.apksig.zip.ZipFormatException;
import com.android.apksig.zip.ZipSections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
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

    @Test
    public void isEocdZip64_withZip64Signature_returnsTrue() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, ZIP64_EOCD_REC_SIG);

        assertTrue(ZipUtils.isEocdZip64(buffer));
    }

    @Test
    public void isEocdZip64_withZipSignature_returnsFalse() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, ZIP_EOCD_REC_SIG);

        assertFalse(ZipUtils.isEocdZip64(buffer));
    }

    @Test
    public void isEocdZip64_withInvalidSignature_throwsException() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, 0x12345678);

        assertThrows(IllegalArgumentException.class, () -> ZipUtils.isEocdZip64(buffer));
    }

    @Test
    public void getZipEocdCentralDirectoryOffset_zip64_returnsCorrectOffset() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP64_EOCD_REC_SIG);
        long expectedOffset = 0x123456789abcdef0L;
        eocd.putLong(ZIP64_EOCD_CENTRAL_DIR_OFFSET_FIELD_OFFSET, expectedOffset);

        long actualOffset = ZipUtils.getZipEocdCentralDirectoryOffset(eocd);

        assertEquals(expectedOffset, actualOffset);
    }

    @Test
    public void getZipEocdCentralDirectoryOffset_zip_returnsCorrectOffset() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP_EOCD_REC_SIG);
        long expectedOffset = 0x12345678L;
        eocd.putInt(16, (int) expectedOffset);

        long actualOffset = ZipUtils.getZipEocdCentralDirectoryOffset(eocd);

        assertEquals(expectedOffset, actualOffset);
    }

    @Test
    public void setZipEocdCentralDirectoryOffset_zip64WithoutLocator_throwsException() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP64_EOCD_REC_SIG);
        // This field is the size of the rest of the record,
        // 56 total bytes - 4 for the signature - 8 for the size itself.
        eocd.putLong(ZIP64_EOCD_SIZE_OFFSET, 44);
        eocd.putLong(ZIP64_EOCD_CENTRAL_DIR_OFFSET_FIELD_OFFSET, 0x1000);
        long newOffset = 0x2000;

        assertThrows(
                IllegalArgumentException.class,
                () -> ZipUtils.setZipEocdCentralDirectoryOffset(eocd, newOffset));
    }

    @Test
    public void setZipEocdCentralDirectoryOffset_zip64WithLocator_updatesEocdAndLocator() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE + ZIP64_EOCD_LOCATOR_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);

        // Zip64 EOCD record
        eocd.putInt(0, ZIP64_EOCD_REC_SIG);
        eocd.putLong(ZIP64_EOCD_SIZE_OFFSET, 44);
        long originalCdOffset = 0x2000;
        eocd.putLong(ZIP64_EOCD_CENTRAL_DIR_OFFSET_FIELD_OFFSET, originalCdOffset);

        // Zip64 EOCD locator
        int locatorOffset = ZIP64_EOCD_REC_MIN_SIZE;
        eocd.putInt(locatorOffset, ZIP64_EOCD_LOCATOR_SIG);
        long originalZip64EocdOffset = 0x10000;
        eocd.putLong(
                locatorOffset + ZIP64_EOCD_LOCATOR_ZIP64_EOCD_OFFSET_OFFSET,
                originalZip64EocdOffset);

        long newCdOffset = 0x3000;
        ZipUtils.setZipEocdCentralDirectoryOffset(eocd, newCdOffset);

        // Check CD offset in zip64 EOCD is updated
        assertEquals(newCdOffset, eocd.getLong(ZIP64_EOCD_CENTRAL_DIR_OFFSET_FIELD_OFFSET));

        // Check zip64 EOCD offset in locator is updated
        long delta = originalCdOffset - newCdOffset;
        long expectedNewZip64EocdOffset = originalZip64EocdOffset - delta;
        assertEquals(
                expectedNewZip64EocdOffset,
                eocd.getLong(locatorOffset + ZIP64_EOCD_LOCATOR_ZIP64_EOCD_OFFSET_OFFSET));
    }

    @Test
    public void setZipEocdCentralDirectoryOffset_zip_updatesOffset() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP_EOCD_REC_SIG);
        eocd.putInt(16, 0x1000);
        long newOffset = 0x2000;

        ZipUtils.setZipEocdCentralDirectoryOffset(eocd, newOffset);

        assertEquals(newOffset, eocd.getInt(16) & 0xffffffffL);
    }

    @Test
    public void findZipSections_withZip64Apk_returnsCorrectSections() throws Exception {
        long cdOffset = 0x100000000L;
        long cdSize = 0x8000;
        int cdRecordCount = 65536; // > 0xffff

        // Create a mock APK with a zip64 EOCD structure.
        // The structure is: [Zip64 EOCD Record] [Zip64 EOCD Locator] [EOCD Record]
        int zip64EocdRecordSize = ZIP64_EOCD_REC_MIN_SIZE;
        int zip64EocdLocatorSize = ZIP64_EOCD_LOCATOR_SIZE;
        int eocdRecordSize = ZIP_EOCD_REC_MIN_SIZE;
        int eocdStructureSize = zip64EocdRecordSize + zip64EocdLocatorSize + eocdRecordSize;

        long zip64EocdOffset = cdOffset + cdSize;
        long fileSize = zip64EocdOffset + eocdStructureSize;

        ByteBuffer apkBytes = ByteBuffer.allocate(eocdStructureSize);
        apkBytes.order(ByteOrder.LITTLE_ENDIAN);

        // 1. Zip64 EOCD Record
        apkBytes.putInt(ZIP64_EOCD_REC_SIG);
        apkBytes.putLong(zip64EocdRecordSize - ZIP64_EOCD_REC_HEADER_SIZE); // size of record
        apkBytes.putShort((short) MIN_VERSION_SUPPORT_ZIP64); // version made by
        apkBytes.putShort((short) MIN_VERSION_SUPPORT_ZIP64); // version needed to extract
        apkBytes.putInt(0); // number of this disk
        apkBytes.putInt(0); // number of disk with start of CD
        apkBytes.putLong(cdRecordCount); // total records on this disk
        apkBytes.putLong(cdRecordCount); // total records
        apkBytes.putLong(cdSize); // size of CD
        apkBytes.putLong(cdOffset); // offset of CD

        // 2. Zip64 EOCD Locator
        apkBytes.putInt(ZIP64_EOCD_LOCATOR_SIG);
        apkBytes.putInt(0); // number of disk with zip64 eocd
        apkBytes.putLong(zip64EocdOffset); // offset of zip64 eocd
        apkBytes.putInt(1); // total number of disks

        // 3. EOCD Record
        apkBytes.putInt(ZIP_EOCD_REC_SIG);
        apkBytes.putShort((short) 0); // number of this disk
        apkBytes.putShort((short) 0); // number of disk with start of CD
        ZipUtils.putUnsignedInt16(apkBytes, UINT16_MAX_VALUE); // total records on this disk
        ZipUtils.putUnsignedInt16(apkBytes, UINT16_MAX_VALUE); // total records
        ZipUtils.putUnsignedInt32(apkBytes, UINT32_MAX_VALUE); // size of CD
        ZipUtils.putUnsignedInt32(apkBytes, UINT32_MAX_VALUE); // offset of CD
        apkBytes.putShort((short) 0); // comment length
        apkBytes.flip();

        DataSource ds = new MockDataSource(fileSize, zip64EocdOffset, apkBytes);
        ZipSections sections = ZipUtils.findZipSections(ds);

        assertEquals(cdOffset, sections.getZipCentralDirectoryOffset());
        assertEquals(cdSize, sections.getZipCentralDirectorySizeBytes());
        assertEquals(cdRecordCount, sections.getZipCentralDirectoryRecordCount());
        assertEquals(zip64EocdOffset, sections.getZipEndOfCentralDirectoryOffset());
        assertTrue(ZipUtils.isEocdZip64(sections.getZipEndOfCentralDirectory()));
    }

    @Test
    public void updateZipEocdCommentLen_zip64_updatesStandardEocdCommentLen() {
        // Create a zip64 EOCD structure with a comment.
        int zip64EocdRecordSize = ZIP64_EOCD_REC_MIN_SIZE;
        int zip64EocdLocatorSize = ZIP64_EOCD_LOCATOR_SIZE;
        int eocdRecordSize = ZIP_EOCD_REC_MIN_SIZE;
        int commentLength = 10;
        int bufferSize =
                zip64EocdRecordSize + zip64EocdLocatorSize + eocdRecordSize + commentLength;

        ByteBuffer eocdStructure = ByteBuffer.allocate(bufferSize);
        eocdStructure.order(ByteOrder.LITTLE_ENDIAN);

        // 1. Zip64 EOCD Record
        eocdStructure.putInt(ZIP64_EOCD_REC_SIG);
        eocdStructure.putLong(
                ZIP64_EOCD_SIZE_OFFSET, zip64EocdRecordSize - ZIP64_EOCD_REC_HEADER_SIZE);
        eocdStructure.position(zip64EocdRecordSize);

        // 2. Zip64 EOCD Locator
        eocdStructure.putInt(ZIP64_EOCD_LOCATOR_SIG);
        eocdStructure.position(zip64EocdRecordSize + zip64EocdLocatorSize);

        // 3. EOCD Record
        int standardEocdOffset = zip64EocdRecordSize + zip64EocdLocatorSize;
        eocdStructure.putInt(standardEocdOffset, ZIP_EOCD_REC_SIG);
        eocdStructure.putShort(
                standardEocdOffset + ZipUtils.ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET, (short) 0);
        eocdStructure.position(0);

        ZipUtils.updateZipEocdCommentLen(eocdStructure);

        int updatedCommentLen =
                eocdStructure.getShort(
                        standardEocdOffset + ZipUtils.ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET);
        assertEquals(commentLength, updatedCommentLen);
    }

    @Test
    public void updateZipEocdCommentLen_zip_updatesCommentLen() {
        int commentLength = 10;
        int bufferSize = ZIP_EOCD_REC_MIN_SIZE + commentLength;
        ByteBuffer eocd = ByteBuffer.allocate(bufferSize);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP_EOCD_REC_SIG);
        eocd.putShort(ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET, (short) 0);
        eocd.position(0);

        ZipUtils.updateZipEocdCommentLen(eocd);

        int updatedCommentLen = eocd.getShort(ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET);
        assertEquals(commentLength, updatedCommentLen);
    }

    @Test
    public void getZipEocdCentralDirectorySizeBytes_zip64_returnsCorrectSize() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP64_EOCD_REC_SIG);
        long expectedSize = 0x123456789abcdef0L;
        eocd.putLong(ZIP64_EOCD_REC_CD_SIZE_FIELD_OFFSET, expectedSize);

        long actualSize = ZipUtils.getZipEocdCentralDirectorySizeBytes(eocd);

        assertEquals(expectedSize, actualSize);
    }

    @Test
    public void getZipEocdCentralDirectorySizeBytes_zip_returnsCorrectSize() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP_EOCD_REC_SIG);
        long expectedSize = 0x12345678L;
        eocd.putInt(ZIP_EOCD_CENTRAL_DIR_SIZE_FIELD_OFFSET, (int) expectedSize);

        long actualSize = ZipUtils.getZipEocdCentralDirectorySizeBytes(eocd);

        assertEquals(expectedSize, actualSize);
    }

    @Test
    public void getZipEocdCentralDirectoryTotalRecordCount_zip64_returnsCorrectCount() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP64_EOCD_REC_SIG);
        long expectedCount = 0x12345678;
        eocd.putLong(ZIP64_EOCD_REC_TOTAL_RECORD_COUNT_OFFSET, expectedCount);

        int actualCount = ZipUtils.getZipEocdCentralDirectoryTotalRecordCount(eocd);

        assertEquals(expectedCount, actualCount);
    }

    @Test
    public void getZipEocdCentralDirectoryTotalRecordCount_zip64_countTooLarge_throwsException() {
        // The original ZipSections class stored the central directory total record count in an
        // int, but zip64 supports a long value for this field. This test verifies an exception is
        // thrown if more than Integer.MAX_VALUE records are present since this would require a
        // new API and a new ZipSections class.
        ByteBuffer eocd = ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP64_EOCD_REC_SIG);
        long tooLargeCount = (long) Integer.MAX_VALUE + 1;
        eocd.putLong(ZIP64_EOCD_REC_TOTAL_RECORD_COUNT_OFFSET, tooLargeCount);

        assertThrows(
                IllegalArgumentException.class,
                () -> ZipUtils.getZipEocdCentralDirectoryTotalRecordCount(eocd));
    }

    @Test
    public void getZipEocdCentralDirectoryTotalRecordCount_zip_returnsCorrectCount() {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP_EOCD_REC_MIN_SIZE);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0, ZIP_EOCD_REC_SIG);
        int expectedCount = 0x1234;
        eocd.putShort(ZIP_EOCD_CENTRAL_DIR_TOTAL_RECORD_COUNT_OFFSET, (short) expectedCount);

        int actualCount = ZipUtils.getZipEocdCentralDirectoryTotalRecordCount(eocd);

        assertEquals(expectedCount, actualCount);
    }

    @Test
    public void findZipSections_withZipApk_returnsCorrectSections() throws Exception {
        long cdOffset = 0x1000;
        long cdSize = 0x800;
        int cdRecordCount = 10;

        int eocdRecordSize = 22;
        long eocdOffset = cdOffset + cdSize;
        long fileSize = eocdOffset + eocdRecordSize;

        ByteBuffer apkBytes = ByteBuffer.allocate(eocdRecordSize);
        apkBytes.order(ByteOrder.LITTLE_ENDIAN);

        // EOCD Record
        apkBytes.putInt(ZIP_EOCD_REC_SIG);
        apkBytes.putShort((short) 0); // number of this disk
        apkBytes.putShort((short) 0); // number of disk with start of CD
        ZipUtils.putUnsignedInt16(apkBytes, cdRecordCount); // total records on this disk
        ZipUtils.putUnsignedInt16(apkBytes, cdRecordCount); // total records
        ZipUtils.putUnsignedInt32(apkBytes, cdSize); // size of CD
        ZipUtils.putUnsignedInt32(apkBytes, cdOffset); // offset of CD
        apkBytes.putShort((short) 0); // comment length
        apkBytes.flip();

        DataSource ds = new MockDataSource(fileSize, eocdOffset, apkBytes);
        ZipSections sections = ZipUtils.findZipSections(ds);

        assertEquals(cdOffset, sections.getZipCentralDirectoryOffset());
        assertEquals(cdSize, sections.getZipCentralDirectorySizeBytes());
        assertEquals(cdRecordCount, sections.getZipCentralDirectoryRecordCount());
        assertEquals(eocdOffset, sections.getZipEndOfCentralDirectoryOffset());
        assertFalse(ZipUtils.isEocdZip64(sections.getZipEndOfCentralDirectory()));
    }

    @Test
    public void findZipSections_withZip64MarkersNoLocator_returnsCorrectSections()
            throws Exception {
        // This test verifies that when an EOCD record contains one of the ZIP64 marker values
        // (e.g., 0xffff for record count), but there is no ZIP64 EOCD Locator, the values from
        // the original EOCD are used. This simulates a non-ZIP64 file where a value happens to be
        // the marker value. Arbitrary data is added before the EOCD where the ZIP64 EOCD
        // Locator would otherwise be.
        long cdOffset = 0x1000;
        long cdSize = 0x800;
        int cdRecordCount = UINT16_MAX_VALUE;
        int dataSize = ZIP64_EOCD_LOCATOR_SIZE;

        long dataOffset = cdOffset + cdSize;
        long eocdOffset = dataOffset + dataSize;
        long fileSize = eocdOffset + ZIP_EOCD_REC_MIN_SIZE;

        ByteBuffer apkBytes = ByteBuffer.allocate(dataSize + ZIP_EOCD_REC_MIN_SIZE);
        apkBytes.order(ByteOrder.LITTLE_ENDIAN);

        // Data that is not a ZIP64 EOCD Locator
        byte[] data = new byte[dataSize];
        apkBytes.put(data);

        // Standard EOCD Record
        apkBytes.putInt(ZIP_EOCD_REC_SIG);
        apkBytes.putShort((short) 0); // number of this disk
        apkBytes.putShort((short) 0); // number of disk with start of CD
        ZipUtils.putUnsignedInt16(apkBytes, cdRecordCount); // total records on this disk
        ZipUtils.putUnsignedInt16(apkBytes, cdRecordCount); // total records
        ZipUtils.putUnsignedInt32(apkBytes, cdSize); // size of CD
        ZipUtils.putUnsignedInt32(apkBytes, cdOffset); // offset of CD
        apkBytes.putShort((short) 0); // comment length
        apkBytes.flip();

        DataSource ds = new MockDataSource(fileSize, dataOffset, apkBytes);
        ZipSections sections = ZipUtils.findZipSections(ds);

        assertEquals(cdOffset, sections.getZipCentralDirectoryOffset());
        assertEquals(cdSize, sections.getZipCentralDirectorySizeBytes());
        assertEquals(cdRecordCount, sections.getZipCentralDirectoryRecordCount());
        assertEquals(eocdOffset, sections.getZipEndOfCentralDirectoryOffset());
        assertFalse(ZipUtils.isEocdZip64(sections.getZipEndOfCentralDirectory()));
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

    private static class MockDataSource implements DataSource {
        private final long mSize;
        private final long mDataOffset;
        private final ByteBuffer mData;

        MockDataSource(long size, long dataOffset, ByteBuffer data) {
            mSize = size;
            mDataOffset = dataOffset;
            mData = data;
        }

        @Override
        public long size() {
            return mSize;
        }

        @Override
        public void feed(long offset, long size, DataSink sink) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void copyTo(long offset, int size, ByteBuffer dest) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer getByteBuffer(long offset, int size) throws IOException {
            if (offset < mDataOffset || (offset + size) > (mDataOffset + mData.capacity())) {
                throw new IOException(
                        "Requested data out of range: offset="
                                + offset
                                + ", size="
                                + size
                                + ", data range=["
                                + mDataOffset
                                + ", "
                                + (mDataOffset + mData.capacity())
                                + ")");
            }
            int dataOffsetInBuf = (int) (offset - mDataOffset);
            ByteBuffer source = mData.duplicate();
            source.order(ByteOrder.LITTLE_ENDIAN);
            source.position(dataOffsetInBuf);
            source.limit(dataOffsetInBuf + size);
            ByteBuffer result = source.slice();
            result.order(ByteOrder.LITTLE_ENDIAN);
            return result;
        }

        @Override
        public DataSource slice(long offset, long size) {
            if (offset < 0 || size < 0 || (offset + size) > mSize) {
                throw new IllegalArgumentException(
                        "Requested slice out of range: offset="
                                + offset
                                + ", size="
                                + size
                                + ", data size="
                                + mSize);
            }
            // For simplicity, this mock implementation will return a new MockDataSource that
            // represents the sliced portion of the original data.
            // In a real scenario, you might want to create a new ByteBuffer slice.
            return new MockDataSource(size, mDataOffset + offset, mData);
        }
    }
}
