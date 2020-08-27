package aqua.mal.data;

import java.util.List;

public class Anime extends Item {
    public static final int TV = 1;
    public static final int OVA = 2;
    public static final int MOVIE = 3;
    public static final int SPECIAL = 4;
    public static final int ONA = 5;
    public static final int MUSIC = 6;

    public static final int AIRING = 1;
    public static final int FINISHED = 2;
    public static final int NOTAIRED = 3;

    public int animedbId;
    public String title;
    public int status;
    public int seriesType;
    public int episodes;
    public List<String> genres;
    public long startedAiring;
    public long endedAiring;

    @Override
    public int itemId() {
        return animedbId;
    }

    public boolean isCompleted() {
        return status == FINISHED || (status == AIRING && endedAiring >= System.currentTimeMillis() / 1000);
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
