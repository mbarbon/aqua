package aqua.recommend;

import aqua.mal.data.User;
import aqua.mal.data.Rated;

import java.util.Iterator;

public class Pearson {
    private final User usera;
    private final int minCommonItems;

    public Pearson(User usera, int minCommonItems) {
        this.usera = usera;
        this.minCommonItems = minCommonItems;
    }

    public double userSimilarity(User userb) {
        if (usera.equals(userb))
            return 1;
        Iterator<Rated> ratedA = usera.completedAndDropped().iterator();
        Iterator<Rated> ratedB = userb.completedAndDropped().iterator();
        if (!ratedA.hasNext() || !ratedB.hasNext())
            return 0;
        Rated currentA = null;
        Rated currentB = null;
        double productSum = 0.0, sumSquaresA = 0.0, sumSquaresB = 0.0;
        int count = 0, countA = 0, countB = 0;

        for (;;) {
            if (currentA == null) {
                if (!ratedA.hasNext())
                    break;
                currentA = ratedA.next();
                ++countA;
            }
            if (currentB == null) {
                if (!ratedB.hasNext())
                    break;
                currentB = ratedB.next();
                ++countB;
            }

            int order = currentA.animedbId - currentB.animedbId;
            if (order == 0) {
                productSum += currentA.normalizedRating * currentB.normalizedRating;
                sumSquaresA += currentA.normalizedRating * currentA.normalizedRating;
                sumSquaresB += currentB.normalizedRating * currentB.normalizedRating;
                ++count;
                currentA = currentB = null;
            } else if (order > 0) {
                currentB = null;
            } else {
                currentA = null;
            }
        }

        if (100 * count / countA < minCommonItems ||
                100 * count / countB < minCommonItems)
            return 1;
        if (sumSquaresA == 0.0 || sumSquaresB == 0.0)
            return 1;
        return -productSum / (Math.sqrt(sumSquaresA) * Math.sqrt(sumSquaresB));
    }
}
