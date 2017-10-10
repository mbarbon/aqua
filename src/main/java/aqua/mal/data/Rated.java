package aqua.mal.data;

import io.protostuff.Tag;

import java.lang.Comparable;

public class Rated extends RatedBase implements Comparable<Rated> {
    @Tag(4)
    public short completedDay;

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
