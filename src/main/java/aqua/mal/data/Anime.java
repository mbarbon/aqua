package aqua.mal.data;

import java.util.List;

public class Anime {
    public static final int AIRING = 1;
    public static final int FINISHED = 2;
    public static final int NOTAIRED = 3;

    public int animedbId;
    public String title;
    public String image;
    public int status;
    public int seriesType;
    public Franchise franchise;
    public int episodes;
    public List<String> genres;
    public long startedAiring;
    public long endedAiring;
    public boolean isHentai;

    public boolean isCompleted() {
        return status == FINISHED ||
            (status == AIRING && endedAiring >= System.currentTimeMillis() / 1000);
    }

    public boolean isAiring() {
        return status == AIRING && !isCompleted();
    }

    public boolean isOld() {
        return (System.currentTimeMillis() / 1000 - startedAiring) > 365 * 86400;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Anime))
            return false;
        return equals((Anime) o);
    }

    public boolean equals(Anime other) {
        return animedbId == other.animedbId;
    }

    @Override
    public int hashCode() {
        return animedbId;
    }
}
