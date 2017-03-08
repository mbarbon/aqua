package aqua.mal.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.lang.Comparable;

public class Rated implements Comparable<Rated> {
    public static final byte WATCHING = 1;
    public static final byte COMPLETED = 2;
    public static final byte ONOHOLD = 3;
    public static final byte DROPPED = 4;
    public static final byte PLANTOWATCH = 6;

    public int animedbId;
    public byte status;
    public byte rating;
    public short completedDay;

    @JsonIgnore
    public float normalizedRating;

    public Rated() {
    }

    public Rated(int animedbId, byte status, byte rating, short completedDay) {
        this.animedbId = animedbId;
        this.status = status;
        this.rating = rating;
        if (status == COMPLETED || status == DROPPED)
            this.completedDay = completedDay;
        else
            this.completedDay = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Rated))
            return false;
        return equals((Rated) o);
    }

    public boolean equals(Rated other) {
        return animedbId == other.animedbId;
    }

    @Override
    public int hashCode() {
        return animedbId;
    }

    @Override
    public int compareTo(Rated other) {
        return animedbId - other.animedbId;
    }
}
