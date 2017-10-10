package aqua.recommend;

import aqua.mal.data.RatedBase;

import java.lang.Comparable;

public class CFRated extends RatedBase implements Comparable<CFRated>, RecommendationItem {
    // for deserialization
    public CFRated() {
    }

    public CFRated(int animedbId, byte status, byte rating) {
        this.animedbId = animedbId;
        this.status = status;
        this.rating = rating;
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
        return animedbId == other.animedbId &&
            status == other.status &&
            rating == other.rating;
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
}
