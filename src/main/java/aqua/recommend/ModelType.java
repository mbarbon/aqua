package aqua.recommend;

public enum ModelType {
    COMPLETED_ANIME, AIRING_ANIME, ANIME, MANGA;

    public boolean isManga() {
        return this == MANGA;
    }

    public boolean isAnime() {
        return this != MANGA;
    }

    public static ModelType fromString(String name) {
        switch (name) {
            case "anime":
                return ANIME;
            case "manga":
                return MANGA;
            default:
                throw new IllegalArgumentException("Invalid value: " + name);
        }
    }
}