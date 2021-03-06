package org.willemsens.player.fetchers.musicbrainz;

import android.support.annotation.NonNull;

import org.willemsens.player.exceptions.NetworkClientException;
import org.willemsens.player.exceptions.NetworkServerException;
import org.willemsens.player.exceptions.PlayerException;
import org.willemsens.player.fetchers.AlbumInfo;
import org.willemsens.player.fetchers.ArtistInfo;
import org.willemsens.player.fetchers.InfoFetcher;
import org.willemsens.player.fetchers.UnparsedResponse;
import org.willemsens.player.fetchers.musicbrainz.dto.ArtistsResponse;
import org.willemsens.player.fetchers.musicbrainz.dto.ImagesReponse;
import org.willemsens.player.fetchers.musicbrainz.dto.Release;
import org.willemsens.player.fetchers.musicbrainz.dto.ReleasesResponse;

import okhttp3.HttpUrl;
import okhttp3.Request;

public class MusicbrainzInfoFetcher extends InfoFetcher {
    @Override
    public Request getRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Player/0.1 ( https://github.com/larsdroid/Player )")
                .addHeader("Accept", "application/json")
                .build();
    }

    @Override
    @NonNull
    public String fetchArtistId(String artistName) throws NetworkClientException, NetworkServerException {
        final HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("musicbrainz.org")
                .addPathSegment("ws")
                .addPathSegment("2")
                .addPathSegment("artist")
                .addQueryParameter("query", "artist:" + sanitizeSearchString(artistName))
                .build();
        final UnparsedResponse unparsedResponse = fetch(url);
        final ArtistsResponse artistsResponse = getGson().fromJson(
                unparsedResponse.getJson(),
                ArtistsResponse.class);
        return artistsResponse.getFirstArtistID();
    }

    @Override
    @NonNull
    public AlbumInfo fetchAlbumInfo(String artistName, String albumName) throws NetworkClientException, NetworkServerException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("musicbrainz.org")
                .addPathSegment("ws")
                .addPathSegment("2")
                .addPathSegment("release")
                //.addQueryParameter("query", "release:" + albumName + " AND arid:" + artistId)
                .addQueryParameter("query", "release:" + sanitizeAlbumSearchString(albumName) + " AND artist:" + sanitizeSearchString(artistName))
                .build();

        UnparsedResponse unparsedResponse = fetch(url);
        final ReleasesResponse releasesResponse = getGson().fromJson(
                unparsedResponse.getJson(),
                ReleasesResponse.class);
        final Release[] releases = releasesResponse.getReleases();

        if (releases != null) {
            for (Release release : releases) {
                url = new HttpUrl.Builder()
                        .scheme("http")
                        .host("coverartarchive.org")
                        .addPathSegment("release")
                        .addPathSegment(release.getId())
                        .build();

                unparsedResponse = fetch(url);
                if (unparsedResponse.getHttpStatusCode() != 404) {
                    ImagesReponse imagesReponse = getGson().fromJson(unparsedResponse.getJson(), ImagesReponse.class);

                    return new AlbumInfo(
                            imagesReponse.getFirstLargeThumbnail(),
                            releasesResponse.getOldestReleaseYear());
                }
            }
        }

        return new AlbumInfo(
                null,
                releasesResponse.getOldestReleaseYear());
    }

    @Override
    @NonNull
    public ArtistInfo fetchArtistInfo(String artistId) {
        throw new PlayerException("Fetching artist images is not supported by Musicbrainz.");
    }

    private String sanitizeAlbumSearchString(String string) {
        // Remove ending "Disc X"
        string = string.replaceAll("(?i)\\s+disc\\s+\\d{1,2}$", "");

        string = sanitizeSearchString(string);

        // Remove ending "Disc X"
        string = string.replaceAll("(?i)\\s+disc\\s+\\d{1,2}$", "");

        return string;
    }

    @Override
    protected String sanitizeSearchString(String string) {
        string = super.sanitizeSearchString(string);

        // Just a few Apache Lucene escapes
        string = string.replaceAll("\\(", "\\\\(");
        string = string.replaceAll("\\)", "\\\\)");
        string = string.replaceAll("\\?", "\\\\?");
        return string.replaceAll("/", "\\\\/");
    }
}
