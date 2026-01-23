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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Parser for Davis Vantage Pro binary records.
 * <p>
 * Handles byte offset parsing and unit conversions from Davis native format
 * (Fahrenheit, inHg, mph) to metric units (Celsius, hPa, km/h).
 */
public final class DavisRecordParser {

    // Invalid value markers
    private static final int INVALID_BYTE = 0xFF;        // -1 as signed byte
    private static final int INVALID_WORD = 0x7FFF;      // 32767

    private DavisRecordParser() {
        // Static utility class
    }

    /**
     * Parse a 99-byte LOOP packet into a DavisLoopRecord.
     * <p>
     * LOOP packet structure (key offsets):
     * - 0-2: "LOO" signature
     * - 3: barometer trend
     * - 4: packet type (0=LOOP, 1=LOOP2)
     * - 5-6: next archive record pointer
     * - 7-8: barometer
     * - 9-10: inside temperature
     * - 11: inside humidity
     * - 12-13: outside temperature
     * - 14: wind gust (10-min)
     * - 15: wind speed
     * - 16-17: wind direction
     * - 33: outside humidity
     * - 41-42: rain rate
     * - 43: UV
     * - 44-45: solar radiation
     * - 50-51: day rain
     * - 87-88: console battery voltage
     *
     * @param buf 99-byte LOOP packet (CRC already validated)
     * @return parsed record
     */
    public static DavisLoopRecord parseLoopRecord(byte[] buf) {
        DavisLoopRecord.Builder builder = DavisLoopRecord.builder();

        // Barometer trend (-60 falling, 0 steady, +60 rising)
        builder.barometerTrend(buf[3]);

        // Next archive record pointer
        builder.nextRecord(parseWord(buf, 5));

        // Barometer in hPa (stored as inHg * 1000)
        builder.pressure(parseBarometer(buf, 7));

        // Inside temperature
        builder.tempIn(fahrenheitToCelsius(parseTempF(buf, 9)));

        // Inside humidity
        int humidityIn = buf[11] & 0xFF;
        builder.humidityIn(humidityIn == INVALID_BYTE ? 0 : humidityIn);

        // Outside temperature
        builder.tempOut(fahrenheitToCelsius(parseTempF(buf, 12)));

        // Wind gust (10-min high)
        builder.windGust(mphToKph(parseWindSpeedMph(buf, 14)));

        // Wind speed
        builder.windSpeed(mphToKph(parseWindSpeedMph(buf, 15)));

        // Wind direction
        int windDir = parseWord(buf, 16);
        builder.windDir(windDir == INVALID_WORD ? 0 : windDir);

        // Outside humidity
        int humidityOut = buf[33] & 0xFF;
        builder.humidityOut(humidityOut == INVALID_BYTE ? 0 : humidityOut);

        // Rain rate in mm/h
        builder.rainRate(parseRain(buf, 41));

        // UV index (stored as UV * 10)
        int uv = buf[43] & 0xFF;
        builder.uvIndex(uv == INVALID_BYTE ? 0 : uv / 10.0);

        // Solar radiation
        int solar = parseWord(buf, 44);
        builder.solarRadiation(solar == INVALID_WORD ? 0 : solar);

        // Day rain
        builder.rainDaily(parseRain(buf, 50));

        // Console battery voltage (formula: value * 300 / 512 / 100)
        int batteryRaw = parseWord(buf, 87);
        builder.consoleBattery(round((batteryRaw * 300.0) / 512.0 / 100.0));

        return builder.build();
    }

    /**
     * Parse a 52-byte archive record from a page buffer.
     * <p>
     * Archive record structure (key offsets from record start):
     * - 0-1: date stamp
     * - 2-3: time stamp
     * - 4-5: outside temperature
     * - 6-7: high outside temperature
     * - 8-9: low outside temperature
     * - 10-11: rainfall
     * - 12-13: high rain rate
     * - 14-15: barometer
     * - 16-17: solar radiation
     * - 20-21: inside temperature
     * - 22: inside humidity
     * - 23: outside humidity
     * - 24: average wind speed
     * - 25: high wind speed
     * - 26: direction of high wind speed
     * - 27: prevailing wind direction
     * - 28: UV index
     * - 29-30: ET
     *
     * @param page   267-byte page buffer
     * @param offset offset to start of record within page
     * @return parsed archive record
     */
    public static DavisArchiveRecord parseArchiveRecord(byte[] page, int offset) {
        DavisArchiveRecord.Builder builder = DavisArchiveRecord.builder();

        // Timestamp
        builder.timestamp(parseTimestamp(page, offset));

        // Outside temperature (avg, high, low)
        builder.tempOut(fahrenheitToCelsius(parseTempF(page, offset + 4)));
        builder.tempOutHigh(fahrenheitToCelsius(parseTempF(page, offset + 6)));
        builder.tempOutLow(fahrenheitToCelsius(parseTempF(page, offset + 8)));

        // Rain
        builder.rain(parseRain(page, offset + 10));
        builder.rainRate(parseRain(page, offset + 12));

        // Barometer
        builder.pressure(parseBarometer(page, offset + 14));

        // Solar radiation
        int solar = parseWord(page, offset + 16);
        builder.solarRadiation(solar == INVALID_WORD ? 0 : solar);

        // Inside temperature
        builder.tempIn(fahrenheitToCelsius(parseTempF(page, offset + 20)));

        // Humidity
        int humidityIn = page[offset + 22] & 0xFF;
        builder.humidityIn(humidityIn == INVALID_BYTE ? 0 : humidityIn);

        int humidityOut = page[offset + 23] & 0xFF;
        builder.humidityOut(humidityOut == INVALID_BYTE ? 0 : humidityOut);

        // Wind
        builder.windSpeed(mphToKph(parseWindSpeedMph(page, offset + 24)));
        builder.windGust(mphToKph(parseWindSpeedMph(page, offset + 25)));
        builder.windGustDir(page[offset + 26] & 0xFF);

        // Wind direction: 16 compass points to degrees
        int windDirRaw = page[offset + 27] & 0xFF;
        builder.windDir((int) (windDirRaw * 22.5));

        // UV
        int uv = page[offset + 28] & 0xFF;
        builder.uvIndex(uv == INVALID_BYTE ? 0 : uv / 10.0);

        // ET (evapotranspiration)
        int et = parseWord(page, offset + 29);
        builder.et(et / 1000.0);

        // High solar radiation (Rev B field at offset 30-31)
        int solarHigh = parseWord(page, offset + 30);
        builder.solarRadHigh(solarHigh == INVALID_WORD ? 0 : solarHigh);

        // High UV (Rev B field at offset 32-33)
        int uvHigh = parseWord(page, offset + 32);
        builder.uvIndexHigh(uvHigh == INVALID_WORD ? 0 : uvHigh / 10.0);

        return builder.build();
    }

    // ==================== Primitive Parsers ====================

    /**
     * Parse a 16-bit little-endian word.
     */
    public static int parseWord(byte[] buf, int offset) {
        int low = buf[offset] & 0xFF;
        int high = buf[offset + 1] & 0xFF;
        return (high << 8) | low;
    }

    /**
     * Parse temperature in Fahrenheit (stored as F * 10).
     */
    private static double parseTempF(byte[] buf, int offset) {
        int raw = parseWord(buf, offset);
        if (raw == INVALID_WORD) {
            return 0.0;
        }
        return raw / 10.0;
    }

    /**
     * Parse barometer in hPa (stored as inHg * 1000).
     */
    private static double parseBarometer(byte[] buf, int offset) {
        int raw = parseWord(buf, offset);
        double inchHg = raw / 1000.0;
        return inchHgToHPa(inchHg);
    }

    /**
     * Parse rain in mm (stored as clicks, 0.2mm per click).
     */
    private static double parseRain(byte[] buf, int offset) {
        int clicks = parseWord(buf, offset);
        return round(clicks * 0.2);
    }

    /**
     * Parse wind speed in mph.
     */
    private static double parseWindSpeedMph(byte[] buf, int offset) {
        int mph = buf[offset] & 0xFF;
        // Davis stores 0.45 factor for some reason in the reference code
        return mph != INVALID_BYTE ? mph * 0.45 : 0;
    }

    /**
     * Parse Davis timestamp (date + time words) into Instant.
     * Date format: day + month*32 + (year-2000)*512
     * Time format: hour*100 + minute
     */
    private static Instant parseTimestamp(byte[] buf, int offset) {
        int dateWord = parseWord(buf, offset);
        int timeWord = parseWord(buf, offset + 2);

        int year = ((dateWord >>> 9) & 0x7F) + 2000;
        int month = (dateWord >>> 5) & 0x0F;
        int day = dateWord & 0x1F;

        int hour = timeWord / 100;
        int minute = timeWord % 100;

        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute);
        return ldt.atZone(ZoneId.systemDefault()).toInstant();
    }

    // ==================== Unit Conversions ====================

    /**
     * Convert Fahrenheit to Celsius.
     */
    public static double fahrenheitToCelsius(double f) {
        return round((f - 32) * 5 / 9.0);
    }

    /**
     * Convert inches of mercury to hectopascals.
     */
    public static double inchHgToHPa(double inchHg) {
        return round(inchHg / 0.02953007);
    }

    /**
     * Convert miles per hour to kilometers per hour.
     */
    public static double mphToKph(double mph) {
        return round(mph * 1.609344);
    }

    /**
     * Round to 1 decimal place.
     */
    public static double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    // ==================== Date Encoding (for DMPAFT) ====================

    /**
     * Encode a date into Davis format for DMPAFT command.
     * Format: day + month*32 + (year-2000)*512
     *
     * @param instant the timestamp to encode
     * @return 2-byte date stamp (LSB first)
     */
    public static byte[] encodeDate(Instant instant) {
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        int year = ldt.getYear();
        int month = ldt.getMonthValue();
        int day = ldt.getDayOfMonth();
        int value = day + month * 32 + (year - 2000) * 512;
        return getBytes(value);
    }

    /**
     * Encode a time into Davis format for DMPAFT command.
     * Format: hour*100 + minute
     *
     * @param instant the timestamp to encode
     * @return 2-byte time stamp (LSB first)
     */
    public static byte[] encodeTime(Instant instant) {
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        int hour = ldt.getHour();
        int minute = ldt.getMinute();
        int value = hour * 100 + minute;
        return getBytes(value);
    }

    /**
     * Convert integer to 2-byte little-endian array.
     */
    private static byte[] getBytes(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >>> 8) & 0xFF)
        };
    }
}
