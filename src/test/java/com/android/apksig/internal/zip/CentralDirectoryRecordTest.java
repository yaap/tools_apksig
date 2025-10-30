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

import static com.android.apksig.internal.zip.CentralDirectoryRecord.EXTRA_FIELD_OFFSET;
import static com.android.apksig.internal.zip.CentralDirectoryRecord.HEADER_SIZE_BYTES;
import static com.android.apksig.internal.zip.CentralDirectoryRecord.LOCAL_FILE_HEADER_OFFSET_OFFSET;
import static com.android.apksig.internal.zip.CentralDirectoryRecord.MIN_VERSION_SUPPORT_DEFLATE_COMPRESSION;
import static com.android.apksig.internal.zip.CentralDirectoryRecord.MIN_VERSION_SUPPORT_ZIP64;
import static com.android.apksig.internal.zip.CentralDirectoryRecord.RECORD_SIGNATURE;
import static com.android.apksig.internal.zip.CentralDirectoryRecord.VERSION_NEEDED_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.COMPRESSION_METHOD_DEFLATED;
import static com.android.apksig.internal.zip.ZipUtils.UINT32_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_RECORD_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.apksig.zip.ZipFormatException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(JUnit4.class)
public class CentralDirectoryRecordTest {
    @Test
    public void createWithModifiedLocalFileHeaderOffset_offsetGreaterThan4GB_zip64DataAdded()
            throws Exception {
        // This test verifies that when createWithModifiedLocalFileHeaderOffset is called on a
        // CentralDirectoryRecord with an offset greater than 4GB a new record is created with the
        // LFH offset in the header set to 0xffffffff and the provided offset is in the zip64 extra
        // field.
        CentralDirectoryRecord originalRecord =
                CentralDirectoryRecord.createWithDeflateCompressedData(
                        "test.txt", 0, 0, 12345, 100, 200, 1000);
        long newLfhOffset = 0x100000000L;

        CentralDirectoryRecord modifiedRecord =
                originalRecord.createWithModifiedLocalFileHeaderOffset(newLfhOffset);

        // Verify the new record has the expected LFH offset and the offset in the header is set to
        // the max 32-bit value.
        assertEquals(newLfhOffset, modifiedRecord.getLocalFileHeaderOffset());
        ByteBuffer data = ByteBuffer.allocate(modifiedRecord.getSize());
        data.order(ByteOrder.LITTLE_ENDIAN);
        modifiedRecord.copyTo(data);
        data.flip();

        assertEquals(
                UINT32_MAX_VALUE, ZipUtils.getUnsignedInt32(data, LOCAL_FILE_HEADER_OFFSET_OFFSET));

        // Also verify the version needed to extract has been updated to the min required for zip64.
        assertEquals(
                MIN_VERSION_SUPPORT_ZIP64, ZipUtils.getUnsignedInt16(data, VERSION_NEEDED_OFFSET));

        // Finally, verify the extra field now contains the zip64 record with the new LFH offset.
        int nameSize = ZipUtils.getUnsignedInt16(data, 28);
        int extraSize = ZipUtils.getUnsignedInt16(data, 30);
        ByteBuffer extra = data.slice();
        extra.order(ByteOrder.LITTLE_ENDIAN);
        extra.position(EXTRA_FIELD_OFFSET + nameSize);
        extra.limit(EXTRA_FIELD_OFFSET + nameSize + extraSize);
        boolean zip64RecordFound = false;
        while (extra.hasRemaining()) {
            int headerId = ZipUtils.getUnsignedInt16(extra);
            int size = ZipUtils.getUnsignedInt16(extra);
            if (headerId == ZIP64_RECORD_ID) {
                zip64RecordFound = true;
                // The original record did not have any fields requiring zip64, so the new record
                // should only contain the LFH offset.
                assertEquals(8, size);
                assertEquals(newLfhOffset, extra.getLong());
                break;
            } else {
                extra.position(extra.position() + size);
            }
        }
        assertTrue(zip64RecordFound);
    }

    @Test
    public void createWithDeflateCompressedData_offsetGreaterThan4GB_zip64DataAdded()
            throws Exception {
        // This test verifies that when createWithDeflateCompressedData is called with an offset
        // greater than 4GB a new record is created with the LFH offset in the header set to
        // 0xffffffff and the provided offset is in the zip64 extra field.
        long newLfhOffset = 0x100000000L;

        CentralDirectoryRecord modifiedRecord =
                CentralDirectoryRecord.createWithDeflateCompressedData(
                        "test.txt", 0, 0, 12345, 100, 200, newLfhOffset);

        // Verify the new record has the expected LFH offset and the offset in the header is set to
        // the max 32-bit value.
        assertEquals(newLfhOffset, modifiedRecord.getLocalFileHeaderOffset());
        ByteBuffer data = ByteBuffer.allocate(modifiedRecord.getSize());
        data.order(ByteOrder.LITTLE_ENDIAN);
        modifiedRecord.copyTo(data);
        data.flip();

        assertEquals(
                UINT32_MAX_VALUE, ZipUtils.getUnsignedInt32(data, LOCAL_FILE_HEADER_OFFSET_OFFSET));

        // Also verify the version needed to extract has been updated to the min required for zip64.
        assertEquals(
                MIN_VERSION_SUPPORT_ZIP64, ZipUtils.getUnsignedInt16(data, VERSION_NEEDED_OFFSET));

        // Finally, verify the extra field now contains the zip64 record with the new LFH offset.
        int nameSize = ZipUtils.getUnsignedInt16(data, 28);
        int extraSize = ZipUtils.getUnsignedInt16(data, 30);
        ByteBuffer extra = data.slice();
        extra.order(ByteOrder.LITTLE_ENDIAN);
        extra.position(EXTRA_FIELD_OFFSET + nameSize);
        extra.limit(EXTRA_FIELD_OFFSET + nameSize + extraSize);
        boolean zip64RecordFound = false;
        while (extra.hasRemaining()) {
            int headerId = ZipUtils.getUnsignedInt16(extra);
            int size = ZipUtils.getUnsignedInt16(extra);
            if (headerId == ZIP64_RECORD_ID) {
                zip64RecordFound = true;
                // The original record did not have any fields requiring zip64, so the new record
                // should only contain the LFH offset.
                assertEquals(8, size);
                assertEquals(newLfhOffset, extra.getLong());
                break;
            } else {
                extra.position(extra.position() + size);
            }
        }
        assertTrue(zip64RecordFound);
    }

    @Test
    public void getRecord_inputTooShortForHeader_throwsException() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES - 1);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(ZipFormatException.class, () -> CentralDirectoryRecord.getRecord(buffer));
    }

    @Test
    public void getRecord_invalidSignature_throwsException() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, 0x12345678); // Invalid signature
        assertThrows(ZipFormatException.class, () -> CentralDirectoryRecord.getRecord(buffer));
    }

    @Test
    public void getRecord_inputTooShortForRecord_throwsException() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES + 10);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, RECORD_SIGNATURE);
        buffer.putShort(28, (short) 20); // set nameSize so that recordSize > buffer.remaining
        assertThrows(ZipFormatException.class, () -> CentralDirectoryRecord.getRecord(buffer));
    }

    @Test
    public void getRecord_noZip64ExtraField_returnsCorrectRecord() throws Exception {
        String name = "test.txt";
        byte[] nameBytes = name.getBytes(UTF_8);
        int nameSize = nameBytes.length;
        int extraSize = 0;
        int commentSize = 0;
        int recordSize = 46 + nameSize + extraSize + commentSize;

        ByteBuffer buffer = ByteBuffer.allocate(recordSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(RECORD_SIGNATURE);
        buffer.putShort((short) MIN_VERSION_SUPPORT_DEFLATE_COMPRESSION); // Version made by
        buffer.putShort((short) MIN_VERSION_SUPPORT_DEFLATE_COMPRESSION); // Version needed
        buffer.putShort((short) 0); // GP flags
        buffer.putShort((short) 8); // Compression method (DEFLATED)
        buffer.putShort((short) 0); // Last mod time
        buffer.putShort((short) 0); // Last mod date
        buffer.putInt(12345); // CRC32
        buffer.putInt(100); // Compressed size
        buffer.putInt(200); // Uncompressed size
        buffer.putShort((short) nameSize);
        buffer.putShort((short) extraSize);
        buffer.putShort((short) commentSize);
        buffer.putShort((short) 0); // Disk number
        buffer.putShort((short) 0); // Internal file attributes
        buffer.putInt(0); // External file attributes
        buffer.putInt(1000); // Local file header offset
        buffer.put(nameBytes);
        buffer.flip();

        CentralDirectoryRecord record = CentralDirectoryRecord.getRecord(buffer);

        assertEquals(name, record.getName());
        assertEquals(100, record.getCompressedSize());
        assertEquals(200, record.getUncompressedSize());
        assertEquals(1000, record.getLocalFileHeaderOffset());
    }

    @Test
    public void getRecord_withZip64ExtraField_returnsCorrectRecord() throws Exception {
        String name = "test.txt";
        byte[] nameBytes = name.getBytes(UTF_8);
        int nameSize = nameBytes.length;
        int extraSize = 12; // 4 bytes for header + 8 bytes for LFH offset
        int commentSize = 0;
        int recordSize = 46 + nameSize + extraSize + commentSize;

        long expectedLfhOffset = 0x100000000L;

        ByteBuffer buffer = ByteBuffer.allocate(recordSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(RECORD_SIGNATURE); // Signature
        buffer.putShort((short) MIN_VERSION_SUPPORT_ZIP64); // Version made by
        buffer.putShort((short) MIN_VERSION_SUPPORT_ZIP64); // Version needed
        buffer.putShort((short) 0); // GP flags
        buffer.putShort((short) COMPRESSION_METHOD_DEFLATED);
        buffer.putShort((short) 0); // Last mod time
        buffer.putShort((short) 0); // Last mod date
        buffer.putInt(12345); // CRC32
        buffer.putInt(100); // Compressed size
        buffer.putInt(200); // Uncompressed size
        buffer.putShort((short) nameSize); // Name size
        buffer.putShort((short) extraSize); // Extra size
        buffer.putShort((short) commentSize); // Comment size
        buffer.putShort((short) 0); // Disk number
        buffer.putShort((short) 0); // Internal file attributes
        buffer.putInt(0); // External file attributes
        buffer.putInt((int) UINT32_MAX_VALUE); // Local file header offset (marker for zip64)
        buffer.put(nameBytes);
        // Extra field
        buffer.putShort((short) ZIP64_RECORD_ID);
        buffer.putShort((short) 8); // Size of LFH offset in zip64 extra field
        buffer.putLong(expectedLfhOffset);
        buffer.flip();

        CentralDirectoryRecord record = CentralDirectoryRecord.getRecord(buffer);

        assertEquals(name, record.getName());
        assertEquals(100, record.getCompressedSize());
        assertEquals(200, record.getUncompressedSize());
        assertEquals(expectedLfhOffset, record.getLocalFileHeaderOffset());
    }

    @Test
    public void createWithModifiedLocalFileHeaderOffset_offsetLessThan4GB_noZip64Data()
            throws Exception {
        CentralDirectoryRecord originalRecord =
                CentralDirectoryRecord.createWithDeflateCompressedData(
                        "test.txt", 0, 0, 12345, 100, 200, 1000);
        long newLfhOffset = 2000;

        CentralDirectoryRecord modifiedRecord =
                originalRecord.createWithModifiedLocalFileHeaderOffset(newLfhOffset);

        assertEquals(newLfhOffset, modifiedRecord.getLocalFileHeaderOffset());
        ByteBuffer data = ByteBuffer.allocate(modifiedRecord.getSize());
        data.order(ByteOrder.LITTLE_ENDIAN);
        modifiedRecord.copyTo(data);
        data.flip();

        assertEquals(
                newLfhOffset, ZipUtils.getUnsignedInt32(data, LOCAL_FILE_HEADER_OFFSET_OFFSET));
        assertEquals(0, ZipUtils.getUnsignedInt16(data, 30)); // Extra field length
    }

    @Test
    public void createWithDeflateCompressedData_offsetLessThan4GB_noZip64Data() throws Exception {
        long lfhOffset = 2000;

        CentralDirectoryRecord record =
                CentralDirectoryRecord.createWithDeflateCompressedData(
                        "test.txt", 0, 0, 12345, 100, 200, lfhOffset);

        assertEquals(lfhOffset, record.getLocalFileHeaderOffset());
        ByteBuffer mData = ByteBuffer.allocate(record.getSize());
        mData.order(ByteOrder.LITTLE_ENDIAN);
        record.copyTo(mData);
        mData.flip();

        assertEquals(lfhOffset, ZipUtils.getUnsignedInt32(mData, LOCAL_FILE_HEADER_OFFSET_OFFSET));
        assertEquals(0, ZipUtils.getUnsignedInt16(mData, 30)); // Extra field length
    }
}
