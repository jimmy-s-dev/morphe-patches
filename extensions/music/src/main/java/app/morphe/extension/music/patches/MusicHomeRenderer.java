/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms.
 */
package app.morphe.extension.music.patches;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads the two phone Home view models observed in YTM 9.15.51.
 * Unknown models are skipped. Commands are returned intact for YTM's native encoder.
 * This parser never reads credentials, makes network requests or starts playback.
 */
public final class MusicHomeRenderer {
    public static final class Entry {
        public final String title, subtitle, artwork, browseId;
        public final byte[] command;
        Entry(String title, String subtitle, String artwork, String browseId, byte[] command) {
            this.title=title; this.subtitle=subtitle; this.artwork=artwork;
            this.browseId=browseId; this.command=command;
        }
    }

    public static List<Entry> read(byte[] renderer) {
        if (renderer.length > 2_000_000) return Collections.emptyList();
        List<Entry> result = new ArrayList<>();
        try {
            for (Proto model : new Proto(renderer).path(172660663, 1, 168777401)) {
                for (Proto body : model.path(5)) {
                    for (Proto shelf : body.path(487343630)) {
                        for (Proto row : shelf.path(5, 1)) add(result, row, true);
                    }
                    for (Proto shelf : body.path(404005902)) {
                        for (Proto row : shelf.path(1, 3)) add(result, row, false);
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Retain any complete rows before an unsupported or malformed structure.
        }
        return result;
    }

    private static void add(List<Entry> result, Proto row, boolean speedDial) {
        if (result.size() >= 200) return;
        try {
            String title=row.text(1);
            if (title.isEmpty() || title.length()>300) return;
            byte[] command=row.bytes(speedDial ? 9 : 4, 169495254);
            if (command.length==0 && speedDial) command=row.bytes(10,169495254);
            if (command.length==0) return;
            String browseId=new Proto(command).text(48687626,2);
            if (!browseId.startsWith("VL")) browseId="";
            String artwork=row.text(speedDial ? 2 : 3,1,1,1);
            if (!artwork.startsWith("https://")) artwork="";
            String subtitle=speedDial ? "" : row.text(2);
            if (subtitle.length()>500) subtitle="";
            result.add(new Entry(title,subtitle,artwork,browseId,command));
        } catch (IllegalArgumentException ignored) { }
    }

    /** Minimal bounded protobuf wire reader. It visits only explicit schema paths. */
    private static final class Proto {
        final byte[] bytes;
        Proto(byte[] bytes) {this.bytes=bytes;}
        long varint(int[] position) {
            long value=0;
            for (int shift=0;shift<64;shift+=7) {
                if (position[0]>=bytes.length) throw new IllegalArgumentException();
                int current=bytes[position[0]++] & 255;
                value|=(long)(current & 127)<<shift;
                if ((current & 128)==0) return value;
            }
            throw new IllegalArgumentException();
        }
        List<Proto> field(int wanted) {
            List<Proto> result=new ArrayList<>();
            int[] position={0}; int count=0;
            while (position[0]<bytes.length) {
                if (++count>20_000) throw new IllegalArgumentException();
                long tag=varint(position);
                int field=(int)(tag>>>3),wire=(int)(tag&7);
                if (field<=0 || tag>>>3>536870911) throw new IllegalArgumentException();
                if (wire==0) {varint(position);continue;}
                long length;
                if (wire==2) length=varint(position);
                else if (wire==1) length=8;
                else if (wire==5) length=4;
                else throw new IllegalArgumentException();
                if (length<0 || length>bytes.length-position[0]) throw new IllegalArgumentException();
                int end=position[0]+(int)length;
                if (wire==2 && field==wanted) {
                    result.add(new Proto(java.util.Arrays.copyOfRange(bytes,position[0],end)));
                }
                position[0]=end;
            }
            return result;
        }
        List<Proto> path(int... path) {
            List<Proto> current=Collections.singletonList(this);
            for (int key:path) {
                List<Proto> next=new ArrayList<>();
                for (Proto proto:current) next.addAll(proto.field(key));
                if (next.size()>2000) throw new IllegalArgumentException();
                current=next;
            }
            return current;
        }
        byte[] bytes(int... path) {
            List<Proto> values=path(path);
            return values.isEmpty() ? new byte[0] : values.get(0).bytes;
        }
        String text(int... path) {return new String(bytes(path),StandardCharsets.UTF_8);}
    }
}
