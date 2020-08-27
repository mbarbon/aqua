package aqua.mal.data;

import java.util.List;

public class Manga extends Item {
    public static final int MANGA = 1;
    public static final int NOVEL = 2;
    public static final int ONESHOT = 3;
    public static final int DOUJINSHI = 4;
    public static final int MANHWA = 5;
    public static final int MANHUA = 6;

    public int mangadbId;
    public String title;
    public int status;
    public int seriesType;
    public int chapters;
    public int volumes;
    public List<String> genres;
    public long startedPublishing;
    public long endedPublishing;
    public boolean isHentai;

    @Override
    public int itemId() {
        return mangadbId;
    }
}
