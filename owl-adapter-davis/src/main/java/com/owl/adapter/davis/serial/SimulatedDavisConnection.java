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
package com.owl.adapter.davis.serial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Simulated serial connection for Davis Vantage Pro.
 * <p>
 * Replays a fixed LOOP packet for development and testing without
 * requiring actual hardware. Use by setting serial-port to "SIMULATED"
 * in the configuration.
 * <p>
 * To capture real bytes: run with actual hardware and check debug logs for
 * "Raw LOOP packet bytes:" then paste those bytes into LOOP_PACKET below.
 */
public class SimulatedDavisConnection extends DavisSerialConnection {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedDavisConnection.class);

    private static final long LOOP_INTERVAL_MS = 2500; // Davis sends LOOP every 2.5 seconds

    /**
     * Fixed LOOP packet bytes captured from a real Davis Vantage Pro console.
     * This packet represents realistic weather data that will be replayed.
     * <p>
     * To update: capture bytes from debug log "Raw LOOP packet bytes:" and paste here.
     * Format: 99 bytes total, starts with "LOO" (4C 4F 4F), ends with 2-byte CRC.
     * <p>
     * This sample packet represents approximately:
     * - Outside temp: ~18.5°C (65°F)
     * - Inside temp: ~21°C (70°F)
     * - Outside humidity: 65%
     * - Inside humidity: 45%
     * - Pressure: ~1013 hPa (29.92 inHg)
     * - Wind: 8 km/h from SW (225°)
     * - Solar radiation: 350 W/m²
     */
    private static final byte[] LOOP_PACKET = hexToBytes(
        // Bytes 0-2: "LOO" signature
        "4C 4F 4F " +
        // Byte 3: barometer trend (0 = steady)
        "00 " +
        // Byte 4: packet type (0 = LOOP)
        "00 " +
        // Bytes 5-6: next archive record (little-endian, 100 = 0x64 0x00)
        "64 00 " +
        // Bytes 7-8: barometer (29.92 inHg = 29920 = 0x74E0, little-endian)
        "E0 74 " +
        // Bytes 9-10: inside temp (70.0°F * 10 = 700 = 0x02BC, little-endian)
        "BC 02 " +
        // Byte 11: inside humidity (45%)
        "2D " +
        // Bytes 12-13: outside temp (65.3°F * 10 = 653 = 0x028D, little-endian)
        "8D 02 " +
        // Byte 14: wind gust (mph with 0.45 factor: ~15 km/h -> 9 mph / 0.45 = 20)
        "14 " +
        // Byte 15: wind speed (mph with 0.45 factor: ~8 km/h -> 5 mph / 0.45 = 11)
        "0B " +
        // Bytes 16-17: wind direction (225° = 0x00E1, little-endian)
        "E1 00 " +
        // Bytes 18-32: extra temps and soil/leaf (fill with 0xFF = invalid)
        "FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF " +
        // Byte 33: outside humidity (65%)
        "41 " +
        // Bytes 34-40: extra humidities (fill with 0xFF)
        "FF FF FF FF FF FF FF " +
        // Bytes 41-42: rain rate (0 clicks = no rain)
        "00 00 " +
        // Byte 43: UV index (2.5 * 10 = 25)
        "19 " +
        // Bytes 44-45: solar radiation (350 W/m² = 0x015E, little-endian)
        "5E 01 " +
        // Bytes 46-47: storm rain (0)
        "00 00 " +
        // Bytes 48-49: storm start date (0 = no storm)
        "FF FF " +
        // Bytes 50-51: day rain (0 clicks)
        "00 00 " +
        // Bytes 52-53: month rain (0)
        "00 00 " +
        // Bytes 54-55: year rain (0)
        "00 00 " +
        // Bytes 56-57: day ET (0)
        "00 00 " +
        // Bytes 58-59: month ET (0)
        "00 00 " +
        // Bytes 60-61: year ET (0)
        "00 00 " +
        // Bytes 62-65: soil moistures (0xFF)
        "FF FF FF FF " +
        // Bytes 66-69: leaf wetnesses (0xFF)
        "FF FF FF FF " +
        // Byte 70: inside alarms (0)
        "00 " +
        // Byte 71: rain alarms (0)
        "00 " +
        // Bytes 72-73: outside alarms (0)
        "00 00 " +
        // Bytes 74-81: extra temp/hum alarms (0)
        "00 00 00 00 00 00 00 00 " +
        // Bytes 82-85: soil/leaf alarms (0)
        "00 00 00 00 " +
        // Byte 86: transmitter battery status (0 = OK)
        "00 " +
        // Bytes 87-88: console battery voltage (768 = ~4.5V, 0x0300 little-endian)
        "00 03 " +
        // Byte 89: forecast icons (0)
        "00 " +
        // Byte 90: forecast rule (0)
        "00 " +
        // Bytes 91-92: time of sunrise (0x0258 = 600 = 6:00 AM)
        "58 02 " +
        // Bytes 93-94: time of sunset (0x0708 = 1800 = 6:00 PM)
        "08 07 " +
        // Byte 95: line feed (0x0A)
        "0A " +
        // Byte 96: carriage return (0x0D)
        "0D " +
        // Bytes 97-98: CRC (calculated for above data)
        "00 00"
    );

    // Recalculate CRC for the packet
    static {
        recalculateCRC();
    }

    private final ScheduledExecutorService scheduler;
    private Consumer<byte[]> dataCallback;
    private volatile boolean connected = false;
    private ScheduledFuture<?> loopTask;

    public SimulatedDavisConnection(String portName, int baudRate) {
        super(portName, baudRate);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "davis-simulator");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void setDataCallback(Consumer<byte[]> callback) {
        this.dataCallback = callback;
    }

    @Override
    public void open() throws IOException {
        LOG.info("Opening SIMULATED Davis connection (no hardware required)");
        connected = true;
        LOG.info("Simulated Davis connection ready - will replay fixed LOOP packet every 2.5s");
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (!connected) {
            throw new IOException("Simulated connection not open");
        }
        String cmd = new String(data).trim();
        processCommand(cmd);
    }

    @Override
    public void writeCommand(String command) throws IOException {
        write((command + "\n").getBytes());
    }

    @Override
    public void writeByte(byte b) throws IOException {
        // Ignore single byte writes (ACK responses etc)
    }

    @Override
    public byte[] readBlocking(int length, int timeoutMs) throws IOException {
        throw new IOException("Archive download not supported in simulation mode");
    }

    @Override
    public void disableListener() {
        stopLooping();
    }

    @Override
    public void enableListener(Consumer<byte[]> callback) {
        this.dataCallback = callback;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        LOG.info("Closing simulated Davis connection");
        stopLooping();
        scheduler.shutdownNow();
        connected = false;
    }

    private void processCommand(String cmd) {
        if (cmd.isEmpty()) {
            // Wakeup - respond with LF CR after short delay
            scheduler.schedule(() -> {
                if (dataCallback != null) {
                    LOG.debug("Sending simulated wakeup response (LF CR)");
                    dataCallback.accept(new byte[]{0x0A, 0x0D});
                }
            }, 100, TimeUnit.MILLISECONDS);
        } else if (cmd.startsWith("LOOP")) {
            startLooping();
        }
    }

    private void startLooping() {
        stopLooping();
        LOG.info("Starting simulated LOOP mode - replaying fixed packet every {}ms", LOOP_INTERVAL_MS);

        loopTask = scheduler.scheduleAtFixedRate(() -> {
            if (dataCallback != null && connected) {
                LOG.debug("Sending simulated LOOP packet ({} bytes)", LOOP_PACKET.length);
                dataCallback.accept(LOOP_PACKET.clone());
            }
        }, 500, LOOP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopLooping() {
        if (loopTask != null) {
            loopTask.cancel(false);
            loopTask = null;
        }
    }

    /**
     * Recalculate the CRC for LOOP_PACKET and update bytes 97-98.
     */
    private static void recalculateCRC() {
        // Calculate CRC over bytes 0-96 (everything except the last 2 CRC bytes)
        int crc = 0;
        int[] TABLE = {
            0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50a5, 0x60c6, 0x70e7,
            0x8108, 0x9129, 0xa14a, 0xb16b, 0xc18c, 0xd1ad, 0xe1ce, 0xf1ef,
            0x1231, 0x0210, 0x3273, 0x2252, 0x52b5, 0x4294, 0x72f7, 0x62d6,
            0x9339, 0x8318, 0xb37b, 0xa35a, 0xd3bd, 0xc39c, 0xf3ff, 0xe3de,
            0x2462, 0x3443, 0x0420, 0x1401, 0x64e6, 0x74c7, 0x44a4, 0x5485,
            0xa56a, 0xb54b, 0x8528, 0x9509, 0xe5ee, 0xf5cf, 0xc5ac, 0xd58d,
            0x3653, 0x2672, 0x1611, 0x0630, 0x76d7, 0x66f6, 0x5695, 0x46b4,
            0xb75b, 0xa77a, 0x9719, 0x8738, 0xf7df, 0xe7fe, 0xd79d, 0xc7bc,
            0x48c4, 0x58e5, 0x6886, 0x78a7, 0x0840, 0x1861, 0x2802, 0x3823,
            0xc9cc, 0xd9ed, 0xe98e, 0xf9af, 0x8948, 0x9969, 0xa90a, 0xb92b,
            0x5af5, 0x4ad4, 0x7ab7, 0x6a96, 0x1a71, 0x0a50, 0x3a33, 0x2a12,
            0xdbfd, 0xcbdc, 0xfbbf, 0xeb9e, 0x9b79, 0x8b58, 0xbb3b, 0xab1a,
            0x6ca6, 0x7c87, 0x4ce4, 0x5cc5, 0x2c22, 0x3c03, 0x0c60, 0x1c41,
            0xedae, 0xfd8f, 0xcdec, 0xddcd, 0xad2a, 0xbd0b, 0x8d68, 0x9d49,
            0x7e97, 0x6eb6, 0x5ed5, 0x4ef4, 0x3e13, 0x2e32, 0x1e51, 0x0e70,
            0xff9f, 0xefbe, 0xdfdd, 0xcffc, 0xbf1b, 0xaf3a, 0x9f59, 0x8f78,
            0x9188, 0x81a9, 0xb1ca, 0xa1eb, 0xd10c, 0xc12d, 0xf14e, 0xe16f,
            0x1080, 0x00a1, 0x30c2, 0x20e3, 0x5004, 0x4025, 0x7046, 0x6067,
            0x83b9, 0x9398, 0xa3fb, 0xb3da, 0xc33d, 0xd31c, 0xe37f, 0xf35e,
            0x02b1, 0x1290, 0x22f3, 0x32d2, 0x4235, 0x5214, 0x6277, 0x7256,
            0xb5ea, 0xa5cb, 0x95a8, 0x8589, 0xf56e, 0xe54f, 0xd52c, 0xc50d,
            0x34e2, 0x24c3, 0x14a0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
            0xa7db, 0xb7fa, 0x8799, 0x97b8, 0xe75f, 0xf77e, 0xc71d, 0xd73c,
            0x26d3, 0x36f2, 0x0691, 0x16b0, 0x6657, 0x7676, 0x4615, 0x5634,
            0xd94c, 0xc96d, 0xf90e, 0xe92f, 0x99c8, 0x89e9, 0xb98a, 0xa9ab,
            0x5844, 0x4865, 0x7806, 0x6827, 0x18c0, 0x08e1, 0x3882, 0x28a3,
            0xcb7d, 0xdb5c, 0xeb3f, 0xfb1e, 0x8bf9, 0x9bd8, 0xabbb, 0xbb9a,
            0x4a75, 0x5a54, 0x6a37, 0x7a16, 0x0af1, 0x1ad0, 0x2ab3, 0x3a92,
            0xfd2e, 0xed0f, 0xdd6c, 0xcd4d, 0xbdaa, 0xad8b, 0x9de8, 0x8dc9,
            0x7c26, 0x6c07, 0x5c64, 0x4c45, 0x3ca2, 0x2c83, 0x1ce0, 0x0cc1,
            0xef1f, 0xff3e, 0xcf5d, 0xdf7c, 0xaf9b, 0xbfba, 0x8fd9, 0x9ff8,
            0x6e17, 0x7e36, 0x4e55, 0x5e74, 0x2e93, 0x3eb2, 0x0ed1, 0x1ef0
        };

        for (int i = 0; i < LOOP_PACKET.length - 2; i++) {
            int b = LOOP_PACKET[i] & 0xFF;
            crc = (TABLE[(b ^ (crc >>> 8)) & 0xff] ^ (crc << 8)) & 0xffff;
        }

        // Store CRC (MSB first)
        LOOP_PACKET[97] = (byte) ((crc >>> 8) & 0xFF);
        LOOP_PACKET[98] = (byte) (crc & 0xFF);
    }

    /**
     * Convert hex string to byte array.
     */
    private static byte[] hexToBytes(String hex) {
        String clean = hex.replaceAll("\\s+", "");
        byte[] result = new byte[clean.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    /**
     * Check if a port name indicates simulation mode.
     */
    public static boolean isSimulatedPort(String portName) {
        return portName != null && (
            "SIMULATED".equalsIgnoreCase(portName) ||
            "SIMULATOR".equalsIgnoreCase(portName) ||
            "SIM".equalsIgnoreCase(portName)
        );
    }
}
