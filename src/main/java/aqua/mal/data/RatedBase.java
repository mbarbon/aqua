package aqua.mal.data;

public class RatedBase {
    public static final byte WATCHING = 1;
    public static final byte COMPLETED = 2;
    public static final byte ONOHOLD = 3;
    public static final byte DROPPED = 4;
    public static final byte PLANTOWATCH = 6;

    public int animedbId;
    public byte status;
    public byte rating;
}
