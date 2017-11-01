package aqua.recommend;

import aqua.mal.data.Anime;

import java.util.Arrays;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class ComputeLatentFactorDecompositionItems {
    private final Map<Integer, Anime> animeMap;
    private final LatentFactorDecomposition completed;
    private final LatentFactorDecomposition factors;
    private final int similarAnimeCount;
    // actually those are square matrices with one row per completed anime
    // and similarAnimeCount columns
    public final int[] similarAnimeId;
    public final float[] similarAnimeScore;

    public ComputeLatentFactorDecompositionItems(Map<Integer, Anime> animeMap, LatentFactorDecomposition completed, LatentFactorDecomposition factors, int similarAnimeCount) {
        this.animeMap = animeMap;
        this.completed = completed;
        this.factors = factors;
        this.similarAnimeCount = similarAnimeCount;
        this.similarAnimeId = new int[completed.animeCount() * similarAnimeCount];
        this.similarAnimeScore = new float[completed.animeCount() * similarAnimeCount];
    }

    public void findSimilarAnime() {
        fillSimilarityMatrix();
    }

    public LatentFactorDecompositionItems lfdItems() {
        return new LatentFactorDecompositionItems(completed, factors, similarAnimeCount, similarAnimeId, similarAnimeScore);
    }

    private void fillSimilarityMatrix() {
        int animeCount = factors.animeRatedMap.length;
        for (int i = 0; i < completed.animeCount(); ++i) {
            ScoredAnimeId[] similar = new ScoredAnimeId[animeCount];
            for (int j = 0; j < animeCount; ++j) {
                similar[j] = new ScoredAnimeId(
                    factors.animeRatedMap[j],
                    -itemSimilarity(completed, i, factors, j)
                );
            }
            Arrays.sort(similar, ScoredAnimeId.SORT_SCORE);
            Set<Integer> seenFranchises = new HashSet<>();
            addFranchise(completed.animeRatedMap[i], seenFranchises);
            int index = i * similarAnimeCount;
            for (int j = 0; j < similarAnimeCount; ++j) {
                ScoredAnimeId scored = similar[j];
                if (addFranchise(scored.animedbId, seenFranchises))
                    continue;
                similarAnimeId[index] = scored.animedbId;
                similarAnimeScore[index] = scored.score;
                ++index;
            }
        }
    }

    private static float itemSimilarity(LatentFactorDecomposition items1, int index1, LatentFactorDecomposition items2, int index2) {
        int rank = items1.rank();
        double product = 0;
        for (int i = 0; i < rank; ++i) {
            product += items1.animeFactors.get(index1, i) * items2.animeFactors.get(index2, i);
        }
        return (float) product;
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
