/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2489
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.music.patches;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

/**
 * Exposes the signed-in phone Library, history and home through Android Auto browse folders.
 * Playlist entries open their songs without starting playback. Playable song IDs are supplied
 * by YTM's native encoder; this extension never invokes transport controls.
 *
 * <p>The Kotlin patch adds the interfaces and methods below to YTM's obfuscated classes.
 */
@SuppressWarnings("unused")
public final class RestoreAndroidAutoPlaylistsPatch {
    private static final String PHONE_LIBRARY_BROWSE_ID = "FEmusic_library_landing";
    private static final String EPISODES_FOR_LATER_BROWSE_ID = "VLSE";
    private static final String PLAYLISTS_TITLE_RESOURCE_NAME = "library_playlists_shelf_title";
    private static final Executor BACKGROUND_EXECUTOR = Utils::runOnBackgroundThread;
    // A playlist named Playlists starts playback; Android Auto's Playlists folder opens the list.
    private static final Set<String> PLAYLISTS_TITLE_MATCH_MEDIA_IDS =
            ConcurrentHashMap.newKeySet();

    // YTM uses this object to request the phone Library, more playlists, and opened playlists.
    public interface PhoneBrowseRequests {
        @NonNull ListenableFuture<BrowseResponse> patch_requestBrowse(
                @NonNull String browseId, @NonNull Executor executor);
        @NonNull ListenableFuture<BrowseResponse> patch_requestMorePlaylists(
                @NonNull Object continuationAction, @NonNull Executor executor);
    }

    // Library, pagination, and opened-playlist requests all return this class.
    public interface BrowseResponse {
        // Initial Library and opened-playlist responses.
        @NonNull Iterable<BrowseTab> patch_getTabs();
        // Pagination responses.
        @Nullable GridRenderer patch_getMorePlaylists();
        @Nullable OpenedPlaylistSongs patch_getMoreSongs();
        // Opened-playlist responses.
        @Nullable String patch_getPlayableMediaId();
    }

    // YTM uses TabRenderer for both Library and opened-playlist contents, even when no tab is visible.
    public interface BrowseTab {
        @Nullable SectionList patch_getSectionList();
    }

    // SectionListRenderer is an invisible container for the Library grid or opened-playlist songs.
    public interface SectionList {
        @NonNull Iterable<?> patch_getContents();
    }

    public interface GridRenderer {
        // The phone Library grid mixes playlists with artists, podcasts, and other content.
        @NonNull Iterable<?> patch_getRows();
        // NEXT requests another batch; RELOAD replaces the current grid.
        @NonNull Iterable<?> patch_getContinuationActions();
    }

    // Protobuf field 175617300 contains the songs below an opened playlist's header.
    public interface OpenedPlaylistSongs {
        @NonNull Iterable<PlaylistOrTrack> patch_getSongs();
        @NonNull Iterable<?> patch_getContinuationActions();
    }

    // Carries the requested Playlists folder ID and the playlist list returned to Android Auto.
    public interface AndroidAutoPlaylistsRequest {
        boolean patch_isRoot();
        @Nullable String patch_getRequestedMediaId();
        void patch_deliverAndroidAutoPlaylists(
                @NonNull List<MediaBrowserCompat.MediaItem> androidAutoPlaylists);
    }

    // YTM uses protobuf field 161429595 for a Library playlist or an opened-playlist song.
    public interface PlaylistOrTrack {
        @Nullable String patch_getPlaylistBrowseId();
        @Nullable String patch_getPlayableMediaId();
        @Nullable Uri patch_getArtworkUri();
        @Nullable CharSequence patch_getTitle();
        @Nullable CharSequence patch_getSubtitle();
    }

    // MusicBrowserService recreation replaces this; active loads keep the instance in their state.
    @Nullable
    private static volatile PhoneBrowseRequests phoneBrowseRequests;

    private RestoreAndroidAutoPlaylistsPatch() {
    }

    /** Injection point. Captures the YTM object used for phone Library and playlist requests. */
    public static void setPhoneBrowseRequests(@NonNull PhoneBrowseRequests requests) {
        phoneBrowseRequests = requests;
        Logger.printDebug(() -> "Ready to request phone Library and opened playlists: " +
                requests.getClass().getName());
    }

    /**
     * Injection point. YTM detaches MediaBrowserService.Result before this hook, so the Android
     * Auto playlist list can be delivered after the phone requests finish.
     */
    public static boolean handleAndroidAutoPlaylists(
            @NonNull AndroidAutoPlaylistsRequest androidAutoRequest) {
        try {
            PhoneBrowseRequests phoneRequests = phoneBrowseRequests;
            if (phoneRequests == null) return false;
            return handleMusicBrowse(androidAutoRequest, phoneRequests);
        } catch (RuntimeException ex) {
            Logger.printException(() -> "Could not handle Android Auto Playlists request", ex);
            return false;
        }
    }

    /** Injection point. Records media IDs whose title matches Android Auto's localized Playlists. */
    public static void rememberPlaylistsTitleMatch(
            @Nullable String androidAutoMediaId, @Nullable CharSequence title) {
        if (title == null || !ResourceUtils.getString(PLAYLISTS_TITLE_RESOURCE_NAME)
                .contentEquals(title)) return;
        if (androidAutoMediaId != null) PLAYLISTS_TITLE_MATCH_MEDIA_IDS.add(androidAutoMediaId);
    }

    private static String subtitleOrEmpty(PlaylistOrTrack playlist) {
        try {
            CharSequence subtitle = playlist.patch_getSubtitle();
            return subtitle == null ? "" : subtitle.toString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static Uri artworkUriOrNull(PlaylistOrTrack playlist) {
        try {
            return playlist.patch_getArtworkUri();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static MediaBrowserCompat.MediaItem createAndroidAutoPlaylist(
            String playableMediaId, String title, String subtitle, Uri artworkUri) {
        MediaDescriptionCompat description = new MediaDescriptionCompat(
                playableMediaId, title, subtitle, null, null, artworkUri, listStyle(), null);
        return new MediaBrowserCompat.MediaItem(
                description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    private static boolean isAndroidAutoPlaylistsRequest(
            AndroidAutoPlaylistsRequest androidAutoRequest) {
        String requestedMediaId = androidAutoRequest.patch_getRequestedMediaId();
        return requestedMediaId != null &&
                PLAYLISTS_TITLE_MATCH_MEDIA_IDS.contains(requestedMediaId);
    }

    public interface NestedContainer {
        @NonNull Iterable<?> patch_getChildren();
    }

    public interface WrappedRenderer {
        @Nullable Object patch_getRenderer();
    }

    public interface SerializedRenderer {
        @NonNull byte[] patch_getBytes();
        @Nullable String patch_createPlayableId(@NonNull byte[] command);
    }

    @Nullable
    private static Object firstContinuationAction(GridRenderer grid) {
        Iterator<?> actions = grid.patch_getContinuationActions().iterator();
        return actions.hasNext() ? actions.next() : null;
    }

    private static final String MUSIC_FOLDER_PREFIX = "AA_MUSIC_";
    private static final String PHONE_HOME = "FEmusic_home";
    private static final String PHONE_HISTORY = "FEmusic_history";

    private static Bundle listStyle() {
        Bundle extras = new Bundle();
        extras.putInt("android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT", 1);
        extras.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1);
        extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1);
        return extras;
    }

    // MediaItemInfo.server_id is protobuf field 1 in 9.15.51. The native decoder
    // accepts URL-safe Base64 and validates the server ID as [a-zA-Z0-9_-]*.
    // Encode only our folders; playable IDs always come from YTM's own encoder.
    private static String folderId(String browseId) {
        byte[] name = (MUSIC_FOLDER_PREFIX + android.util.Base64.encodeToString(
                browseId.getBytes(java.nio.charset.StandardCharsets.UTF_8), 11))
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        bytes.write(10);
        int length = name.length;
        while (length > 127) { bytes.write((length & 127) | 128); length >>>= 7; }
        bytes.write(length);
        bytes.write(name, 0, name.length);
        return android.util.Base64.encodeToString(bytes.toByteArray(), 10);
    }

    @Nullable
    private static String folderBrowseId(@Nullable String mediaId) {
        if (mediaId == null) return null;
        try {
            byte[] bytes = android.util.Base64.decode(mediaId, 10);
            if (bytes.length < 3 || bytes[0] != 10) return null;
            int length = 0, shift = 0, offset = 1;
            int value;
            do {
                if (offset >= bytes.length || shift > 21) return null;
                value = bytes[offset++] & 255;
                length |= (value & 127) << shift;
                shift += 7;
            } while ((value & 128) != 0);
            if (length != bytes.length - offset) return null;
            String name = new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
            if (!name.startsWith(MUSIC_FOLDER_PREFIX)) return null;
            String browseId = new String(android.util.Base64.decode(
                    name.substring(MUSIC_FOLDER_PREFIX.length()), 11), java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = browseId.split("\\|", -1);
            if (parts.length > 2) return null;
            if (parts.length == 2 && (Integer.parseInt(parts[1]) < 0 || Integer.parseInt(parts[1]) > 10_000)) return null;
            String source = parts[0];
            return PHONE_LIBRARY_BROWSE_ID.equals(source) || PHONE_HOME.equals(source)
                    || PHONE_HISTORY.equals(source) || source.startsWith("VL") ? browseId : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    private static MediaBrowserCompat.MediaItem folder(String browseId, String title,
                                                       String subtitle, Uri artwork) {
        return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat(folderId(browseId),
                title, subtitle, null, null, artwork, listStyle(), null), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private static boolean handleMusicBrowse(AndroidAutoPlaylistsRequest request, PhoneBrowseRequests phone) {
        if (request.patch_isRoot()) {
            boolean korean = java.util.Locale.getDefault().getLanguage().equals("ko");
            List<MediaBrowserCompat.MediaItem> folders = new ArrayList<>();
            folders.add(folder(PHONE_LIBRARY_BROWSE_ID, korean ? "재생목록" : "Playlists", "", null));
            folders.add(folder(PHONE_HISTORY, korean ? "최근 감상" : "Recent listening", "", null));
            folders.add(folder(PHONE_HOME, korean ? "추천" : "Recommendations", "", null));
            request.patch_deliverAndroidAutoPlaylists(folders);
            Logger.printInfo(() -> "AA music: delivered three browse folders");
            return true;
        }
        String browseId = folderBrowseId(request.patch_getRequestedMediaId());
        if (browseId == null && isAndroidAutoPlaylistsRequest(request)) browseId = PHONE_LIBRARY_BROWSE_ID;
        if (browseId == null) return false;
        String[] parts = browseId.split("\\|", -1);
        browseId = parts[0];
        int offset = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
        MusicBrowseLoad load = new MusicBrowseLoad(request, phone, browseId, offset);
        Utils.runOnMainThreadDelayed(load::finish, 30_000);
        load.request(phone.patch_requestBrowse(browseId, BACKGROUND_EXECUTOR));
        return true;
    }

    private static final class MusicBrowseLoad {
        final AndroidAutoPlaylistsRequest request;
        final PhoneBrowseRequests phone;
        final boolean library;
        final String browseId;
        final int offset;
        final List<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        final Set<Object> continuations = new HashSet<>();
        boolean delivered;
        int pages;

        MusicBrowseLoad(AndroidAutoPlaylistsRequest request, PhoneBrowseRequests phone, String browseId, int offset) {
            this.request = request; this.phone = phone; this.browseId = browseId; this.offset = offset;
            this.library = PHONE_LIBRARY_BROWSE_ID.equals(browseId);
        }

        synchronized void finish() {
            if (delivered) return;
            delivered = true;
            List<MediaBrowserCompat.MediaItem> page = new ArrayList<>();
            int index = Math.min(offset, items.size());
            int bytes = 0;
            // Binder's 1 MiB buffer is shared by concurrent transactions. A 729 KiB
            // history response already failed on the test phone. Keep a generous margin.
            while (index < items.size() && page.size() < 40) {
                MediaBrowserCompat.MediaItem item = items.get(index);
                Parcel parcel = Parcel.obtain();
                int size;
                try { item.writeToParcel(parcel, 0); size = parcel.dataSize(); }
                finally { parcel.recycle(); }
                if (size > 90_000) { index++; continue; }
                if (!page.isEmpty() && bytes + size > 90_000) break;
                page.add(item); bytes += size; index++;
            }
            if (index < items.size()) {
                boolean korean=java.util.Locale.getDefault().getLanguage().equals("ko");
                page.add(folder(browseId + "|" + index, korean ? "더 보기" : "More", "", null));
            }
            request.patch_deliverAndroidAutoPlaylists(page);
            Logger.printInfo(() -> "AA music: delivered " + page.size() + " visible items; total="
                    + items.size() + ", source pages=" + pages);
        }

        void request(ListenableFuture<BrowseResponse> future) {
            future.addListener(() -> {
                try {
                    BrowseResponse response = future.get();
                    synchronized (this) {
                        if (delivered) return;
                        pages++;
                        Object next = null;
                        Object more = pages > 1 ? (library ? response.patch_getMorePlaylists() : response.patch_getMoreSongs()) : null;
                        if (more != null) next = collect(more);
                        for (BrowseTab tab : response.patch_getTabs()) {
                            SectionList sections = tab.patch_getSectionList();
                            if (sections == null) continue;
                            for (Object section : sections.patch_getContents()) {
                                Object continuation = collect(section);
                                if (next == null) next = continuation;
                            }
                        }
                        if (next != null && pages < 20 && continuations.add(next)) {
                            request(phone.patch_requestMorePlaylists(next, BACKGROUND_EXECUTOR));
                        } else finish();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    finish();
                } catch (ExecutionException | RuntimeException ex) {
                    Logger.printInfo(() -> "AA music: browse failed (" + ex.getClass().getSimpleName() + ") at "
                            + java.util.Arrays.toString(ex.getStackTrace()));
                    finish();
                }
            }, BACKGROUND_EXECUTOR);
        }

        Object collect(Object section) {
            if (section instanceof SerializedRenderer) {
                SerializedRenderer renderer=(SerializedRenderer)section;
                for (MusicHomeRenderer.Entry entry:MusicHomeRenderer.read(renderer.patch_getBytes())) {
                    try {
                        Uri artwork=entry.artwork.isEmpty() ? null : Uri.parse(entry.artwork);
                        if (!entry.browseId.isEmpty()) {
                            if (ids.add(entry.browseId)) items.add(folder(entry.browseId,entry.title,entry.subtitle,artwork));
                        } else {
                            String id=renderer.patch_createPlayableId(entry.command);
                            if (id!=null && ids.add(id)) items.add(createAndroidAutoPlaylist(id,entry.title,entry.subtitle,artwork));
                        }
                    } catch (RuntimeException ex) { Logger.printInfo(() -> "AA music: unsupported Home action"); }
                }
                return null;
            }
            if (section instanceof NestedContainer) {
                Object next = null;
                for (Object child : ((NestedContainer) section).patch_getChildren()) {
                    Object continuation = collect(child);
                    if (next == null) next = continuation;
                }
                return next;
            }
            if (section instanceof WrappedRenderer) return collect(((WrappedRenderer) section).patch_getRenderer());
            if (section instanceof PlaylistOrTrack) { add((PlaylistOrTrack) section); return null; }
            if (section instanceof GridRenderer) {
                GridRenderer grid = (GridRenderer) section;
                for (Object row : grid.patch_getRows()) if (row instanceof PlaylistOrTrack) add((PlaylistOrTrack) row);
                return firstContinuationAction(grid);
            }
            if (section instanceof OpenedPlaylistSongs) {
                for (PlaylistOrTrack row : ((OpenedPlaylistSongs) section).patch_getSongs()) add(row);
                Iterator<?> actions = ((OpenedPlaylistSongs) section).patch_getContinuationActions().iterator();
                return actions.hasNext() ? actions.next() : null;
            } else if (section != null) {
                // Structural diagnostics only; never log titles, IDs, account information or tokens.
                Logger.printInfo(() -> "AA music: unsupported section " + section.getClass().getName());
            }
            return null;
        }

        void add(PlaylistOrTrack row) {
            try { addRow(row); }
            catch (RuntimeException ex) {
                Logger.printInfo(() -> "AA music: skipped row (" + ex.getClass().getSimpleName() + ") at "
                        + java.util.Arrays.toString(ex.getStackTrace()));
            }
        }

        void addRow(PlaylistOrTrack row) {
            CharSequence title = row.patch_getTitle();
            if (title == null || title.length() == 0) return;
            String browseId = row.patch_getPlaylistBrowseId();
            if (EPISODES_FOR_LATER_BROWSE_ID.equals(browseId)) return;
            if (browseId != null) {
                if (ids.add(browseId)) items.add(folder(browseId, title.toString(), subtitleOrEmpty(row), artworkUriOrNull(row)));
            } else if (!library) {
                String playableId = row.patch_getPlayableMediaId();
                if (playableId != null && ids.add(playableId)) items.add(createAndroidAutoPlaylist(
                        playableId, title.toString(), subtitleOrEmpty(row), artworkUriOrNull(row)));
            }
        }
    }

}
