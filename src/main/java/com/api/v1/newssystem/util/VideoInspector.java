package com.api.v1.newssystem.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Looks inside an uploaded video in pure Java (no native binaries, no transcoding): the real container from the content
 * (MP4 or WebM, never the name or the Content-Type), the video codec of an MP4 and the duration. It reads only the boxes and
 * elements it needs and never throws: what cannot be read is reported as unknown (null), and then only the size limit applies.
 * An MP4 reads its moov box (mvhd for the duration, the sample entry of the video track for the codec); a WebM reads the EBML
 * header (the DocType must be "webm", a Matroska file is refused) and the Duration of the Info element.
 */
public final class VideoInspector {
    public static final String MP4 = "mp4";
    public static final String WEBM = "webm";

    // H.264 sample entries (avc1: parameter sets in the header, avc3: also in the stream)
    private static final Set<String> H264 = Set.of("avc1", "avc3");
    // the brands of the MP4 family besides iso*: QuickTime, HEIF, 3GP and the like are not MP4 files for a browser
    private static final Set<String> MP4_BRANDS = Set.of("mp41", "mp42", "avc1", "M4V ", "M4VH", "dash");

    private static final int EBML = 0x1A45DFA3;
    private static final int SEGMENT = 0x18538067;
    private static final int INFO = 0x1549A966;
    private static final int TIMECODE_SCALE = 0x2AD7B1;
    private static final int DURATION = 0x4489;
    private static final int DOC_TYPE = 0x4282;

    /**
     * What was found: type is mp4 or webm; videoCodec is the sample entry of an MP4 video track (avc1 ...), null when
     * unknown (and for WebM); seconds is null when the duration could not be read.
     */
    public record Info(String type, String videoCodec, Double seconds) {
        /** An MP4 whose video track is known to be something other than H.264 (H.265, AV1 ...). */
        public boolean isNotH264() {
            return MP4.equals(type) && videoCodec != null && !H264.contains(videoCodec);
        }
    }

    private VideoInspector() {
    }

    /** mp4 or webm from the first bytes of a file (at least 12), or null for anything else. */
    public static String containerOf(byte[] head) {
        if (head.length >= 4 && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45 && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            return WEBM;
        }
        if (head.length >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            String brand = new String(head, 8, 4, StandardCharsets.ISO_8859_1);
            if (brand.startsWith("iso") || MP4_BRANDS.contains(brand)) {
                return MP4;
            }
        }
        return null;
    }

    /** What the file is, or empty when it is not an MP4 or a WebM (a Matroska file is not). */
    public static Optional<Info> inspect(byte[] data) {
        String type = containerOf(data);
        if (type == null) {
            return Optional.empty();
        }
        try {
            return type.equals(MP4) ? Optional.of(mp4(data)) : webm(data);
        } catch (RuntimeException e) {
            return Optional.of(new Info(type, null, null)); // a file that cannot be read further: the size limit is all we have
        }
    }

    // ---- MP4: a tree of boxes (size, type, payload) ----

    private static Info mp4(byte[] d) {
        int[] moov = child(d, 0, d.length, "moov");
        if (moov == null) {
            return new Info(MP4, null, null);
        }
        Double seconds = null;
        int[] mvhd = child(d, moov[0], moov[1], "mvhd");
        if (mvhd != null) {
            int p = mvhd[0];
            boolean v1 = (d[p] & 0xFF) == 1;
            long timescale = u32(d, v1 ? p + 20 : p + 12);
            long duration = v1 ? u64(d, p + 24) : u32(d, p + 16);
            boolean unknown = duration == 0 || (v1 ? duration == -1L : duration == 0xFFFFFFFFL);
            if (timescale > 0 && !unknown) {
                seconds = (double) duration / timescale;
            }
        }
        return new Info(MP4, videoCodec(d, moov), seconds);
    }

    private static String videoCodec(byte[] d, int[] moov) {
        for (int[] trak : children(d, moov[0], moov[1], "trak")) {
            int[] mdia = child(d, trak[0], trak[1], "mdia");
            int[] hdlr = mdia == null ? null : child(d, mdia[0], mdia[1], "hdlr");
            // hdlr: version and flags (4), predefined (4), then the handler type
            if (hdlr == null || !"vide".equals(ascii(d, hdlr[0] + 8, 4))) {
                continue;
            }
            int[] minf = child(d, mdia[0], mdia[1], "minf");
            int[] stbl = minf == null ? null : child(d, minf[0], minf[1], "stbl");
            int[] stsd = stbl == null ? null : child(d, stbl[0], stbl[1], "stsd");
            // stsd: version and flags (4), entry count (4), then the first entry: its size (4) and its type
            if (stsd != null) {
                return ascii(d, stsd[0] + 12, 4);
            }
        }
        return null;
    }

    // the {payloadStart, end} of the first child box with this type inside [start, end), or null
    private static int[] child(byte[] d, int start, int end, String type) {
        List<int[]> found = children(d, start, end, type);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<int[]> children(byte[] d, int start, int end, String type) {
        List<int[]> found = new ArrayList<>();
        int pos = start;
        while (pos + 8 <= end) {
            long size = u32(d, pos);
            int header = 8;
            if (size == 1) {
                if (pos + 16 > end) {
                    break;
                }
                size = u64(d, pos + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos; // the box runs to the end
            }
            if (size < header) {
                break;
            }
            int boxEnd = (int) Math.min(end, pos + size);
            if (type.equals(ascii(d, pos + 4, 4))) {
                found.add(new int[]{pos + header, boxEnd});
            }
            if (boxEnd <= pos) {
                break;
            }
            pos = boxEnd;
        }
        return found;
    }

    // ---- WebM: EBML elements (id, size, payload) ----

    private static Optional<Info> webm(byte[] d) {
        int[] header = element(d, 0, d.length, EBML);
        if (header != null) {
            int[] docType = element(d, header[0], header[1], DOC_TYPE);
            if (docType != null && !"webm".equals(ascii(d, docType[0], docType[1] - docType[0]))) {
                return Optional.empty(); // Matroska: it may hold H.265 or other codecs a browser cannot play
            }
        }
        Double seconds = null;
        int[] segment = element(d, header == null ? 0 : header[1], d.length, SEGMENT);
        int[] info = segment == null ? null : element(d, segment[0], segment[1], INFO);
        if (info != null) {
            long scale = 1_000_000L; // nanoseconds per tick when the file does not say
            int[] timecode = element(d, info[0], info[1], TIMECODE_SCALE);
            if (timecode != null) {
                scale = unsigned(d, timecode[0], timecode[1]);
            }
            int[] duration = element(d, info[0], info[1], DURATION);
            if (duration != null && scale > 0) {
                int length = duration[1] - duration[0];
                double ticks = length == 4 ? Float.intBitsToFloat((int) unsigned(d, duration[0], duration[1]))
                        : length == 8 ? Double.longBitsToDouble(unsigned(d, duration[0], duration[1])) : -1;
                if (ticks > 0) {
                    seconds = ticks * scale / 1_000_000_000.0;
                }
            }
        }
        return Optional.of(new Info(WEBM, null, seconds));
    }

    // the {payloadStart, payloadEnd} of the first element with this id inside [start, end), skipping the others, or null
    private static int[] element(byte[] d, int start, int end, int wanted) {
        int pos = start;
        while (pos < end) {
            int idLength = Integer.numberOfLeadingZeros(d[pos] & 0xFF) - 23;
            if (idLength < 1 || idLength > 4 || pos + idLength > end) {
                return null;
            }
            int id = 0;
            for (int i = 0; i < idLength; i++) {
                id = (id << 8) | (d[pos + i] & 0xFF);
            }
            pos += idLength;
            if (pos >= end) {
                return null;
            }
            int sizeLength = Integer.numberOfLeadingZeros(d[pos] & 0xFF) - 23;
            if (sizeLength < 1 || sizeLength > 8 || pos + sizeLength > end) {
                return null;
            }
            long size = (d[pos] & 0xFF) & (0xFF >> sizeLength);
            boolean unknown = size == (0xFF >> sizeLength);
            for (int i = 1; i < sizeLength; i++) {
                int next = d[pos + i] & 0xFF;
                unknown &= next == 0xFF;
                size = (size << 8) | next;
            }
            pos += sizeLength;
            int payloadEnd = (int) (unknown ? end : Math.min((long) end, pos + size));
            if (id == wanted) {
                return new int[]{pos, payloadEnd};
            }
            pos = payloadEnd;
        }
        return null;
    }

    // ---- bytes ----

    private static long u32(byte[] d, int at) {
        return ((long) (d[at] & 0xFF) << 24) | ((d[at + 1] & 0xFF) << 16) | ((d[at + 2] & 0xFF) << 8) | (d[at + 3] & 0xFF);
    }

    private static long u64(byte[] d, int at) {
        return (u32(d, at) << 32) | u32(d, at + 4);
    }

    private static long unsigned(byte[] d, int start, int end) {
        long value = 0;
        for (int i = start; i < end && i < start + 8; i++) {
            value = (value << 8) | (d[i] & 0xFF);
        }
        return value;
    }

    private static String ascii(byte[] d, int at, int length) {
        return new String(d, at, length, StandardCharsets.ISO_8859_1);
    }
}
