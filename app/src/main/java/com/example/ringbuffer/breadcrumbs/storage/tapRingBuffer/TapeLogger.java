package com.example.ringbuffer.breadcrumbs.storage.tapRingBuffer;

import com.example.ringbuffer.tap.ObjectQueue;
import com.example.ringbuffer.tap.QueueFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Example of using Tape (tap) for file-based logging
 */
public class TapeLogger {

    // Log entry data class
    public static class LogEntry {
        public final String timestamp;
        public final String level;
        public final String message;
        public final String tag;

        public LogEntry(String level, String tag, String message) {
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(new Date());
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        public LogEntry(String timestamp, String level, String tag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s/%s: %s", timestamp, level, tag, message);
        }
    }

    // Converter to serialize/deserialize log entries
    public static class LogEntryConverter implements ObjectQueue.Converter<LogEntry> {

        @Override
        public LogEntry from(byte[] source) throws IOException {
            String data = new String(source, StandardCharsets.UTF_8);
            String[] parts = data.split("\\|", 4); // Split into 4 parts max

            if (parts.length != 4) {
                throw new IOException("Invalid log entry format");
            }

            return new LogEntry(parts[0], parts[1], parts[2], parts[3]);
        }

        @Override
        public void toStream(LogEntry value, OutputStream sink) throws IOException {
            String data = value.timestamp + "|" + value.level + "|" + value.tag + "|" + value.message;
            sink.write(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    // The actual logger class
    public static class FileLogger {
        private final ObjectQueue<LogEntry> logQueue;
        private final String tag;

        public FileLogger(File logFile, String tag) throws IOException {
            // Create QueueFile with builder pattern
            QueueFile queueFile = new QueueFile.Builder(logFile)
                    .zero(true)  // Zero out removed data for security
                    .build();

            // Create ObjectQueue with our converter
            this.logQueue = ObjectQueue.create(queueFile, new LogEntryConverter());
            this.tag = tag;
        }

        // Logging methods
        public void debug(String message) throws IOException {
            log("DEBUG", message);
        }

        public void info(String message) throws IOException {
            log("INFO", message);
        }

        public void warning(String message) throws IOException {
            log("WARN", message);
        }

        public void error(String message) throws IOException {
            log("ERROR", message);
        }

        private void log(String level, String message) throws IOException {
            LogEntry entry = new LogEntry(level, tag, message);
            logQueue.add(entry);
        }

        // Reading methods
        public LogEntry readLatest() throws IOException {
            return logQueue.peek();
        }

        public void readAll() throws IOException {
            System.out.println("=== All Log Entries ===");
            for (LogEntry entry : logQueue) {
                System.out.println(entry);
            }
        }

        public void readLatest(int count) throws IOException {
            System.out.println("=== Latest " + count + " Entries ===");
            for (LogEntry entry : logQueue.peek(count)) {
                System.out.println(entry);
            }
        }

        // Maintenance methods
        public void clearOldLogs() throws IOException {
            logQueue.clear();
        }

        public void removeProcessedLogs(int count) throws IOException {
            logQueue.remove(count);
        }

        public int getLogCount() {
            return logQueue.size();
        }

        public void close() throws IOException {
            logQueue.close();
        }
    }

    // Example usage
    public static void main(String[] args) {
        try {
            // Create log file
            File logFile = new File("app_logs.queue");

            // Create logger
            FileLogger logger = new FileLogger(logFile, "MainActivity");

            // Log some entries
            logger.info("Application started");
            logger.debug("Loading configuration");
            logger.warning("Low memory detected");
            logger.error("Network connection failed");
            logger.info("User logged in: john_doe");

            System.out.println("Logged " + logger.getLogCount() + " entries");

            // Read logs
            logger.readAll();

            // Read only latest 3 entries
            logger.readLatest(3);

            // Remove old entries (simulate log rotation)
            logger.removeProcessedLogs(2);
            System.out.println("\nAfter removing 2 old entries:");
            logger.readAll();

            // Close logger
            logger.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}