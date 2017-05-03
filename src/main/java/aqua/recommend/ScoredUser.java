package aqua.recommend;

import java.util.Comparator;

public class ScoredUser {
    public static final Comparator<ScoredUser> SORT_SCORE = new Comparator<ScoredUser>() {
        @Override
        public int compare(ScoredUser a, ScoredUser b) {
            return Float.compare(a.score, b.score);
        }
    };

    public CFUser user;
    public float score;

    public ScoredUser(CFUser user, float score) {
        this.user = user;
        this.score = score;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(user.userId);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScoredUser))
            return false;
        return equals((ScoredUser) o);
    }

    public boolean equals(ScoredUser other) {
        return user.userId == other.user.userId;
    }
}
