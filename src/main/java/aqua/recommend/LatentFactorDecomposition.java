package aqua.recommend;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static aqua.recommend.MatrixUtils.partialTransAmult;

public class LatentFactorDecomposition {
    public final double lambda;
    public final DenseMatrix animeFactors;
    private final Map<Integer, Integer> animeIndexMap;
    public final int[] animeRatedMap; // inverse for animeIndexMap

    public LatentFactorDecomposition(double lambda, Map<Integer, Integer> animeIndexMap, int[] animeRatedMap, DenseMatrix animeFactors) {
        this.lambda = lambda;
        this.animeFactors = animeFactors;
        this.animeIndexMap = animeIndexMap;
        this.animeRatedMap = animeRatedMap;
    }

    public LatentFactorDecomposition(double lambda, int[] animeRatedMap, DenseMatrix animeFactors) {
        this.lambda = lambda;
        this.animeFactors = animeFactors;
        this.animeIndexMap = new HashMap<>();
        this.animeRatedMap = animeRatedMap;
        for (int i = 0; i < animeRatedMap.length; ++i)
            animeIndexMap.put(animeRatedMap[i], i);
    }

    public int rank() {
        return animeFactors.numColumns();
    }

    public double[] computeUserVector(CFUser user) {
        int rank = rank();
        int indexCount = user.completedAndDroppedIds.length;
        DenseMatrix linearSystemCoefficients = new DenseMatrix(rank, rank);
        DenseVector linearSystemValues = new DenseVector(rank);
        DenseVector linearSystemSolution = new DenseVector(rank);
        double weightedLambda = lambda * indexCount;

        int[] animeIndices = new int[indexCount];
        DenseVector animeRatingSlice = new DenseVector(animeFactors.numRows());

        for (int i = 0; i < indexCount; ++i) {
            Integer animeIndex = animeIndexMap.get(user.completedAndDroppedIds[i]);
            if (animeIndex == null)
                continue;
            double rating = user.completedAndDroppedRating[i];

            animeRatingSlice.set(animeIndex, rating);
            animeIndices[i] = animeIndex;
        }

        // duplicated in ComputeLatentFactorDecomposition.userStep
        partialTransAmult(animeFactors, animeIndices, indexCount, animeFactors, linearSystemCoefficients);
        for (int i = 0; i < rank; ++i)
            linearSystemCoefficients.add(i, i, weightedLambda);

        partialTransAmult(animeFactors, animeIndices, indexCount, animeRatingSlice, linearSystemValues);

        linearSystemCoefficients.solve(linearSystemValues, linearSystemSolution);

        return linearSystemSolution.getData();
    }

    public List<ScoredAnimeId> computeUserAnimeScores(double[] userFactors) {
        DenseVector userVector = new DenseVector(userFactors, false);
        DenseVector scores = new DenseVector(animeRatedMap.length);

        animeFactors.mult(userVector, scores);

        List<ScoredAnimeId> scoredAnime = new ArrayList<>(animeRatedMap.length);
        for (int i = 0; i < animeRatedMap.length; ++i) {
            scoredAnime.add(new ScoredAnimeId(animeRatedMap[i], -(float) scores.get(i)));
        }

        return scoredAnime;
    }
}
