package aqua.recommend;

import aqua.mal.data.User;
import aqua.mal.data.Rated;

import java.util.Iterator;

public class Cosine {
    private final User usera;
    private final double sumSquaresA;

    public Cosine(User usera) {
        this.usera = usera;
        this.sumSquaresA = sumSquares(usera);
    }

    public double userSimilarity(User userb) {
        if (usera.equals(userb) || sumSquaresA == 0)
            return 1;
        double sumSquaresB = sumSquares(userb);
        if (sumSquaresB == 0)
            return 1;
        double productSum = productSum(usera, userb);
        return -productSum / (sumSquaresA * sumSquaresB);
    }

    private static double sumSquares(User user) {
        double sum = 0.0;
        for (Rated item : user.completedAndDropped())
            sum += item.normalizedRating * item.normalizedRating;
        return Math.sqrt(sum);
    }

    private static double productSum(User usera, User userb) {
        Iterator<Rated> ratedA = usera.completedAndDropped().iterator();
        Iterator<Rated> ratedB = userb.completedAndDropped().iterator();
        if (!ratedA.hasNext() || !ratedB.hasNext())
            return 0;
        Rated currentA = null;
        Rated currentB = null;
        double sum = 0.0;

        for (;;) {
            if (currentA == null) {
                if (!ratedA.hasNext())
                    break;
                currentA = ratedA.next();
            }
            if (currentB == null) {
                if (!ratedB.hasNext())
                    break;
                currentB = ratedB.next();
            }

            int order = currentA.animedbId - currentB.animedbId;
            if (order == 0) {
                sum += currentA.normalizedRating * currentB.normalizedRating;
                currentA = currentB = null;
            } else if (order > 0) {
                currentB = null;
            } else {
                currentA = null;
            }
        }

        return sum;
    }
}
