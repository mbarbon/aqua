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
        for (Rated item : user.completedAndDroppedArray)
            sum += item.normalizedRating * item.normalizedRating;
        return Math.sqrt(sum);
    }

    private static double productSum(User usera, User userb) {
        Rated[] ratedA = usera.completedAndDroppedArray;
        Rated[] ratedB = userb.completedAndDroppedArray;
        int indexA = 0, indexB = 0, maxA = ratedA.length, maxB = ratedB.length;
        if (maxA == 0 || maxB == 0)
            return 0;
        Rated currentA = ratedA[indexA++];
        Rated currentB = ratedB[indexB++];
        double sum = 0.0;

        for (;;) {
            int order = currentA.animedbId - currentB.animedbId;
            if (order == 0) {
                sum += currentA.normalizedRating * currentB.normalizedRating;

                if (indexA == maxA)
                    break;
                currentA = ratedA[indexA++];
                if (indexB == maxB)
                    break;
                currentB = ratedB[indexB++];
            } else if (order > 0) {
                if (indexB == maxB)
                    break;
                currentB = ratedB[indexB++];
            } else {
                if (indexA == maxA)
                    break;
                currentA = ratedA[indexA++];
            }
        }

        return sum;
    }
}
