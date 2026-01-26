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

import com.owl.adapter.davis.protocol.CRC16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Simulated serial connection for Davis Vantage Pro.
 * <p>
 * Replays LOOP packets every 2.5 seconds and generates archive records
 * every 5 minutes for development and testing without requiring actual hardware.
 * Use by setting serial-port to "SIMULATED" in the configuration.
 * <p>
 * Supports full DMPAFT archive download simulation.
 */
public class SimulatedDavisConnection extends DavisSerialConnection {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedDavisConnection.class);

    private static final long LOOP_INTERVAL_MS = 2500;      // Davis sends LOOP every 2.5 seconds
    private static final long ARCHIVE_INTERVAL_MS = 300000; // Archive record every 5 minutes (300 seconds)

    // Protocol constants
    private static final byte ACK = 0x06;
    private static final int ARCHIVE_PAGE_SIZE = 267;
    private static final int ARCHIVE_RECORD_SIZE = 52;
    private static final int RECORDS_PER_PAGE = 5;

    // Simulated weather state (with slight random variations)
    private final Random random = new Random();
    private double baseOutTemp = 18.5;      // °C base, will vary
    private double baseInTemp = 21.0;       // °C
    private int baseHumidityOut = 65;       // %
    private int baseHumidityIn = 45;        // %
    private double basePressure = 1013.25;  // hPa
    private double baseWindSpeed = 8.0;     // km/h
    private int baseWindDir = 225;          // degrees
    private int baseSolarRad = 350;         // W/m²

    // Archive tracking
    private volatile int nextArchiveRecord = 100;
    private volatile Instant lastArchiveTime;
    private volatile Instant simulationStartTime;

    // DMPAFT state machine
    private enum DmpaftState { IDLE, AWAITING_DATETIME, SENDING_HEADER, SENDING_PAGES }
    private volatile DmpaftState dmpaftState = DmpaftState.IDLE;
    private volatile Instant dmpaftFromTime;
    private volatile int dmpaftPageIndex;
    private volatile int dmpaftTotalPages;
    private final BlockingQueue<byte[]> readQueue = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService scheduler;
    private Consumer<byte[]> dataCallback;
    private volatile boolean connected = false;
    private ScheduledFuture<?> loopTask;
    private ScheduledFuture<?> archiveTask;

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
        simulationStartTime = Instant.now();
        lastArchiveTime = simulationStartTime;

        // Start archive interval timer
        archiveTask = scheduler.scheduleAtFixedRate(this::onArchiveInterval,
                ARCHIVE_INTERVAL_MS, ARCHIVE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        LOG.info("Simulated Davis connection ready - LOOP every 2.5s, archive every 5min");
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (!connected) {
            throw new IOException("Simulated connection not open");
        }

        // Handle DMPAFT date/time data (6 bytes: date + time + CRC)
        if (dmpaftState == DmpaftState.AWAITING_DATETIME && data.length >= 2) {
            handleDmpaftDateTime(data);
            return;
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
        if (!connected) {
            throw new IOException("Simulated connection not open");
        }

        // Handle ACK during DMPAFT - send next page
        if (b == ACK && dmpaftState == DmpaftState.SENDING_PAGES) {
            sendNextArchivePage();
        }
    }

    @Override
    public byte[] readBlocking(int length, int timeoutMs) throws IOException {
        if (!connected) {
            throw new IOException("Simulated connection not open");
        }

        try {
            byte[] data = readQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (data == null) {
                throw new IOException("Read timeout waiting for " + length + " bytes");
            }
            // Return requested length (or full data if shorter request)
            if (data.length >= length) {
                byte[] result = new byte[length];
                System.arraycopy(data, 0, result, 0, length);
                return result;
            }
            return data;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Read interrupted", e);
        }
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
        if (archiveTask != null) {
            archiveTask.cancel(false);
            archiveTask = null;
        }
        scheduler.shutdownNow();
        connected = false;
    }

    // ==================== Command Processing ====================

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
        } else if (cmd.equals("DMPAFT")) {
            handleDmpaftCommand();
        }
    }

    // ==================== LOOP Packet Generation ====================

    private void startLooping() {
        stopLooping();
        LOG.info("Starting simulated LOOP mode - replaying packets every {}ms", LOOP_INTERVAL_MS);

        loopTask = scheduler.scheduleAtFixedRate(() -> {
            if (dataCallback != null && connected) {
                byte[] packet = buildLoopPacket();
                LOG.debug("Sending simulated LOOP packet ({} bytes)", packet.length);
                dataCallback.accept(packet);
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
     * Build a LOOP packet with current simulated weather data.
     */
    private byte[] buildLoopPacket() {
        byte[] packet = new byte[99];

        // Signature "LOO"
        packet[0] = 'L';
        packet[1] = 'O';
        packet[2] = 'O';

        // Barometer trend (0 = steady)
        packet[3] = 0;

        // Packet type (0 = LOOP)
        packet[4] = 0;

        // Next archive record pointer (little-endian)
        packet[5] = (byte) (nextArchiveRecord & 0xFF);
        packet[6] = (byte) ((nextArchiveRecord >> 8) & 0xFF);

        // Barometer in inHg * 1000 (e.g., 29.92 inHg = 29920)
        // Convert from hPa: inHg = hPa * 0.02953
        double pressure = basePressure + randomVariation(2.0);
        int baroRaw = (int) (pressure * 0.02953 * 1000);
        packet[7] = (byte) (baroRaw & 0xFF);
        packet[8] = (byte) ((baroRaw >> 8) & 0xFF);

        // Inside temperature in F * 10
        double inTemp = baseInTemp + randomVariation(0.5);
        int inTempRaw = (int) (celsiusToFahrenheit(inTemp) * 10);
        packet[9] = (byte) (inTempRaw & 0xFF);
        packet[10] = (byte) ((inTempRaw >> 8) & 0xFF);

        // Inside humidity
        packet[11] = (byte) Math.max(0, Math.min(100, baseHumidityIn + (int) randomVariation(3)));

        // Outside temperature in F * 10
        double outTemp = baseOutTemp + randomVariation(1.0);
        int outTempRaw = (int) (celsiusToFahrenheit(outTemp) * 10);
        packet[12] = (byte) (outTempRaw & 0xFF);
        packet[13] = (byte) ((outTempRaw >> 8) & 0xFF);

        // Wind gust (stored as mph / 0.45)
        double windGust = baseWindSpeed * 1.5 + randomVariation(3.0);
        packet[14] = (byte) Math.max(0, (int) (kphToMph(windGust) / 0.45));

        // Wind speed (stored as mph / 0.45)
        double windSpeed = baseWindSpeed + randomVariation(2.0);
        packet[15] = (byte) Math.max(0, (int) (kphToMph(windSpeed) / 0.45));

        // Wind direction (little-endian)
        int windDir = (baseWindDir + (int) randomVariation(20) + 360) % 360;
        packet[16] = (byte) (windDir & 0xFF);
        packet[17] = (byte) ((windDir >> 8) & 0xFF);

        // Extra temps and soil/leaf (0xFF = invalid)
        for (int i = 18; i <= 32; i++) {
            packet[i] = (byte) 0xFF;
        }

        // Outside humidity
        packet[33] = (byte) Math.max(0, Math.min(100, baseHumidityOut + (int) randomVariation(5)));

        // Extra humidities (0xFF = invalid)
        for (int i = 34; i <= 40; i++) {
            packet[i] = (byte) 0xFF;
        }

        // Rain rate (0 = no rain)
        packet[41] = 0;
        packet[42] = 0;

        // UV index * 10
        packet[43] = (byte) Math.max(0, 25 + (int) randomVariation(10));

        // Solar radiation (little-endian)
        int solar = Math.max(0, baseSolarRad + (int) randomVariation(50));
        packet[44] = (byte) (solar & 0xFF);
        packet[45] = (byte) ((solar >> 8) & 0xFF);

        // Storm rain, start date (0)
        for (int i = 46; i <= 49; i++) {
            packet[i] = 0;
        }

        // Day/month/year rain (0)
        for (int i = 50; i <= 55; i++) {
            packet[i] = 0;
        }

        // ET values (0)
        for (int i = 56; i <= 61; i++) {
            packet[i] = 0;
        }

        // Soil moistures (0xFF)
        for (int i = 62; i <= 65; i++) {
            packet[i] = (byte) 0xFF;
        }

        // Leaf wetnesses (0xFF)
        for (int i = 66; i <= 69; i++) {
            packet[i] = (byte) 0xFF;
        }

        // Alarms (0)
        for (int i = 70; i <= 85; i++) {
            packet[i] = 0;
        }

        // Transmitter battery status (0 = OK)
        packet[86] = 0;

        // Console battery voltage (768 = ~4.5V)
        packet[87] = 0x00;
        packet[88] = 0x03;

        // Forecast icons and rule (0)
        packet[89] = 0;
        packet[90] = 0;

        // Sunrise (6:00 AM = 600)
        packet[91] = 0x58;
        packet[92] = 0x02;

        // Sunset (6:00 PM = 1800)
        packet[93] = 0x08;
        packet[94] = 0x07;

        // LF CR
        packet[95] = 0x0A;
        packet[96] = 0x0D;

        // Calculate and set CRC (bytes 97-98)
        byte[] crc = CRC16.calculate(java.util.Arrays.copyOf(packet, 97));
        packet[97] = crc[0];
        packet[98] = crc[1];

        return packet;
    }

    // ==================== Archive Generation ====================

    /**
     * Called every 5 minutes to simulate a new archive record.
     */
    private void onArchiveInterval() {
        nextArchiveRecord = (nextArchiveRecord + 1) % 2560; // Davis has 2560 archive records
        lastArchiveTime = Instant.now();
        LOG.info("Archive interval: new record pointer = {}", nextArchiveRecord);

        // Slowly drift the base weather values for realism
        baseOutTemp += randomVariation(0.2);
        baseOutTemp = Math.max(-10, Math.min(40, baseOutTemp));
        basePressure += randomVariation(0.5);
        basePressure = Math.max(980, Math.min(1040, basePressure));
        baseWindDir = (baseWindDir + (int) randomVariation(10) + 360) % 360;
    }

    // ==================== DMPAFT Command Handling ====================

    private void handleDmpaftCommand() {
        LOG.info("Received DMPAFT command");
        dmpaftState = DmpaftState.AWAITING_DATETIME;

        // Send ACK
        readQueue.offer(new byte[]{ACK});
    }

    private void handleDmpaftDateTime(byte[] data) {
        // Date/time received (with CRC), validate and prepare archive download
        LOG.debug("Received DMPAFT date/time ({} bytes)", data.length);

        // Parse the date to determine how many records to send
        // For simulation, we'll just send 1 page with 1 record (the latest)
        dmpaftFromTime = Instant.now().minusSeconds(300); // Last 5 minutes
        dmpaftTotalPages = 1;
        dmpaftPageIndex = 0;

        // Send ACK
        readQueue.offer(new byte[]{ACK});

        // Send header (6 bytes: pages count + start index + CRC)
        byte[] header = new byte[6];
        // Number of pages (little-endian)
        header[0] = (byte) (dmpaftTotalPages & 0xFF);
        header[1] = (byte) ((dmpaftTotalPages >> 8) & 0xFF);
        // Start index (first record in first page)
        header[2] = 0;
        header[3] = 0;
        // CRC
        byte[] headerCrc = CRC16.calculate(new byte[]{header[0], header[1], header[2], header[3]});
        header[4] = headerCrc[0];
        header[5] = headerCrc[1];

        readQueue.offer(header);
        dmpaftState = DmpaftState.SENDING_PAGES;

        LOG.info("DMPAFT: sending {} page(s)", dmpaftTotalPages);
    }

    private void sendNextArchivePage() {
        if (dmpaftPageIndex >= dmpaftTotalPages) {
            LOG.info("DMPAFT: all pages sent");
            dmpaftState = DmpaftState.IDLE;
            return;
        }

        byte[] page = buildArchivePage(dmpaftPageIndex);
        readQueue.offer(page);
        dmpaftPageIndex++;

        LOG.debug("DMPAFT: sent page {}/{}", dmpaftPageIndex, dmpaftTotalPages);
    }

    /**
     * Build a 267-byte archive page with simulated records.
     */
    private byte[] buildArchivePage(int pageIndex) {
        byte[] page = new byte[ARCHIVE_PAGE_SIZE];

        // Byte 0: sequence number
        page[0] = (byte) pageIndex;

        // Build one record at offset 1 (we'll put empty markers for the rest)
        Instant recordTime = lastArchiveTime;
        buildArchiveRecord(page, 1, recordTime);

        // Mark remaining records as empty (0xFF in first byte)
        for (int rec = 1; rec < RECORDS_PER_PAGE; rec++) {
            int offset = 1 + (rec * ARCHIVE_RECORD_SIZE);
            page[offset] = (byte) 0xFF;
        }

        // Calculate CRC over bytes 0-262 and store in bytes 263-266
        byte[] dataForCrc = new byte[ARCHIVE_PAGE_SIZE - 4];
        System.arraycopy(page, 0, dataForCrc, 0, dataForCrc.length);
        byte[] crc = CRC16.calculate(dataForCrc);
        page[263] = crc[0];
        page[264] = crc[1];
        // Padding
        page[265] = 0;
        page[266] = 0;

        return page;
    }

    /**
     * Build a 52-byte archive record at the specified offset.
     */
    private void buildArchiveRecord(byte[] page, int offset, Instant timestamp) {
        LocalDateTime ldt = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());

        // Bytes 0-1: Date stamp (day + month*32 + (year-2000)*512)
        int dateStamp = ldt.getDayOfMonth() + ldt.getMonthValue() * 32 + (ldt.getYear() - 2000) * 512;
        page[offset] = (byte) (dateStamp & 0xFF);
        page[offset + 1] = (byte) ((dateStamp >> 8) & 0xFF);

        // Bytes 2-3: Time stamp (hour*100 + minute)
        int timeStamp = ldt.getHour() * 100 + ldt.getMinute();
        page[offset + 2] = (byte) (timeStamp & 0xFF);
        page[offset + 3] = (byte) ((timeStamp >> 8) & 0xFF);

        // Bytes 4-5: Outside temperature (F * 10)
        double outTemp = baseOutTemp + randomVariation(0.5);
        int outTempRaw = (int) (celsiusToFahrenheit(outTemp) * 10);
        page[offset + 4] = (byte) (outTempRaw & 0xFF);
        page[offset + 5] = (byte) ((outTempRaw >> 8) & 0xFF);

        // Bytes 6-7: High outside temperature
        int highTempRaw = (int) (celsiusToFahrenheit(outTemp + 1.0) * 10);
        page[offset + 6] = (byte) (highTempRaw & 0xFF);
        page[offset + 7] = (byte) ((highTempRaw >> 8) & 0xFF);

        // Bytes 8-9: Low outside temperature
        int lowTempRaw = (int) (celsiusToFahrenheit(outTemp - 1.0) * 10);
        page[offset + 8] = (byte) (lowTempRaw & 0xFF);
        page[offset + 9] = (byte) ((lowTempRaw >> 8) & 0xFF);

        // Bytes 10-11: Rainfall (clicks, 0.2mm per click)
        page[offset + 10] = 0;
        page[offset + 11] = 0;

        // Bytes 12-13: High rain rate
        page[offset + 12] = 0;
        page[offset + 13] = 0;

        // Bytes 14-15: Barometer (inHg * 1000)
        double pressure = basePressure + randomVariation(1.0);
        int baroRaw = (int) (pressure * 0.02953 * 1000);
        page[offset + 14] = (byte) (baroRaw & 0xFF);
        page[offset + 15] = (byte) ((baroRaw >> 8) & 0xFF);

        // Bytes 16-17: Solar radiation
        int solar = Math.max(0, baseSolarRad + (int) randomVariation(30));
        page[offset + 16] = (byte) (solar & 0xFF);
        page[offset + 17] = (byte) ((solar >> 8) & 0xFF);

        // Bytes 18-19: Number of wind samples
        page[offset + 18] = (byte) 120; // ~120 samples in 5 minutes
        page[offset + 19] = 0;

        // Bytes 20-21: Inside temperature (F * 10)
        double inTemp = baseInTemp + randomVariation(0.3);
        int inTempRaw = (int) (celsiusToFahrenheit(inTemp) * 10);
        page[offset + 20] = (byte) (inTempRaw & 0xFF);
        page[offset + 21] = (byte) ((inTempRaw >> 8) & 0xFF);

        // Byte 22: Inside humidity
        page[offset + 22] = (byte) Math.max(0, Math.min(100, baseHumidityIn + (int) randomVariation(2)));

        // Byte 23: Outside humidity
        page[offset + 23] = (byte) Math.max(0, Math.min(100, baseHumidityOut + (int) randomVariation(3)));

        // Byte 24: Average wind speed (mph / 0.45)
        double windSpeed = baseWindSpeed + randomVariation(1.5);
        page[offset + 24] = (byte) Math.max(0, (int) (kphToMph(windSpeed) / 0.45));

        // Byte 25: High wind speed
        double windGust = windSpeed * 1.3 + randomVariation(2.0);
        page[offset + 25] = (byte) Math.max(0, (int) (kphToMph(windGust) / 0.45));

        // Byte 26: Direction of high wind speed (0-15 compass points)
        page[offset + 26] = (byte) ((baseWindDir / 22.5) % 16);

        // Byte 27: Prevailing wind direction (0-15 compass points)
        page[offset + 27] = (byte) ((baseWindDir / 22.5) % 16);

        // Byte 28: UV index * 10
        page[offset + 28] = (byte) Math.max(0, 25 + (int) randomVariation(5));

        // Bytes 29-30: ET (evapotranspiration in 1/1000 inch)
        page[offset + 29] = 0;
        page[offset + 30] = 0;

        // Bytes 30-31: High solar radiation (Rev B) - overlaps with ET byte 30
        // For simplicity, set high solar same as average
        page[offset + 30] = (byte) (solar & 0xFF);
        page[offset + 31] = (byte) ((solar >> 8) & 0xFF);

        // Bytes 32-33: High UV * 10 (Rev B)
        page[offset + 32] = page[offset + 28]; // Same as average
        page[offset + 33] = 0;

        // Remaining bytes (34-51): leaf/soil temps, humidities, etc. - set to 0xFF or 0
        for (int i = 34; i < 52; i++) {
            page[offset + i] = (byte) 0xFF;
        }
    }

    // ==================== Utility Methods ====================

    private double randomVariation(double range) {
        return (random.nextDouble() - 0.5) * 2 * range;
    }

    private double celsiusToFahrenheit(double c) {
        return c * 9.0 / 5.0 + 32;
    }

    private double kphToMph(double kph) {
        return kph / 1.609344;
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
