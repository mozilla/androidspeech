package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggPacketWriter;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// https://wiki.xiph.org/OggOpus
@TargetApi(23)
public class FormatOPUS_OGG extends FormatOPUS {
    public static final String TAG = FormatOPUS_OGG.class.getSimpleName();

    OggFile file;
    OggPacketWriter writer;
    long lastGranule = 0;

    public FormatOPUS_OGG(Context context, EncoderInfo info, ByteArrayOutputStream out) {
        super(context, info, out);
    }

    @Override
    public void create(final EncoderInfo info, ByteArrayOutputStream out) {
        super.create(info, out);
        try {
            OpusInfo oinfo = new OpusInfo();
            oinfo.setNumChannels(info.channels);
            oinfo.setOutputGain(0);
            oinfo.setPreSkip(0);
            oinfo.setSampleRate(info.hz);
            OpusTags otags = new OpusTags();
            file = new OggFile(out);
            writer = file.getPacketWriter();
            writer.bufferPacket(oinfo.write());
            writer.flush();
            writer.bufferPacket(otags.write());
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    void encode(ByteBuffer bb, long dur) {
        OpusAudioData frame = new OpusAudioData(bb.array());
        long end = NumSamples + dur;
        long gr = OpusAudioData.OPUS_GRANULE_RATE * end / info.hz; // Ogg gr always at 48000hz
        frame.setGranulePosition(gr);
        try {
            if (frame.getGranulePosition() >= 0 && lastGranule != frame.getGranulePosition()) {
                writer.flush();
                lastGranule = frame.getGranulePosition();
                writer.setGranulePosition(lastGranule);
            }
            writer.bufferPacket(frame.write());
            if (writer.getSizePendingFlush() > 16384) {
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        super.close();
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (file != null) {
                file.close();
                file = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
