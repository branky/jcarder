package com.enea.jcarder.common.events;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.enea.jcarder.util.Logger;

public final class EventFileReader {
    private static final int INT_LENGTH = 4;
    private static final int LONG_LENGTH = 8;
    private static final Logger LOGGER = Logger.getLogger("com.enea.jcarder");
    static final int LOCK_EVENT_LENGTH = (INT_LENGTH * 4) + LONG_LENGTH;
    static final long MAGIC_COOKIE = 2153191828159737167L;
    static final int MAJOR_VERSION = 1;
    static final int MINOR_VERSION = 0;

    private EventFileReader() { }

    public static void parseFile(File file,
                                 LockEventListenerIfc eventReceiver)
    throws IOException {
        int numberOfParsedEvents = 0;
        FileInputStream fis = new FileInputStream(file);
        LOGGER.info("Opening for reading: " + file.getAbsolutePath());
        FileChannel fileChannel = fis.getChannel();
        validateHeader(fileChannel, file.getAbsolutePath());
        final ByteBuffer buffer = ByteBuffer.allocate(LOCK_EVENT_LENGTH);
        while (fileChannel.read(buffer) == LOCK_EVENT_LENGTH) {
            buffer.rewind();
            parseLockEvent(buffer, eventReceiver);
            buffer.rewind();
            numberOfParsedEvents++;
        }
        LOGGER.fine("Loaded " + numberOfParsedEvents
                      + " lock events from file.");
    }

    private static void validateHeader(FileChannel channel,
                                       String filename) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(8 + 4 + 4);
        channel.read(buffer);
        buffer.flip();
        if (MAGIC_COOKIE != buffer.getLong()) {
            throw new IOException("Invalid file contents in: " + filename);
        }
        final int majorVersion = buffer.getInt();
        final int minorVersion = buffer.getInt();
        if (majorVersion != MAJOR_VERSION) {
            throw new IOException("Incompatible version: "
                                  + majorVersion + "." + minorVersion
                                  + " in: " + filename);
        }
    }

    private static void parseLockEvent(ByteBuffer lockEventBuffer,
                                       LockEventListenerIfc eventReceiver)
    throws IOException {
        final int lockId                    = lockEventBuffer.getInt();
        final int lockingContextId          = lockEventBuffer.getInt();
        final int lastTakenLockId           = lockEventBuffer.getInt();
        final int lastTakenLockingContextId = lockEventBuffer.getInt();
        final long threadId                 = lockEventBuffer.getLong();
        eventReceiver.onLockEvent(lockId,
                                  lockingContextId,
                                  lastTakenLockId,
                                  lastTakenLockingContextId,
                                  threadId);
    }
}