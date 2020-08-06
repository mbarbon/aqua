package aqua.recommend;

import aqua.mal.data.RatedBase;

import java.lang.Comparable;
import java.util.List;

public class CFRated extends RatedBase implements Comparable<CFRated>, RecommendationItem {
    // for deserialization
    public CFRated() {
    }

    public CFRated(int animedbId, byte status, byte rating) {
        this.animedbId = animedbId;
        this.status = status;
        this.rating = rating;
    }

    public CFRated(int packed) {
        unpackIntTo(packed, this);
    }

    @Override
    public int animedbId() {
        return Math.abs(animedbId);
    }

    @Override
    public boolean isHentai() {
        return animedbId < 0;
    }

    public void setHentai() {
        animedbId = -Math.abs(animedbId);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CFRated))
            return false;
        return equals((CFRated) o);
    }

    public boolean equals(CFRated other) {
        return animedbId == other.animedbId && status == other.status && rating == other.rating;
    }

    @Override
    public int hashCode() {
        return animedbId * 17 + status + 13 + rating;
    }

    @Override
    public int compareTo(CFRated other) {
        if (animedbId != other.animedbId)
            return animedbId - other.animedbId;
        if (status != other.status)
            return status - other.status;
        if (rating != other.rating)
            return rating - other.rating;
        return 0;
    }

    public int packToInt() {
        int absId = animedbId();

        return (absId << 9) | (isHentai() ? 0x100 : 0) | (rating << 4) | status;
    }

    public static void unpackIntTo(int packed, CFRated target) {
        boolean isHentai = (packed & 0x100) != 0;
        int absId = packed >>> 9;

        target.animedbId = isHentai ? -absId : absId;
        target.rating = (byte) ((packed >> 4) & 0xf);
        target.status = (byte) (packed & 0xf);
    }

    public static int unpackAnimeId(int packed) {
        return packed >>> 9;
    }

    public static int unpackRating(int packed) {
        return (packed >> 4) & 0xf;
    }

    public static int unpackStatus(int packed) {
        return packed & 0xf;
    }

    public static int[] packAnimeIdArray(CFRated[] ratings) {
        int[] result = new int[ratings.length];
        for (int i = 0, max = ratings.length; i < max; ++i) {
            result[i] = ratings[i].packToInt();
        }
        return result;
    }

    public static int[] packAnimeIdList(List<CFRated> ratings) {
        int[] result = new int[ratings.size()];
        int i = 0;
        for (CFRated rating : ratings) {
            result[i++] = rating.packToInt();
        }
        return result;
    }
}
