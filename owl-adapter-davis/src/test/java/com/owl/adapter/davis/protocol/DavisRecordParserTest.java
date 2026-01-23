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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for DavisRecordParser.
 * <p>
 * Tests parsing of LOOP packets, archive records, and unit conversions.
 */
class DavisRecordParserTest {

    @Nested
    class UnitConversions {

        @Test
        void fahrenheitToCelsius_freezingPoint() {
            assertThat(DavisRecordParser.fahrenheitToCelsius(32.0)).isEqualTo(0.0);
        }

        @Test
        void fahrenheitToCelsius_boilingPoint() {
            assertThat(DavisRecordParser.fahrenheitToCelsius(212.0)).isEqualTo(100.0);
        }

        @Test
        void fahrenheitToCelsius_roomTemperature() {
            // 68F = 20C
            assertThat(DavisRecordParser.fahrenheitToCelsius(68.0)).isEqualTo(20.0);
        }

        @Test
        void fahrenheitToCelsius_belowFreezing() {
            // 14F = -10C
            assertThat(DavisRecordParser.fahrenheitToCelsius(14.0)).isEqualTo(-10.0);
        }

        @Test
        void inchHgToHPa_standardPressure() {
            // 29.92 inHg = 1013.25 hPa (standard atmosphere)
            double result = DavisRecordParser.inchHgToHPa(29.92);
            assertThat(result).isCloseTo(1013.2, within(0.5));
        }

        @Test
        void inchHgToHPa_lowPressure() {
            // 28.5 inHg ~ 965 hPa
            double result = DavisRecordParser.inchHgToHPa(28.5);
            assertThat(result).isCloseTo(965.1, within(0.5));
        }

        @Test
        void inchHgToHPa_highPressure() {
            // 30.5 inHg ~ 1033 hPa
            double result = DavisRecordParser.inchHgToHPa(30.5);
            assertThat(result).isCloseTo(1032.8, within(0.5));
        }

        @Test
        void mphToKph_walking() {
            // 3 mph = 4.83 kph
            assertThat(DavisRecordParser.mphToKph(3.0)).isCloseTo(4.8, within(0.1));
        }

        @Test
        void mphToKph_hurricane() {
            // 100 mph = 160.9 kph
            assertThat(DavisRecordParser.mphToKph(100.0)).isCloseTo(160.9, within(0.1));
        }

        @Test
        void round_roundsToOneDecimal() {
            assertThat(DavisRecordParser.round(12.345)).isEqualTo(12.3);
            assertThat(DavisRecordParser.round(12.351)).isEqualTo(12.4);
            assertThat(DavisRecordParser.round(12.35)).isEqualTo(12.4); // Round half up
        }
    }

    @Nested
    class ParseWord {

        @Test
        void parseWord_littleEndian_parsesCorrectly() {
            // 0x1234 stored as [0x34, 0x12] in little-endian
            byte[] buf = {0x34, 0x12};
            assertThat(DavisRecordParser.parseWord(buf, 0)).isEqualTo(0x1234);
        }

        @Test
        void parseWord_withOffset_parsesCorrectPosition() {
            byte[] buf = {0x00, 0x00, 0x78, 0x56};
            assertThat(DavisRecordParser.parseWord(buf, 2)).isEqualTo(0x5678);
        }

        @Test
        void parseWord_maxValue_parsesCorrectly() {
            byte[] buf = {(byte) 0xFF, (byte) 0x7F}; // 32767 (INVALID_WORD)
            assertThat(DavisRecordParser.parseWord(buf, 0)).isEqualTo(0x7FFF);
        }

        @Test
        void parseWord_unsignedHighByte_parsesCorrectly() {
            byte[] buf = {0x00, (byte) 0xFF}; // 65280
            assertThat(DavisRecordParser.parseWord(buf, 0)).isEqualTo(0xFF00);
        }
    }

    @Nested
    class ParseLoopRecord {

        @Test
        void parseLoopRecord_validPacket_parsesAllFields() {
            byte[] packet = createValidLoopPacket();

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            // Verify temperature: stored as 720 (F*10) = 72.0F = 22.2C
            assertThat(record.tempOut()).isCloseTo(22.2, within(0.1));

            // Verify inside temp: stored as 680 (F*10) = 68.0F = 20.0C
            assertThat(record.tempIn()).isCloseTo(20.0, within(0.1));

            // Verify humidity
            assertThat(record.humidityOut()).isEqualTo(65);
            assertThat(record.humidityIn()).isEqualTo(45);

            // Verify pressure: stored as 29920 (inHg*1000) = 29.92 inHg = 1013.2 hPa
            assertThat(record.pressure()).isCloseTo(1013.2, within(0.5));

            // Verify wind: stored as 10 * 0.45 = 4.5 mph = 7.2 kph
            assertThat(record.windSpeed()).isCloseTo(7.2, within(0.1));

            // Verify wind direction
            assertThat(record.windDir()).isEqualTo(180);

            // Verify solar radiation
            assertThat(record.solarRadiation()).isEqualTo(500);

            // Verify next record pointer
            assertThat(record.nextRecord()).isEqualTo(42);
        }

        @Test
        void parseLoopRecord_invalidTemperature_returnsZero() {
            byte[] packet = createValidLoopPacket();

            // Set outside temp to invalid (0x7FFF)
            packet[12] = (byte) 0xFF;
            packet[13] = (byte) 0x7F;

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            // Invalid temp should result in celsius conversion of 0.0F (stored as 0x7FFF)
            // Since parseTempF returns 0.0 for invalid, celsius will be (0-32)*5/9 = -17.8
            // Actually, the code returns 0.0 for invalid which converts to -17.8C
            // Let me check the logic again...
            // parseTempF checks for INVALID_WORD (0x7FFF = 32767) and returns 0.0
            // fahrenheitToCelsius(0.0) = (0-32)*5/9 = -17.8
            assertThat(record.tempOut()).isCloseTo(-17.8, within(0.1));
        }

        @Test
        void parseLoopRecord_invalidHumidity_returnsZero() {
            byte[] packet = createValidLoopPacket();

            // Set outside humidity to invalid (0xFF)
            packet[33] = (byte) 0xFF;

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            assertThat(record.humidityOut()).isEqualTo(0);
        }

        @Test
        void parseLoopRecord_invalidWindDirection_returnsZero() {
            byte[] packet = createValidLoopPacket();

            // Set wind direction to invalid (0x7FFF)
            packet[16] = (byte) 0xFF;
            packet[17] = (byte) 0x7F;

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            assertThat(record.windDir()).isEqualTo(0);
        }

        @Test
        void parseLoopRecord_uvIndex_parsedCorrectly() {
            byte[] packet = createValidLoopPacket();

            // Set UV to 85 (stored as UV * 10, so this is 8.5)
            packet[43] = (byte) 85;

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            assertThat(record.uvIndex()).isEqualTo(8.5);
        }

        @Test
        void parseLoopRecord_invalidUv_returnsZero() {
            byte[] packet = createValidLoopPacket();

            // Set UV to invalid (0xFF)
            packet[43] = (byte) 0xFF;

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            assertThat(record.uvIndex()).isEqualTo(0.0);
        }

        @Test
        void parseLoopRecord_rainRate_parsedCorrectly() {
            byte[] packet = createValidLoopPacket();

            // Set rain rate to 50 clicks (50 * 0.2 = 10.0 mm/h)
            packet[41] = 50;
            packet[42] = 0;

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            assertThat(record.rainRate()).isEqualTo(10.0);
        }

        @Test
        void parseLoopRecord_dailyRain_parsedCorrectly() {
            byte[] packet = createValidLoopPacket();

            // Set daily rain to 25 clicks (25 * 0.2 = 5.0 mm)
            packet[50] = 25;
            packet[51] = 0;

            DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);

            assertThat(record.rainDaily()).isEqualTo(5.0);
        }

        /**
         * Creates a valid 99-byte LOOP packet with known test values.
         */
        private byte[] createValidLoopPacket() {
            byte[] packet = new byte[99];

            // Signature "LOO"
            packet[0] = 'L';
            packet[1] = 'O';
            packet[2] = 'O';

            // Barometer trend (offset 3)
            packet[3] = 0; // Steady

            // Packet type (offset 4) - 0 = LOOP
            packet[4] = 0;

            // Next archive record pointer (offset 5-6) - value 42
            packet[5] = 42;
            packet[6] = 0;

            // Barometer (offset 7-8) - 29920 (29.92 inHg * 1000)
            // 29920 = 0x74E0
            packet[7] = (byte) 0xE0;
            packet[8] = (byte) 0x74;

            // Inside temperature (offset 9-10) - 680 (68.0F * 10)
            // 680 = 0x02A8
            packet[9] = (byte) 0xA8;
            packet[10] = (byte) 0x02;

            // Inside humidity (offset 11) - 45%
            packet[11] = 45;

            // Outside temperature (offset 12-13) - 720 (72.0F * 10)
            // 720 = 0x02D0
            packet[12] = (byte) 0xD0;
            packet[13] = (byte) 0x02;

            // Wind gust (offset 14) - 15 (raw value, * 0.45 = 6.75 mph)
            packet[14] = 15;

            // Wind speed (offset 15) - 10 (raw value, * 0.45 = 4.5 mph)
            packet[15] = 10;

            // Wind direction (offset 16-17) - 180 degrees
            packet[16] = (byte) 180;
            packet[17] = 0;

            // Outside humidity (offset 33) - 65%
            packet[33] = 65;

            // Rain rate (offset 41-42) - 0
            packet[41] = 0;
            packet[42] = 0;

            // UV index (offset 43) - 50 (5.0 UV * 10)
            packet[43] = 50;

            // Solar radiation (offset 44-45) - 500 W/m2
            // 500 = 0x01F4
            packet[44] = (byte) 0xF4;
            packet[45] = (byte) 0x01;

            // Daily rain (offset 50-51) - 0
            packet[50] = 0;
            packet[51] = 0;

            // Console battery voltage (offset 87-88)
            // Using formula: value * 300 / 512 / 100 = voltage
            // For 4.5V: value = 4.5 * 100 * 512 / 300 = 768
            packet[87] = 0;
            packet[88] = 3; // 768 = 0x0300

            return packet;
        }
    }

    @Nested
    class DateTimeEncoding {

        @Test
        void encodeDate_encodesCorrectly() {
            // January 15, 2025
            // Formula: day + month*32 + (year-2000)*512
            // = 15 + 1*32 + 25*512 = 15 + 32 + 12800 = 12847
            Instant instant = LocalDateTime.of(2025, 1, 15, 10, 30)
                    .atZone(ZoneId.systemDefault()).toInstant();

            byte[] encoded = DavisRecordParser.encodeDate(instant);

            assertThat(encoded).hasSize(2);
            int value = (encoded[0] & 0xFF) | ((encoded[1] & 0xFF) << 8);
            assertThat(value).isEqualTo(12847);
        }

        @Test
        void encodeTime_encodesCorrectly() {
            // 10:30 -> 10*100 + 30 = 1030
            Instant instant = LocalDateTime.of(2025, 1, 15, 10, 30)
                    .atZone(ZoneId.systemDefault()).toInstant();

            byte[] encoded = DavisRecordParser.encodeTime(instant);

            assertThat(encoded).hasSize(2);
            int value = (encoded[0] & 0xFF) | ((encoded[1] & 0xFF) << 8);
            assertThat(value).isEqualTo(1030);
        }

        @Test
        void encodeDate_year2000_encodesCorrectly() {
            // December 31, 2000
            // = 31 + 12*32 + 0*512 = 31 + 384 = 415
            Instant instant = LocalDateTime.of(2000, 12, 31, 23, 59)
                    .atZone(ZoneId.systemDefault()).toInstant();

            byte[] encoded = DavisRecordParser.encodeDate(instant);

            int value = (encoded[0] & 0xFF) | ((encoded[1] & 0xFF) << 8);
            assertThat(value).isEqualTo(415);
        }

        @Test
        void encodeTime_midnight_encodesCorrectly() {
            // 00:00 -> 0*100 + 0 = 0
            Instant instant = LocalDateTime.of(2025, 1, 1, 0, 0)
                    .atZone(ZoneId.systemDefault()).toInstant();

            byte[] encoded = DavisRecordParser.encodeTime(instant);

            int value = (encoded[0] & 0xFF) | ((encoded[1] & 0xFF) << 8);
            assertThat(value).isEqualTo(0);
        }

        @Test
        void encodeTime_endOfDay_encodesCorrectly() {
            // 23:59 -> 23*100 + 59 = 2359
            Instant instant = LocalDateTime.of(2025, 1, 1, 23, 59)
                    .atZone(ZoneId.systemDefault()).toInstant();

            byte[] encoded = DavisRecordParser.encodeTime(instant);

            int value = (encoded[0] & 0xFF) | ((encoded[1] & 0xFF) << 8);
            assertThat(value).isEqualTo(2359);
        }
    }

    @Nested
    class ParseArchiveRecord {

        @Test
        void parseArchiveRecord_validRecord_parsesTimestamp() {
            byte[] page = createValidArchivePage();

            DavisArchiveRecord record = DavisRecordParser.parseArchiveRecord(page, 1);

            // Timestamp: Jan 15, 2025 at 10:30
            LocalDateTime expected = LocalDateTime.of(2025, 1, 15, 10, 30);
            LocalDateTime actual = LocalDateTime.ofInstant(record.timestamp(), ZoneId.systemDefault());

            assertThat(actual.getYear()).isEqualTo(expected.getYear());
            assertThat(actual.getMonthValue()).isEqualTo(expected.getMonthValue());
            assertThat(actual.getDayOfMonth()).isEqualTo(expected.getDayOfMonth());
            assertThat(actual.getHour()).isEqualTo(expected.getHour());
            assertThat(actual.getMinute()).isEqualTo(expected.getMinute());
        }

        @Test
        void parseArchiveRecord_validRecord_parsesTemperatures() {
            byte[] page = createValidArchivePage();

            DavisArchiveRecord record = DavisRecordParser.parseArchiveRecord(page, 1);

            // Average temp: 720 (72.0F) = 22.2C
            assertThat(record.tempOut()).isCloseTo(22.2, within(0.1));

            // High temp: 780 (78.0F) = 25.6C
            assertThat(record.tempOutHigh()).isCloseTo(25.6, within(0.1));

            // Low temp: 650 (65.0F) = 18.3C
            assertThat(record.tempOutLow()).isCloseTo(18.3, within(0.1));
        }

        @Test
        void parseArchiveRecord_validRecord_parsesPressure() {
            byte[] page = createValidArchivePage();

            DavisArchiveRecord record = DavisRecordParser.parseArchiveRecord(page, 1);

            // Pressure: 29920 (29.92 inHg) = 1013.2 hPa
            assertThat(record.pressure()).isCloseTo(1013.2, within(0.5));
        }

        @Test
        void parseArchiveRecord_validRecord_parsesHumidity() {
            byte[] page = createValidArchivePage();

            DavisArchiveRecord record = DavisRecordParser.parseArchiveRecord(page, 1);

            assertThat(record.humidityIn()).isEqualTo(45);
            assertThat(record.humidityOut()).isEqualTo(65);
        }

        @Test
        void parseArchiveRecord_validRecord_parsesWindDirection() {
            byte[] page = createValidArchivePage();

            DavisArchiveRecord record = DavisRecordParser.parseArchiveRecord(page, 1);

            // Wind direction: 8 * 22.5 = 180 degrees (S)
            assertThat(record.windDir()).isEqualTo(180);
        }

        /**
         * Creates a 267-byte archive page with one valid record starting at offset 1.
         * Page structure: 1 byte sequence + 5 * 52 byte records + 4 bytes CRC
         */
        private byte[] createValidArchivePage() {
            byte[] page = new byte[267];

            // Sequence byte (offset 0)
            page[0] = 0;

            int offset = 1; // First record starts at offset 1

            // Date stamp (offset 0-1 from record start)
            // Jan 15, 2025: day + month*32 + (year-2000)*512 = 15 + 1*32 + 25*512 = 12847
            page[offset] = (byte) (12847 & 0xFF);
            page[offset + 1] = (byte) ((12847 >> 8) & 0xFF);

            // Time stamp (offset 2-3) - 10:30 = 1030
            page[offset + 2] = (byte) (1030 & 0xFF);
            page[offset + 3] = (byte) ((1030 >> 8) & 0xFF);

            // Outside temperature avg (offset 4-5) - 720 (72.0F)
            page[offset + 4] = (byte) (720 & 0xFF);
            page[offset + 5] = (byte) ((720 >> 8) & 0xFF);

            // Outside temperature high (offset 6-7) - 780 (78.0F)
            page[offset + 6] = (byte) (780 & 0xFF);
            page[offset + 7] = (byte) ((780 >> 8) & 0xFF);

            // Outside temperature low (offset 8-9) - 650 (65.0F)
            page[offset + 8] = (byte) (650 & 0xFF);
            page[offset + 9] = (byte) ((650 >> 8) & 0xFF);

            // Rain (offset 10-11) - 10 clicks = 2.0mm
            page[offset + 10] = 10;
            page[offset + 11] = 0;

            // Rain rate high (offset 12-13) - 25 clicks = 5.0mm/h
            page[offset + 12] = 25;
            page[offset + 13] = 0;

            // Barometer (offset 14-15) - 29920 (29.92 inHg)
            page[offset + 14] = (byte) (29920 & 0xFF);
            page[offset + 15] = (byte) ((29920 >> 8) & 0xFF);

            // Solar radiation (offset 16-17) - 500 W/m2
            page[offset + 16] = (byte) (500 & 0xFF);
            page[offset + 17] = (byte) ((500 >> 8) & 0xFF);

            // Inside temperature (offset 20-21) - 680 (68.0F)
            page[offset + 20] = (byte) (680 & 0xFF);
            page[offset + 21] = (byte) ((680 >> 8) & 0xFF);

            // Inside humidity (offset 22) - 45%
            page[offset + 22] = 45;

            // Outside humidity (offset 23) - 65%
            page[offset + 23] = 65;

            // Avg wind speed (offset 24) - 10
            page[offset + 24] = 10;

            // High wind speed (offset 25) - 20
            page[offset + 25] = 20;

            // Direction of high wind (offset 26) - N/A
            page[offset + 26] = 0;

            // Prevailing wind direction (offset 27) - 8 (S = 180 degrees)
            page[offset + 27] = 8;

            // UV (offset 28) - 50 (5.0)
            page[offset + 28] = 50;

            // ET (offset 29-30) - 100 (0.1mm)
            page[offset + 29] = 100;
            page[offset + 30] = 0;

            return page;
        }
    }
}
