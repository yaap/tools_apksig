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

import static com.android.apksig.internal.zip.EocdRecord.CD_OFFSET_OFFSET;
import static com.android.apksig.internal.zip.EocdRecord.CD_RECORD_COUNT_TOTAL_OFFSET;
import static com.android.apksig.internal.zip.EocdRecord.CD_SIZE_OFFSET;
import static com.android.apksig.internal.zip.EocdRecord.ZIP64_EOCD_REC_MIN_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.UINT16_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.UINT32_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_LOCATOR_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_REC_HEADER_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_REC_MIN_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_REC_SIG;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(JUnit4.class)
public class EocdRecordTest {
    @Test
    public void createWithModifiedCentralDirectoryInfo_noZip64Needed_standardEocdUpdated() {
        ByteBuffer originalEocd = createStandardEocd(10, 1000, 2000, 0);
        int newRecordCount = 20;
        long newCdSizeBytes = 2000;
        long newCdOffset = 4000;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        assertEquals(ZIP_EOCD_REC_MIN_SIZE, result.remaining());
        assertEquals(ZIP_EOCD_REC_SIG, result.getInt(0));
        assertEquals(
                newRecordCount, ZipUtils.getUnsignedInt16(result, CD_RECORD_COUNT_TOTAL_OFFSET));
        assertEquals(newCdSizeBytes, ZipUtils.getUnsignedInt32(result, CD_SIZE_OFFSET));
        assertEquals(newCdOffset, ZipUtils.getUnsignedInt32(result, CD_OFFSET_OFFSET));
    }

    @Test
    public void
            createWithModifiedCentralDirectoryInfo_cdOffsetRequiresZip64_zip64StructureCreated() {
        ByteBuffer originalEocd = createStandardEocd(10, 1000, 2000, 0);
        int newRecordCount = 20;
        long newCdSizeBytes = 2000;
        long newCdOffset = UINT32_MAX_VALUE + 1;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        verifyZip64EocdStructure(result, newRecordCount, newCdSizeBytes, newCdOffset, 0);
    }

    @Test
    public void createWithModifiedCentralDirectoryInfo_cdSizeRequiresZip64_zip64StructureCreated() {
        ByteBuffer originalEocd = createStandardEocd(10, 1000, 2000, 0);
        int newRecordCount = 20;
        long newCdSizeBytes = UINT32_MAX_VALUE + 1;
        long newCdOffset = 4000;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        verifyZip64EocdStructure(result, newRecordCount, newCdSizeBytes, newCdOffset, 0);
    }

    @Test
    public void
            createWithModifiedCentralDirectoryInfo_recordCountRequiresZip64_zip64StructureCreated() {
        ByteBuffer originalEocd = createStandardEocd(10, 1000, 2000, 0);
        int newRecordCount = UINT16_MAX_VALUE + 1;
        long newCdSizeBytes = 2000;
        long newCdOffset = 4000;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        verifyZip64EocdStructure(result, newRecordCount, newCdSizeBytes, newCdOffset, 0);
    }

    @Test
    public void createWithModifiedCentralDirectoryInfo_withComment_commentPreservedInZip64() {
        int commentLength = 10;
        ByteBuffer originalEocd = createStandardEocd(10, 1000, 2000, commentLength);
        int newRecordCount = 20;
        long newCdSizeBytes = 2000;
        long newCdOffset = UINT32_MAX_VALUE + 1;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        verifyZip64EocdStructure(
                result, newRecordCount, newCdSizeBytes, newCdOffset, commentLength);
    }

    @Test
    public void createWithPaddedComment_standardEocd_commentLengthUpdated() {
        int initialCommentLength = 10;
        ByteBuffer originalEocd = createStandardEocd(10, 1000, 2000, initialCommentLength);
        int padding = 5;

        ByteBuffer result = EocdRecord.createWithPaddedComment(originalEocd, padding);

        assertEquals(originalEocd.capacity() + padding, result.capacity());
        int newCommentLength =
                ZipUtils.getUnsignedInt16(result, ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET);
        assertEquals(initialCommentLength + padding, newCommentLength);
    }

    @Test
    public void createWithPaddedComment_zip64Eocd_commentLengthUpdated() {
        int initialCommentLength = 10;
        ByteBuffer originalEocd = createZip64EocdStructure(10, 1000, 2000, initialCommentLength);
        int padding = 5;

        ByteBuffer result = EocdRecord.createWithPaddedComment(originalEocd, padding);

        assertEquals(originalEocd.capacity() + padding, result.capacity());

        // The comment is in the standard EOCD part of the zip64 structure.
        int standardEocdOffset = ZIP64_EOCD_REC_MIN_SIZE + ZIP64_EOCD_LOCATOR_SIZE;
        int newCommentLength =
                ZipUtils.getUnsignedInt16(
                        result, standardEocdOffset + ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET);
        assertEquals(initialCommentLength + padding, newCommentLength);
    }

    @Test
    public void createWithModifiedCentralDirectoryInfo_fromZip64ToStandard_validStandardEocd() {
        ByteBuffer originalEocd = createZip64EocdStructure(UINT16_MAX_VALUE, 1000, 2000, 0);
        int newRecordCount = 20;
        long newCdSizeBytes = 2000;
        long newCdOffset = 4000;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        assertEquals(ZIP_EOCD_REC_MIN_SIZE, result.remaining());
        assertEquals(ZIP_EOCD_REC_SIG, result.getInt(0));
        assertEquals(
                newRecordCount, ZipUtils.getUnsignedInt16(result, CD_RECORD_COUNT_TOTAL_OFFSET));
        assertEquals(newCdSizeBytes, ZipUtils.getUnsignedInt32(result, CD_SIZE_OFFSET));
        assertEquals(newCdOffset, ZipUtils.getUnsignedInt32(result, CD_OFFSET_OFFSET));
    }

    @Test
    public void createWithModifiedCentralDirectoryInfo_fromZip64ToStandard_noValidStandardEocd() {
        ByteBuffer originalZip64 = createZip64EocdStructure(UINT16_MAX_VALUE, 1000, 2000, 0);
        // Create a new buffer without the standard EOCD
        ByteBuffer originalEocd =
                ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE + ZIP64_EOCD_LOCATOR_SIZE);
        originalEocd.order(ByteOrder.LITTLE_ENDIAN);
        originalZip64.position(0);
        originalZip64.limit(ZIP64_EOCD_REC_MIN_SIZE + ZIP64_EOCD_LOCATOR_SIZE);
        originalEocd.put(originalZip64);
        originalEocd.flip();

        int newRecordCount = 20;
        long newCdSizeBytes = 2000;
        long newCdOffset = 4000;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        // Should create a new standard EOCD
        assertEquals(ZIP_EOCD_REC_MIN_SIZE, result.remaining());
        assertEquals(ZIP_EOCD_REC_SIG, result.getInt(0));
        assertEquals(
                newRecordCount, ZipUtils.getUnsignedInt16(result, CD_RECORD_COUNT_TOTAL_OFFSET));
        assertEquals(newCdSizeBytes, ZipUtils.getUnsignedInt32(result, CD_SIZE_OFFSET));
        assertEquals(newCdOffset, ZipUtils.getUnsignedInt32(result, CD_OFFSET_OFFSET));
    }

    @Test
    public void createWithModifiedCentralDirectoryInfo_fromZip64ToZip64_commentPreserved() {
        int commentLength = 15;
        ByteBuffer originalEocd =
                createZip64EocdStructure(UINT16_MAX_VALUE, 1000, 2000, commentLength);
        int newRecordCount = UINT16_MAX_VALUE + 1;
        long newCdSizeBytes = 2000;
        long newCdOffset = 4000;

        ByteBuffer result =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        originalEocd, newRecordCount, newCdSizeBytes, newCdOffset);

        verifyZip64EocdStructure(
                result, newRecordCount, newCdSizeBytes, newCdOffset, commentLength);
    }

    private ByteBuffer createStandardEocd(
            int recordCount, long cdSizeBytes, long cdOffset, int commentLength) {
        ByteBuffer eocd = ByteBuffer.allocate(22 + commentLength);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(ZIP_EOCD_REC_SIG);
        eocd.putShort((short) 0); // disk number
        eocd.putShort((short) 0); // disk with cd
        eocd.putShort((short) recordCount);
        eocd.putShort((short) recordCount);
        eocd.putInt((int) cdSizeBytes);
        eocd.putInt((int) cdOffset);
        eocd.putShort((short) commentLength);
        if (commentLength > 0) {
            eocd.put(new byte[commentLength]);
        }
        eocd.flip();
        return eocd;
    }

    private ByteBuffer createZip64EocdStructure(
            int recordCount, long cdSizeBytes, long cdOffset, int commentLength) {
        int zip64EocdRecordSize = ZIP64_EOCD_REC_MIN_SIZE;
        int zip64EocdLocatorSize = ZIP64_EOCD_LOCATOR_SIZE;
        int eocdRecordSize = ZIP_EOCD_REC_MIN_SIZE;
        int bufferSize =
                zip64EocdRecordSize + zip64EocdLocatorSize + eocdRecordSize + commentLength;

        ByteBuffer eocdStructure = ByteBuffer.allocate(bufferSize);
        eocdStructure.order(ByteOrder.LITTLE_ENDIAN);

        // 1. Zip64 EOCD Record
        eocdStructure.putInt(ZipUtils.ZIP64_EOCD_REC_SIG);
        eocdStructure.putLong(zip64EocdRecordSize - ZIP64_EOCD_REC_HEADER_SIZE);
        eocdStructure.position(zip64EocdRecordSize);

        // 2. Zip64 EOCD Locator
        eocdStructure.putInt(ZipUtils.ZIP64_EOCD_LOCATOR_SIG);
        eocdStructure.position(zip64EocdRecordSize + zip64EocdLocatorSize);

        // 3. EOCD Record
        eocdStructure.putInt(ZIP_EOCD_REC_SIG);
        eocdStructure.putShort((short) 0); // disk number
        eocdStructure.putShort((short) 0); // disk with cd
        eocdStructure.putShort((short) recordCount);
        eocdStructure.putShort((short) recordCount);
        eocdStructure.putInt((int) cdSizeBytes);
        eocdStructure.putInt((int) cdOffset);
        eocdStructure.putShort((short) commentLength);
        if (commentLength > 0) {
            eocdStructure.put(new byte[commentLength]);
        }
        eocdStructure.flip();
        return eocdStructure;
    }

    private void verifyZip64EocdStructure(
            ByteBuffer result,
            int recordCount,
            long cdSizeBytes,
            long cdOffset,
            int commentLength) {
        assertEquals(
                ZIP64_EOCD_REC_MIN_SIZE
                        + ZIP64_EOCD_LOCATOR_SIZE
                        + ZIP_EOCD_REC_MIN_SIZE
                        + commentLength,
                result.remaining());

        // 1. Verify ZIP64 EOCD Record
        assertEquals(ZipUtils.ZIP64_EOCD_REC_SIG, result.getInt());
        assertEquals(44, result.getLong()); // size of record - header - size
        result.getShort(); // version made by
        result.getShort(); // version needed
        result.getInt(); // disk number
        result.getInt(); // disk with cd
        assertEquals(recordCount, result.getLong());
        assertEquals(recordCount, result.getLong());
        assertEquals(cdSizeBytes, result.getLong());
        assertEquals(cdOffset, result.getLong());

        // 2. Verify ZIP64 EOCD Locator
        assertEquals(ZipUtils.ZIP64_EOCD_LOCATOR_SIG, result.getInt());
        result.getInt(); // disk with zip64 eocd
        assertEquals(cdOffset + cdSizeBytes, result.getLong());
        result.getInt(); // total disks

        // 3. Verify EOCD Record
        assertEquals(ZIP_EOCD_REC_SIG, result.getInt());
        result.getShort(); // disk number
        result.getShort(); // disk with cd
        assertEquals(UINT16_MAX_VALUE, ZipUtils.getUnsignedInt16(result));
        assertEquals(UINT16_MAX_VALUE, ZipUtils.getUnsignedInt16(result));
        assertEquals(UINT32_MAX_VALUE, ZipUtils.getUnsignedInt32(result));
        assertEquals(UINT32_MAX_VALUE, ZipUtils.getUnsignedInt32(result));
        assertEquals(commentLength, ZipUtils.getUnsignedInt16(result));
    }
}
