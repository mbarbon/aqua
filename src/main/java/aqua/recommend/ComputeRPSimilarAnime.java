package aqua.recommend;

import aqua.mal.data.Anime;

import com.google.common.collect.MinMaxPriorityQueue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.sparse.FlexCompRowMatrix;

import static aqua.recommend.MatrixUtils.reduceDimensionAndNormalize;

public class ComputeRPSimilarAnime {
    private final Map<Integer, Anime> animeMap;
    private final Map<Integer, Integer> animeIndexMap;
    private final int similarAnimeCount;
    private final int[] similarAnimeId;
    private final float[] similarAnimeScore;

    public ComputeRPSimilarAnime(Map<Integer, Anime> animeMap, Map<Integer, Integer> animeIndexMap, int similarAnimeCount) {
        this.animeMap = animeMap;
        this.animeIndexMap = animeIndexMap;
        this.similarAnimeCount = similarAnimeCount;
        this.similarAnimeId = new int[animeIndexMap.size() * similarAnimeCount];
        this.similarAnimeScore = new float[animeIndexMap.size() * similarAnimeCount];
    }

    public void findSimilarAnime(List<CFUser> users, int projectionSize) {
        FlexCompRowMatrix scores = fillScoreBuckets(users, animeIndexMap);
        DenseMatrix projectedScores = reduceDimensionAndNormalize(scores, projectionSize);

        fillSimilarAnime(projectedScores);
    }

    public RPSimilarAnime rpSimilarAnime() {
        return new RPSimilarAnime(animeIndexMap, similarAnimeCount, similarAnimeId, similarAnimeScore);
    }

    private static FlexCompRowMatrix fillScoreBuckets(List<CFUser> users, Map<Integer, Integer> animeIndexMap) {
        FlexCompRowMatrix scores = new FlexCompRowMatrix(animeIndexMap.size(), users.size());

        int userIndex = 0;
        for (CFUser user : users) {
            for (int i = 0; i < user.completedAndDroppedIds.length; ++i) {
                int animedbId = user.completedAndDroppedIds[i];
                Integer index = animeIndexMap.get(animedbId);
                if (index == null)
                    continue;
                float rating = user.completedAndDroppedRating[i];

                scores.set(index, userIndex, rating);
            }
            ++userIndex;
        }

        return scores;
    }

    private void fillSimilarAnime(DenseMatrix projectedScores) {
        int projectionSize = projectedScores.numColumns();
        DenseVector currentAnime = new DenseVector(projectionSize);
        DenseVector similarityScores = new DenseVector(projectedScores.numRows());

        for (Map.Entry<Integer, Integer> entry : animeIndexMap.entrySet()) {
            int animedbId = entry.getKey();
            int index = entry.getValue();

            for (int i = 0; i < projectionSize; ++i)
                currentAnime.set(i, projectedScores.get(index, i));

            projectedScores.mult(currentAnime, similarityScores);
            fillTopScores(index, similarityScores);
        }
    }

    private void fillTopScores(int animeIndex, DenseVector similarityScores) {
        MinMaxPriorityQueue<ScoredAnimeId> topScored =
            MinMaxPriorityQueue.orderedBy(ScoredAnimeId.SORT_SCORE)
                               .maximumSize(similarAnimeCount)
                               .create();

        for (Map.Entry<Integer, Integer> entry : animeIndexMap.entrySet()) {
            int animedbId = entry.getKey();
            int index = entry.getValue();
            double score = similarityScores.get(index);

            if (index == animeIndex || score < 0.2)
                continue;
            topScored.add(new ScoredAnimeId(animedbId, (float) -score));
        }

        Set<Integer> seenFranchises = new HashSet<>();
        int currentIndex = animeIndex * similarAnimeCount;
        while (!topScored.isEmpty()) {
            ScoredAnimeId scoredAnimeId = topScored.removeFirst();
            Anime anime = animeMap.get(scoredAnimeId.animedbId);
            if (anime.franchise != null) {
                if (seenFranchises.contains(anime.franchise.franchiseId))
                    continue;
                seenFranchises.add(anime.franchise.franchiseId);
            }
            similarAnimeId[currentIndex] = scoredAnimeId.animedbId;
            similarAnimeScore[currentIndex] = scoredAnimeId.score;
            ++currentIndex;
        }
    }
}
