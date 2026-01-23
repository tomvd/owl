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

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Serial port connection wrapper for Davis Vantage Pro console.
 * <p>
 * Uses jSerialComm for cross-platform serial communication.
 * Default settings: 19200 baud, 8N1.
 */
public class DavisSerialConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DavisSerialConnection.class);

    private static final int DEFAULT_BAUD_RATE = 19200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = SerialPort.ONE_STOP_BIT;
    private static final int PARITY = SerialPort.NO_PARITY;
    private static final int READ_TIMEOUT_MS = 2000;

    private final String portName;
    private final int baudRate;
    private SerialPort serialPort;
    private Consumer<byte[]> dataCallback;
    private volatile boolean connected = false;

    /**
     * Create a connection with default baud rate (19200).
     *
     * @param portName serial port name (e.g., "COM4" or "/dev/ttyUSB0")
     */
    public DavisSerialConnection(String portName) {
        this(portName, DEFAULT_BAUD_RATE);
    }

    /**
     * Create a connection with specified baud rate.
     *
     * @param portName serial port name
     * @param baudRate baud rate (typically 19200)
     */
    public DavisSerialConnection(String portName, int baudRate) {
        this.portName = portName;
        this.baudRate = baudRate;
    }

    /**
     * Set the callback for received data.
     *
     * @param callback function to receive byte arrays as they arrive
     */
    public void setDataCallback(Consumer<byte[]> callback) {
        this.dataCallback = callback;
    }

    /**
     * Open the serial port connection.
     *
     * @throws IOException if port cannot be opened
     */
    public void open() throws IOException {
        LOG.info("Opening serial port {} at {} baud", portName, baudRate);

        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(baudRate);
        serialPort.setNumDataBits(DATA_BITS);
        serialPort.setNumStopBits(STOP_BITS);
        serialPort.setParity(PARITY);
        serialPort.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                READ_TIMEOUT_MS,
                0
        );

        if (!serialPort.openPort()) {
            throw new IOException("Failed to open serial port: " + portName);
        }

        connected = true;

        // Set up data listener
        serialPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                    return;
                }

                int available = serialPort.bytesAvailable();
                if (available > 0) {
                    byte[] data = new byte[available];
                    int read = serialPort.readBytes(data, available);
                    if (read > 0 && dataCallback != null) {
                        if (read < available) {
                            byte[] trimmed = new byte[read];
                            System.arraycopy(data, 0, trimmed, 0, read);
                            dataCallback.accept(trimmed);
                        } else {
                            dataCallback.accept(data);
                        }
                    }
                }
            }
        });

        LOG.info("Serial port {} opened successfully", portName);
    }

    /**
     * Write data to the serial port.
     *
     * @param data bytes to write
     * @throws IOException if write fails
     */
    public void write(byte[] data) throws IOException {
        if (!connected || serialPort == null) {
            throw new IOException("Serial port not connected");
        }

        int written = serialPort.writeBytes(data, data.length);
        if (written < data.length) {
            throw new IOException("Failed to write all bytes: " + written + "/" + data.length);
        }
    }

    /**
     * Write a string followed by newline.
     *
     * @param command command string (newline added automatically)
     * @throws IOException if write fails
     */
    public void writeCommand(String command) throws IOException {
        write((command + "\n").getBytes());
    }

    /**
     * Write a single byte.
     *
     * @param b byte to write
     * @throws IOException if write fails
     */
    public void writeByte(byte b) throws IOException {
        write(new byte[]{b});
    }

    /**
     * Read bytes with blocking (for archive download).
     *
     * @param length number of bytes to read
     * @param timeoutMs timeout in milliseconds
     * @return bytes read
     * @throws IOException if read fails or times out
     */
    public byte[] readBlocking(int length, int timeoutMs) throws IOException {
        if (!connected || serialPort == null) {
            throw new IOException("Serial port not connected");
        }

        // Temporarily set blocking timeout
        serialPort.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_BLOCKING,
                timeoutMs,
                0
        );

        try {
            byte[] data = new byte[length];
            int totalRead = 0;
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (totalRead < length && System.currentTimeMillis() < deadline) {
                int read = serialPort.readBytes(data, length - totalRead, totalRead);
                if (read > 0) {
                    totalRead += read;
                }
            }

            if (totalRead < length) {
                throw new IOException("Timeout reading " + length + " bytes, got " + totalRead);
            }

            return data;
        } finally {
            // Restore semi-blocking timeout
            serialPort.setComPortTimeouts(
                    SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                    READ_TIMEOUT_MS,
                    0
            );
        }
    }

    /**
     * Temporarily disable the data listener (for blocking operations).
     */
    public void disableListener() {
        if (serialPort != null) {
            serialPort.removeDataListener();
        }
    }

    /**
     * Re-enable the data listener after blocking operations.
     *
     * @param callback callback to use
     */
    public void enableListener(Consumer<byte[]> callback) {
        if (serialPort != null && connected) {
            this.dataCallback = callback;
            serialPort.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                        return;
                    }
                    int available = serialPort.bytesAvailable();
                    if (available > 0) {
                        byte[] data = new byte[available];
                        int read = serialPort.readBytes(data, available);
                        if (read > 0 && dataCallback != null) {
                            dataCallback.accept(data);
                        }
                    }
                }
            });
        }
    }

    /**
     * Check if the connection is open.
     */
    public boolean isConnected() {
        return connected && serialPort != null && serialPort.isOpen();
    }

    /**
     * Get the port name.
     */
    public String getPortName() {
        return portName;
    }

    @Override
    public void close() {
        if (serialPort != null) {
            LOG.info("Closing serial port {}", portName);
            serialPort.removeDataListener();
            serialPort.closePort();
            connected = false;
            serialPort = null;
        }
    }

    /**
     * List available serial ports.
     *
     * @return array of port descriptors
     */
    public static String[] listPorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] names = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            names[i] = ports[i].getSystemPortName() + " - " + ports[i].getDescriptivePortName();
        }
        return names;
    }
}
