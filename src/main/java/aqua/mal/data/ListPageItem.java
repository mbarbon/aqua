package aqua.mal.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ListPageItem {
    private static final Pattern IMAGE = Pattern
            .compile("^(https://cdn\\.myanimelist\\.net)/.*(/images/(?:anime|manga)/\\d+/.*\\.(?:jpg|webp)).*$");

    private static String convertImage(String url) {
        Matcher matcher = IMAGE.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1) + matcher.group(2);
        } else if (url.contains("qm_50.gif")) {
            return "";
        }
        throw new IllegalArgumentException("Unrecognized CDN URL '" + url + "'");
    }

    private static String convertDate(String date) {
        if (date == null) {
            return "0000-00-00";
        } else if (date.length() != 8 || date.charAt(2) != '-' || date.charAt(5) != '-') {
            throw new IllegalArgumentException("Unrecognized date '" + date + "'");
        }

        if (date.charAt(6) < '5') {
            return "20" + date.substring(6, 8) + "-" + date.substring(0, 2) + "-" + date.substring(3, 5);
        } else {
            return "19" + date.substring(6, 8) + "-" + date.substring(0, 2) + "-" + date.substring(3, 5);
        }
    }

    private static byte seriesType(String type) {
        switch (type) {
            case "TV":
                return Anime.TV;
            case "OVA":
                return Anime.OVA;
            case "Movie":
                return Anime.MOVIE;
            case "Special":
                return Anime.SPECIAL;
            case "ONA":
                return Anime.ONA;
            case "Music":
                return Anime.MUSIC;

            case "Manga":
                return Manga.MANGA;
            case "Novel":
                return Manga.NOVEL;
            case "One-shot":
                return Manga.ONESHOT;
            case "Doujinshi":
                return Manga.DOUJINSHI;
            case "Manhwa":
                return Manga.MANHWA;
            case "Manhua":
                return Manga.MANHUA;
        }

        return 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnimePageItem {
        @JsonProperty("anime_id")
        public int animedbId;

        @JsonProperty("anime_title")
        public String title;

        @JsonProperty("anime_media_type_string")
        public String seriesTypeString;

        @JsonProperty("anime_num_episodes")
        public int episodes;

        @JsonProperty("anime_airing_status")
        public byte seriesStatus;

        @JsonProperty("anime_start_date_string")
        public String start;

        @JsonProperty("anime_end_date_string")
        public String end;

        @JsonProperty("anime_image_path")
        public String image;

        @JsonProperty("score")
        public byte score;

        @JsonProperty("status")
        public byte userStatus;

        public MalAppInfo.RatedAnime toMalAppInfo() {
            MalAppInfo.RatedAnime item = new MalAppInfo.RatedAnime();

            item.animedbId = animedbId;
            item.title = title;
            item.seriesType = seriesType(seriesTypeString);
            item.episodes = episodes;
            item.seriesStatus = seriesStatus;
            item.start = convertDate(start);
            item.end = convertDate(end);
            item.image = convertImage(image);
            item.score = score;
            item.userStatus = userStatus;

            return item;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MangaPageItem {
        @JsonProperty("manga_id")
        public int mangadbId;

        @JsonProperty("manga_title")
        public String title;

        @JsonProperty("manga_media_type_string")
        public String seriesTypeString;

        @JsonProperty("manga_num_chapters")
        public int chapters;

        @JsonProperty("manga_num_volumes")
        public int volumes;

        @JsonProperty("manga_publishing_status")
        public byte seriesStatus;

        @JsonProperty("manga_start_date_string")
        public String start;

        @JsonProperty("manga_end_date_string")
        public String end;

        @JsonProperty("manga_image_path")
        public String image;

        @JsonProperty("score")
        public byte score;

        @JsonProperty("status")
        public byte userStatus;

        public MalAppInfo.RatedManga toMalAppInfo() {
            MalAppInfo.RatedManga item = new MalAppInfo.RatedManga();

            item.mangadbId = mangadbId;
            item.title = title;
            item.seriesType = seriesType(seriesTypeString);
            item.chapters = chapters;
            item.volumes = volumes;
            item.seriesStatus = seriesStatus;
            item.start = convertDate(start);
            item.end = convertDate(end);
            item.image = convertImage(image);
            item.score = score;
            item.userStatus = userStatus;

            return item;
        }
    }

    public static List<MalAppInfo.RatedAnime> convertAnimeList(List<AnimePageItem> items) {
        return items.stream().map(AnimePageItem::toMalAppInfo).collect(Collectors.toList());
    }

    public static List<MalAppInfo.RatedManga> convertMangaList(List<MangaPageItem> items) {
        return items.stream().map(MangaPageItem::toMalAppInfo).collect(Collectors.toList());
    }

    public static MalAppInfo makeAnimeList(int userId, String username, long lastUpdated, List<AnimePageItem> items) {
        MalAppInfo appInfo = new MalAppInfo();
        MalAppInfo.UserInfo user = new MalAppInfo.UserInfo();

        appInfo.user = user;
        appInfo.anime = convertAnimeList(items);
        appInfo.lastUpdatedProfile = lastUpdated;

        user.userId = userId;
        user.username = username;

        for (MalAppInfo.RatedAnime anime : appInfo.anime) {
            switch (anime.userStatus) {
                case RatedBase.PLANTOWATCH:
                    user.plantowatch++;
                    break;
                case RatedBase.WATCHING:
                    user.watching++;
                    break;
                case RatedBase.COMPLETED:
                    user.completed++;
                    break;
                case RatedBase.DROPPED:
                    user.dropped++;
                    break;
                case RatedBase.ONOHOLD:
                    user.onhold++;
                    break;
            }
        }

        return appInfo;
    }

    public static MalAppInfo makeMangaList(int userId, String username, long lastUpdated, List<MangaPageItem> items) {
        MalAppInfo appInfo = new MalAppInfo();
        MalAppInfo.UserInfo user = new MalAppInfo.UserInfo();

        appInfo.user = user;
        appInfo.manga = convertMangaList(items);
        appInfo.lastUpdatedProfile = lastUpdated;

        user.userId = userId;
        user.username = username;

        for (MalAppInfo.RatedManga anime : appInfo.manga) {
            switch (anime.userStatus) {
                case RatedBase.PLANTOWATCH:
                    user.plantoread++;
                    break;
                case RatedBase.WATCHING:
                    user.reading++;
                    break;
                case RatedBase.COMPLETED:
                    user.completed++;
                    break;
                case RatedBase.DROPPED:
                    user.dropped++;
                    break;
                case RatedBase.ONOHOLD:
                    user.onhold++;
                    break;
            }
        }

        return appInfo;
    }
}
