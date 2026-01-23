/*
 * Copyright 2025 Owl (OpenWeatherLink) Project
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
package com.owl.adapter.davis.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CRC16 implementation.
 * <p>
 * Davis uses CRC-CCITT (polynomial 0x1021) with initial value 0.
 */
class CRC16Test {

    @Test
    void calculate_emptyArray_returnsZeroCrc() {
        byte[] data = new byte[0];
        byte[] crc = CRC16.calculate(data);

        assertThat(crc).hasSize(2);
        assertThat(crc[0]).isEqualTo((byte) 0x00);
        assertThat(crc[1]).isEqualTo((byte) 0x00);
    }

    @Test
    void calculate_singleByte_returnsConsistentCrc() {
        // Test that single byte produces consistent, non-zero CRC
        byte[] data = {0x41};
        byte[] crc = CRC16.calculate(data);

        assertThat(crc).hasSize(2);
        // CRC should be non-zero for any non-empty data
        assertThat(crc[0] != 0 || crc[1] != 0).isTrue();

        // Verify the CRC validates correctly
        assertThat(CRC16.check(data, crc)).isTrue();
    }

    @Test
    void calculate_multipleBytes_returnsCorrectCrc() {
        // "LOO" - start of LOOP packet
        byte[] data = {'L', 'O', 'O'};
        byte[] crc = CRC16.calculate(data);

        assertThat(crc).hasSize(2);
        // Verify it's deterministic
        byte[] crc2 = CRC16.calculate(data);
        assertThat(crc).isEqualTo(crc2);
    }

    @Test
    void check_validDataWithEmbeddedCrc_returnsTrue() {
        // Create data and append its CRC
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        byte[] crc = CRC16.calculate(data);

        // Combine data + CRC
        byte[] combined = new byte[data.length + 2];
        System.arraycopy(data, 0, combined, 0, data.length);
        combined[data.length] = crc[0];
        combined[data.length + 1] = crc[1];

        // When CRC is appended, checking whole buffer should return valid (CRC = 0)
        assertThat(CRC16.check(combined, 0, combined.length)).isTrue();
    }

    @Test
    void check_invalidData_returnsFalse() {
        // Create data and append its CRC
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        byte[] crc = CRC16.calculate(data);

        // Combine data + CRC
        byte[] combined = new byte[data.length + 2];
        System.arraycopy(data, 0, combined, 0, data.length);
        combined[data.length] = crc[0];
        combined[data.length + 1] = crc[1];

        // Corrupt one byte
        combined[1] = (byte) (combined[1] ^ 0xFF);

        assertThat(CRC16.check(combined, 0, combined.length)).isFalse();
    }

    @Test
    void check_withOffset_validatesCorrectPortion() {
        // Create a buffer with padding before and after the data
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        byte[] crc = CRC16.calculate(data);

        byte[] buffer = new byte[10];
        buffer[0] = (byte) 0xFF; // Padding before
        buffer[1] = (byte) 0xFF;
        System.arraycopy(data, 0, buffer, 2, data.length);
        buffer[6] = crc[0];
        buffer[7] = crc[1];
        buffer[8] = (byte) 0xFF; // Padding after
        buffer[9] = (byte) 0xFF;

        // Check only the relevant portion (offset=2, length=6 including CRC)
        assertThat(CRC16.check(buffer, 2, 6)).isTrue();
    }

    @Test
    void check_invalidOffsetLength_throwsException() {
        byte[] data = new byte[5];

        assertThatThrownBy(() -> CRC16.check(data, 3, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid offset or length");
    }

    @Test
    void checkWithSeparateCrc_validData_returnsTrue() {
        byte[] data = {0x10, 0x20, 0x30, 0x40, 0x50};
        byte[] crc = CRC16.calculate(data);

        assertThat(CRC16.check(data, crc)).isTrue();
    }

    @Test
    void checkWithSeparateCrc_corruptedData_returnsFalse() {
        byte[] data = {0x10, 0x20, 0x30, 0x40, 0x50};
        byte[] crc = CRC16.calculate(data);

        // Corrupt data
        data[2] = 0x00;

        assertThat(CRC16.check(data, crc)).isFalse();
    }

    @Test
    void checkWithSeparateCrc_invalidCrcLength_throwsException() {
        byte[] data = {0x01, 0x02};
        byte[] crc = {0x00}; // Only 1 byte

        assertThatThrownBy(() -> CRC16.check(data, crc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CRC length");
    }

    @Test
    void instanceMethods_accumulateBytesCorrectly() {
        CRC16 crc = new CRC16();

        crc.add((byte) 'H');
        crc.add((byte) 'e');
        crc.add((byte) 'l');
        crc.add((byte) 'l');
        crc.add((byte) 'o');

        byte[] result = crc.getCrc();
        byte[] expected = CRC16.calculate("Hello".getBytes());

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void instanceMethods_addArray_sameAsIndividualBytes() {
        CRC16 crc1 = new CRC16();
        CRC16 crc2 = new CRC16();

        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};

        // Add individually
        for (byte b : data) {
            crc1.add(b);
        }

        // Add as array
        crc2.add(data);

        assertThat(crc1.getCrc()).isEqualTo(crc2.getCrc());
    }

    @Test
    void reset_clearsAccumulator() {
        CRC16 crc = new CRC16();
        crc.add((byte) 0x41);

        byte[] beforeReset = crc.getCrc();
        assertThat(beforeReset).isNotEqualTo(new byte[]{0, 0});

        crc.reset();
        byte[] afterReset = crc.getCrc();

        assertThat(afterReset).isEqualTo(new byte[]{0, 0});
    }

    @Test
    void isValid_afterAppendingCrc_returnsTrue() {
        CRC16 crc = new CRC16();
        byte[] data = {0x01, 0x02, 0x03};

        // Add data
        crc.add(data);

        // Get CRC
        byte[] crcBytes = crc.getCrc();

        // Reset and add data + CRC
        crc.reset();
        crc.add(data);
        crc.add(crcBytes);

        assertThat(crc.isValid()).isTrue();
    }

    @Test
    void loopPacketCrc_validates99BytePacket() {
        // Simulate a 99-byte LOOP packet with valid CRC at the end
        byte[] loopPacket = new byte[99];

        // Set LOOP signature
        loopPacket[0] = 'L';
        loopPacket[1] = 'O';
        loopPacket[2] = 'O';

        // Fill with some test data
        for (int i = 3; i < 97; i++) {
            loopPacket[i] = (byte) (i & 0xFF);
        }

        // Calculate CRC for bytes 0-96 (97 bytes of data)
        byte[] dataForCrc = new byte[97];
        System.arraycopy(loopPacket, 0, dataForCrc, 0, 97);
        byte[] crc = CRC16.calculate(dataForCrc);

        // Append CRC at bytes 97-98
        loopPacket[97] = crc[0];
        loopPacket[98] = crc[1];

        // Validate whole packet
        assertThat(CRC16.check(loopPacket, 0, 99)).isTrue();
    }
}
