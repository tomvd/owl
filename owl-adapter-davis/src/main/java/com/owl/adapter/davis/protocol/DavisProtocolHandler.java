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

import com.owl.adapter.davis.serial.DavisSerialConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Protocol handler for Davis Vantage Pro communication.
 * <p>
 * Implements a state machine to manage the communication flow:
 * DISCONNECTED -> WAKING -> AWAKE -> LOOPING
 * <p>
 * The console must be woken up before any commands can be sent.
 * Once awake, the LOOP command is sent to receive continuous data packets.
 */
public class DavisProtocolHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DavisProtocolHandler.class);

    // Protocol constants
    public static final byte ACK = 0x06;
    public static final byte NAK = 0x21;
    public static final byte LF = 0x0A;
    public static final byte CR = 0x0D;

    public static final int LOOP_PACKET_SIZE = 99;
    public static final int ARCHIVE_PAGE_SIZE = 267;
    public static final int ARCHIVE_RECORD_SIZE = 52;
    public static final int RECORDS_PER_PAGE = 5;

    private static final int MAX_LOOP_RECORDS = 200;  // Request 200 LOOP packets (~8 minutes)
    private static final int WAKEUP_TIMEOUT_MS = 3000;
    private static final int WAKEUP_RETRY_MS = 1200;
    private static final int RING_BUFFER_SIZE = 2048;

    // State machine
    public enum State {
        DISCONNECTED,
        WAKING,
        AWAKE,
        LOOPING,
        ARCHIVING
    }

    private final DavisSerialConnection connection;
    private final RingBuffer buffer;
    private final ScheduledExecutorService scheduler;

    private volatile State state = State.DISCONNECTED;
    private volatile int wakeupAttempts = 0;
    private volatile Instant lastArchiveTime;

    // Callbacks
    private Consumer<DavisLoopRecord> loopRecordCallback;
    private Consumer<DavisArchiveRecord> archiveRecordCallback;
    private Consumer<State> stateChangeCallback;
    private Consumer<String> errorCallback;

    /**
     * Create a protocol handler for the given connection.
     *
     * @param connection serial connection to the Davis console
     */
    public DavisProtocolHandler(DavisSerialConnection connection) {
        this.connection = connection;
        this.buffer = new RingBuffer(RING_BUFFER_SIZE);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "davis-protocol");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Callbacks ====================

    public void setLoopRecordCallback(Consumer<DavisLoopRecord> callback) {
        this.loopRecordCallback = callback;
    }

    public void setArchiveRecordCallback(Consumer<DavisArchiveRecord> callback) {
        this.archiveRecordCallback = callback;
    }

    public void setStateChangeCallback(Consumer<State> callback) {
        this.stateChangeCallback = callback;
    }

    public void setErrorCallback(Consumer<String> callback) {
        this.errorCallback = callback;
    }

    // ==================== State Management ====================

    public State getState() {
        return state;
    }

    private void setState(State newState) {
        State oldState = this.state;
        this.state = newState;
        LOG.info("State: {} -> {}", oldState, newState);
        if (stateChangeCallback != null) {
            stateChangeCallback.accept(newState);
        }
    }

    // ==================== Protocol Flow ====================

    /**
     * Start the protocol handler. Opens the connection and begins wakeup sequence.
     *
     * @throws IOException if connection cannot be opened
     */
    public void start() throws IOException {
        if (!connection.isConnected()) {
            connection.open();
        }

        connection.setDataCallback(this::onDataReceived);
        wakeup();
    }

    /**
     * Stop the protocol handler.
     */
    public void stop() {
        setState(State.DISCONNECTED);
        scheduler.shutdownNow();
        connection.close();
        buffer.clear();
    }

    /**
     * Initiate wakeup sequence.
     */
    public void wakeup() {
        setState(State.WAKING);
        wakeupAttempts = 0;
        buffer.clear();
        sendWakeup();
    }

    private void sendWakeup() {
        try {
            LOG.debug("Sending wakeup (attempt {})", wakeupAttempts + 1);
            connection.writeCommand("");  // Just send newline

            // Schedule timeout check
            scheduler.schedule(() -> {
                if (state == State.WAKING) {
                    wakeupAttempts++;
                    if (wakeupAttempts < 3) {
                        LOG.info("Wakeup attempt {} failed, retrying...", wakeupAttempts);
                        sendWakeup();
                    } else {
                        LOG.error("Failed to wake console after {} attempts", wakeupAttempts);
                        if (errorCallback != null) {
                            errorCallback.accept("Failed to wake console");
                        }
                    }
                }
            }, wakeupAttempts == 0 ? WAKEUP_TIMEOUT_MS : WAKEUP_RETRY_MS, TimeUnit.MILLISECONDS);

        } catch (IOException e) {
            LOG.error("Error sending wakeup", e);
            if (errorCallback != null) {
                errorCallback.accept("Wakeup error: " + e.getMessage());
            }
        }
    }

    private void startLooping() {
        try {
            LOG.info("Starting LOOP mode with {} records", MAX_LOOP_RECORDS);
            connection.writeCommand("LOOP " + MAX_LOOP_RECORDS);
            setState(State.LOOPING);
        } catch (IOException e) {
            LOG.error("Error starting LOOP", e);
            if (errorCallback != null) {
                errorCallback.accept("LOOP error: " + e.getMessage());
            }
            // Try to recover
            scheduler.schedule(this::wakeup, 5, TimeUnit.SECONDS);
        }
    }

    // ==================== Data Processing ====================

    /**
     * Called when data is received from the serial port.
     */
    private void onDataReceived(byte[] data) {
        buffer.write(data);
        processBuffer();
    }

    private void processBuffer() {
        int iterations = 0;
        while (buffer.available() > 0 && iterations++ < 10) {
            byte first = buffer.peek(0);

            // ACK response
            if (first == ACK) {
                buffer.read(1);
                LOG.debug("Received ACK");
                continue;
            }

            // NAK response
            if (first == NAK) {
                buffer.read(1);
                LOG.warn("Received NAK");
                // Retry wakeup on NAK
                if (state == State.WAKING || state == State.AWAKE) {
                    wakeup();
                }
                continue;
            }

            // Wakeup response: LF CR
            if (buffer.available() >= 2 && first == LF && buffer.peek(1) == CR) {
                buffer.clear();  // Clear any garbage
                LOG.info("Console is awake");
                setState(State.AWAKE);
                startLooping();
                continue;
            }

            // LOOP packet: starts with "LOO"
            if (buffer.available() >= LOOP_PACKET_SIZE &&
                    first == 'L' && buffer.peek(1) == 'O' && buffer.peek(2) == 'O') {

                byte[] packet = buffer.read(LOOP_PACKET_SIZE);

                // Log raw packet bytes for debugging/simulation
                if (LOG.isDebugEnabled()) {
                    StringBuilder hex = new StringBuilder("Raw LOOP packet bytes: ");
                    for (int i = 0; i < packet.length; i++) {
                        hex.append(String.format("%02X", packet[i] & 0xFF));
                        if (i < packet.length - 1) hex.append(" ");
                    }
                    LOG.debug(hex.toString());
                }

                if (!CRC16.check(packet, 0, LOOP_PACKET_SIZE)) {
                    LOG.warn("LOOP packet CRC error, discarding");
                    buffer.clear();  // Clear potentially corrupted data
                    if (errorCallback != null) {
                        errorCallback.accept("CRC error in LOOP packet");
                    }
                    continue;
                }

                try {
                    DavisLoopRecord record = DavisRecordParser.parseLoopRecord(packet);
                    LOG.debug("Parsed LOOP: temp={}, humidity={}, pressure={}",
                            record.tempOut(), record.humidityOut(), record.pressure());

                    if (loopRecordCallback != null) {
                        loopRecordCallback.accept(record);
                    }
                } catch (Exception e) {
                    LOG.error("Error parsing LOOP packet", e);
                }
                continue;
            }

            // If we get here with data but can't parse it, might be incomplete
            if (buffer.available() < LOOP_PACKET_SIZE) {
                // Wait for more data
                break;
            }

            // Unknown data - skip one byte
            LOG.debug("Skipping unknown byte: 0x{}", String.format("%02X", first));
            buffer.read(1);
        }
    }

    // ==================== Archive Download (Recovery) ====================

    /**
     * Request archive download from the specified time.
     * This is a blocking operation that temporarily disables the event listener.
     *
     * @param fromTime start time for archive download
     * @throws IOException if download fails
     */
    public void downloadArchive(Instant fromTime) throws IOException {
        LOG.info("Starting archive download from {}", fromTime);
        setState(State.ARCHIVING);

        // Disable event listener for blocking reads
        connection.disableListener();

        try {
            // Wake console first
            connection.writeCommand("");
            Thread.sleep(500);

            // Send DMPAFT command
            connection.writeCommand("DMPAFT");

            // Wait for ACK
            byte[] ack = connection.readBlocking(1, 2000);
            if (ack[0] != ACK) {
                throw new IOException("Expected ACK, got: " + ack[0]);
            }

            // Send date/time with CRC
            byte[] dateBytes = DavisRecordParser.encodeDate(fromTime);
            byte[] timeBytes = DavisRecordParser.encodeTime(fromTime);

            CRC16 crc = new CRC16();
            crc.add(dateBytes);
            crc.add(timeBytes);

            connection.write(dateBytes);
            connection.write(timeBytes);
            connection.write(crc.getCrc());

            // Wait for ACK
            ack = connection.readBlocking(1, 2000);
            if (ack[0] != ACK) {
                throw new IOException("Expected ACK after date, got: " + ack[0]);
            }

            // Read header (6 bytes: pages count + start index + CRC)
            byte[] header = connection.readBlocking(6, 2000);
            if (!CRC16.check(header, 0, 6)) {
                throw new IOException("CRC error in archive header");
            }

            int numPages = DavisRecordParser.parseWord(header, 0);
            int startIndex = DavisRecordParser.parseWord(header, 2);
            LOG.info("Archive: {} pages, starting at index {}", numPages, startIndex);

            // Send ACK to start download
            connection.writeByte(ACK);

            long lastGoodTime = fromTime.toEpochMilli();

            for (int page = 0; page < numPages; page++) {
                byte[] pageData = connection.readBlocking(ARCHIVE_PAGE_SIZE, 2000);

                if (!CRC16.check(pageData, 0, ARCHIVE_PAGE_SIZE)) {
                    // Request retransmit
                    connection.writeByte(NAK);
                    pageData = connection.readBlocking(ARCHIVE_PAGE_SIZE, 2000);
                    if (!CRC16.check(pageData, 0, ARCHIVE_PAGE_SIZE)) {
                        throw new IOException("CRC error in archive page after retry");
                    }
                }

                // Parse records in page
                int recordStart = page == 0 ? startIndex : 0;
                for (int rec = recordStart; rec < RECORDS_PER_PAGE; rec++) {
                    int offset = 1 + (rec * ARCHIVE_RECORD_SIZE);

                    // Check for empty record
                    if ((pageData[offset] & 0xFF) == 0xFF || pageData[offset] == 0x00) {
                        LOG.debug("Empty record at page {}, record {}", page, rec);
                        break;
                    }

                    DavisArchiveRecord record = DavisRecordParser.parseArchiveRecord(pageData, offset);

                    // Only accept records newer than last known
                    if (record.timestamp().toEpochMilli() > lastGoodTime) {
                        if (archiveRecordCallback != null) {
                            archiveRecordCallback.accept(record);
                        }
                        lastGoodTime = record.timestamp().toEpochMilli();
                    } else {
                        LOG.debug("Skipping old record: {}", record.timestamp());
                        break;
                    }
                }

                // Send ACK for next page
                connection.writeByte(ACK);
            }

            LOG.info("Archive download complete");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Archive download interrupted", e);
        } finally {
            // Re-enable event listener and restart LOOP
            buffer.clear();
            connection.enableListener(this::onDataReceived);
            startLooping();
        }
    }

    /**
     * Ring buffer for accumulating received bytes.
     */
    private static class RingBuffer {
        private final byte[] buffer;
        private final int size;
        private int readIndex = 0;
        private int writeIndex = 0;

        RingBuffer(int size) {
            this.size = size;
            this.buffer = new byte[size];
        }

        synchronized void write(byte[] data) {
            for (byte b : data) {
                buffer[writeIndex++ % size] = b;
            }
        }

        synchronized byte read() {
            if (readIndex < writeIndex) {
                return buffer[readIndex++ % size];
            }
            return -1;
        }

        synchronized byte[] read(int len) {
            byte[] result = new byte[len];
            for (int i = 0; i < len; i++) {
                result[i] = read();
            }
            return result;
        }

        synchronized byte peek(int offset) {
            return buffer[(readIndex + offset) % size];
        }

        synchronized int available() {
            return writeIndex - readIndex;
        }

        synchronized void clear() {
            readIndex = 0;
            writeIndex = 0;
        }
    }
}
