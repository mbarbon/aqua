package aqua.mal.data;

import java.util.Comparator;

import io.protostuff.Tag;

public class RatedBase {
    public static final byte WATCHING = 1;
    public static final byte READING = 1;
    public static final byte COMPLETED = 2;
    public static final byte ONOHOLD = 3;
    public static final byte DROPPED = 4;
    public static final byte PLANTOWATCH = 6;
    public static final byte PLANTOREAD = 6;

    @Tag(1)
    public int animedbId;
    @Tag(2)
    public byte status;
    @Tag(3)
    public byte rating;

    public static int descendingRating(RatedBase a, RatedBase b) {
        return Byte.compare(b.rating, a.rating);
    }

    public static class StableDescendingRating implements Comparator<RatedBase> {
        private final int seed;

        public StableDescendingRating(int seed) {
            this.seed = seed;
        }

        @Override
        public int compare(RatedBase a, RatedBase b) {
            int delta = Byte.compare(b.rating, a.rating);
            if (delta != 0) {
                return delta;
            }
            return Integer.compare(a.animedbId ^ seed, b.animedbId ^ seed);
        }
    }
}
