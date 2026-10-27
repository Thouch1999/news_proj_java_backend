package com.api.v1.newssystem.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Tiny but structurally real video files for the tests (no real picture data): an MP4 with an ftyp and a moov box (mvhd,
 * and a video track whose sample entry is the codec asked for) and a WebM with an EBML header and an Info element.
 */
public final class VideoFixtures {
    private VideoFixtures() {
    }

    /** An MP4 of the given video codec (avc1 is H.264, hvc1 is H.265) and duration in seconds (null: a zero duration). */
    public static byte[] mp4(String codec, Double seconds) {
        return mp4("isom", codec, seconds);
    }

    public static byte[] mp4(String brand, String codec, Double seconds) {
        ByteBuffer mvhdPayload = ByteBuffer.allocate(100);
        mvhdPayload.putInt(0); // version 0, flags
        mvhdPayload.putInt(0); // creation
        mvhdPayload.putInt(0); // modification
        mvhdPayload.putInt(1000); // timescale
        mvhdPayload.putInt(seconds == null ? 0 : (int) Math.round(seconds * 1000)); // duration
        byte[] hdlr = concat(new byte[8], ascii("vide"), new byte[12]);
        byte[] entry = concat(intBytes(16), ascii(codec), new byte[8]);
        byte[] stsd = concat(new byte[4], intBytes(1), entry);
        byte[] stbl = box("stbl", box("stsd", stsd));
        byte[] trak = box("trak", box("mdia", concat(box("hdlr", hdlr), box("minf", stbl))));
        byte[] moov = box("moov", concat(box("mvhd", mvhdPayload.array()), trak));
        byte[] ftyp = box("ftyp", concat(ascii(brand), new byte[]{0, 0, 2, 0}, ascii(brand), ascii("mp41")));
        return concat(ftyp, moov, box("mdat", new byte[16]));
    }

    /** A WebM (docType "webm") or a Matroska file (docType "matroska") of the given duration in seconds (null: none). */
    public static byte[] webm(String docType, Double seconds) {
        byte[] header = element(0x1A45DFA3, element(0x4282, ascii(docType)));
        byte[] info = seconds == null ? new byte[0]
                : element(0x1549A966, concat(element(0x2AD7B1, new byte[]{0x0F, 0x42, 0x40}),
                element(0x4489, ByteBuffer.allocate(8).putDouble(seconds * 1000).array())));
        byte[] segment = element(0x18538067, concat(info, element(0x1F43B675, new byte[8])));
        return concat(header, segment);
    }

    private static byte[] box(String type, byte[] payload) {
        return concat(intBytes(8 + payload.length), ascii(type), payload);
    }

    // an EBML element with a short id and a size of at most 16383 bytes
    private static byte[] element(int id, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = id > 0xFFFFFF ? 4 : id > 0xFFFF ? 3 : id > 0xFF ? 2 : 1;
        for (int i = length - 1; i >= 0; i--) {
            out.write((id >> (8 * i)) & 0xFF);
        }
        if (payload.length < 127) {
            out.write(0x80 | payload.length);
        } else {
            out.write(0x40 | (payload.length >> 8));
            out.write(payload.length & 0xFF);
        }
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
