package aqua.recommend;

import aqua.mal.data.Anime;

import com.google.common.collect.MinMaxPriorityQueue;

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

    public ItemItemModel rpSimilarAnime() {
        return new ItemItemModel(animeIndexMap, similarAnimeCount, similarAnimeId, similarAnimeScore);
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
                float rating = user.completedAndDroppedRatingFloat(i);

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
            fillTopScores(animedbId, index, similarityScores);
        }
    }

    private void fillTopScores(int animedbId, int animeIndex, DenseVector similarityScores) {
        MinMaxPriorityQueue<ScoredAnimeId> topScored =
            MinMaxPriorityQueue.orderedBy(ScoredAnimeId.SORT_SCORE)
                               .maximumSize(similarAnimeCount)
                               .create();

        for (Map.Entry<Integer, Integer> entry : animeIndexMap.entrySet()) {
            int entryAnimedbId = entry.getKey();
            int entryIndex = entry.getValue();
            double score = similarityScores.get(entryIndex);

            if (entryIndex == animeIndex || score < 0.2)
                continue;
            topScored.add(new ScoredAnimeId(entryAnimedbId, (float) -score));
        }

        Set<Integer> seenFranchises = new HashSet<>();
        addFranchise(animedbId, seenFranchises);
        int currentIndex = animeIndex * similarAnimeCount;
        while (!topScored.isEmpty()) {
            ScoredAnimeId scoredAnimeId = topScored.removeFirst();
            if (addFranchise(scoredAnimeId.animedbId, seenFranchises))
                continue;
            similarAnimeId[currentIndex] = scoredAnimeId.animedbId;
            similarAnimeScore[currentIndex] = scoredAnimeId.score;
            ++currentIndex;
        }
    }

    private boolean addFranchise(int animedbId, Set<Integer> seenFranchises) {
        Anime anime = animeMap.get(animedbId);
        if (anime != null && anime.franchise != null) {
            if (seenFranchises.contains(anime.franchise.franchiseId))
                return true;
            seenFranchises.add(anime.franchise.franchiseId);
        }
        return false;
    }
}
