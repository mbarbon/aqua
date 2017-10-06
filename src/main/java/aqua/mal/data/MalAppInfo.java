package aqua.mal.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "myanimelist")
public class MalAppInfo {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {
        @JsonProperty("user_id")
        public int userId;

        @JsonProperty("user_name")
        public String username;

        @JsonProperty("user_watching")
        public int watching;

        @JsonProperty("user_reading")
        public int reading;

        @JsonProperty("user_completed")
        public int completed;

        @JsonProperty("user_onhold")
        public int onhold;

        @JsonProperty("user_dropped")
        public int dropped;

        @JsonProperty("user_plantowatch")
        public int plantowatch;

        @JsonProperty("user_plantoread")
        public int plantoread;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RatedAnime {
        @JsonProperty("series_animedb_id")
        public int animedbId;

        @JsonProperty("series_title")
        public String title;

        @JsonProperty("series_type")
        public byte seriesType;

        @JsonProperty("series_episodes")
        public int episodes;

        @JsonProperty("series_status")
        public byte seriesStatus;

        @JsonProperty("series_start")
        public String start;

        @JsonProperty("series_end")
        public String end;

        @JsonProperty("series_image")
        public String image;

        @JsonProperty("my_score")
        public byte score;

        @JsonProperty("my_status")
        public byte userStatus;

        @JsonProperty("my_last_updated")
        public long lastUpdated;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RatedManga {
        @JsonProperty("series_mangadb_id")
        public int mangadbId;

        @JsonProperty("series_title")
        public String title;

        @JsonProperty("series_type")
        public byte seriesType;

        @JsonProperty("series_chapters")
        public int chapters;

        @JsonProperty("series_volumes")
        public int volumes;

        @JsonProperty("series_status")
        public byte seriesStatus;

        @JsonProperty("series_start")
        public String start;

        @JsonProperty("series_end")
        public String end;

        @JsonProperty("series_image")
        public String image;

        @JsonProperty("my_score")
        public byte score;

        @JsonProperty("my_status")
        public byte userStatus;

        @JsonProperty("my_last_updated")
        public long lastUpdated;
    }

    @JsonProperty("myinfo")
    public UserInfo user;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JsonProperty("anime")
    public List<RatedAnime> anime;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JsonProperty("manga")
    public List<RatedManga> manga;
}
