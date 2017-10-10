package aqua.mal.data;

import io.protostuff.Tag;

public class RatedBase {
    public static final byte WATCHING = 1;
    public static final byte COMPLETED = 2;
    public static final byte ONOHOLD = 3;
    public static final byte DROPPED = 4;
    public static final byte PLANTOWATCH = 6;

    @Tag(1)
    public int animedbId;
    @Tag(2)
    public byte status;
    @Tag(3)
    public byte rating;
}
