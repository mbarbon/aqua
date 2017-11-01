package aqua.recommend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LatentFactorDecompositionItems {
    private static final int ITEM_MIN = 10;
    private static final int ITEM_MAX = 20;

    private final LatentFactorDecomposition factors;
    private final Map<Integer, Integer> animeIndexMap;
    public final int similarAnimeCount;
    // actually those are square matrices with one row per completed anime
    // and similarAnimeCount columns
    public final int[] similarAnimeId;
    public final float[] similarAnimeScore;

    public LatentFactorDecompositionItems(LatentFactorDecomposition completedAnime, LatentFactorDecomposition factors, int similarAnimeCount, int[] similarAnimeId, float[] similarAnimeScore) {
        this.factors = factors;
        this.similarAnimeCount = similarAnimeCount;
        animeIndexMap = completedAnime.animeIndexMap;
        this.similarAnimeId = similarAnimeId;
        this.similarAnimeScore = similarAnimeScore;
    }

    public List<ScoredAnimeId> similarAnime(int animedbId) {
        Integer index = animeIndexMap.get(animedbId);
        if (index == null)
            return new ArrayList<>();
        List<ScoredAnimeId> result = new ArrayList<>(similarAnimeCount);

        for (int i = 0, currentIndex = index * similarAnimeCount; i < similarAnimeCount; ++i, ++currentIndex) {
            if (similarAnimeId[currentIndex] == 0)
                break;
            result.add(new ScoredAnimeId(similarAnimeId[currentIndex], similarAnimeScore[currentIndex]));
        }

        return result;
    }

    public List<ScoredAnimeId> findSimilarAnime(CFUser user) {
        List<ScoredAnimeId> sortedScores = new ArrayList<>(user.completedAndDroppedIds.length);
        for (int i = 0; i < user.completedAndDroppedIds.length; ++i) {
            sortedScores.add(new ScoredAnimeId(user.completedAndDroppedIds[i], user.completedAndDroppedRating[i]));
        }
        sortedScores.sort(ScoredAnimeId.SORT_SCORE);
        int maxUse = Math.min(ITEM_MAX, sortedScores.size());
        int minUse = Math.min(ITEM_MIN, sortedScores.size());
        List<ScoredAnimeId> picked = new ArrayList<>(maxUse);
        for (int i = 0; i < maxUse; ++i) {
            picked.add(sortedScores.get(sortedScores.size() - 1 - i));
        }
        Collections.shuffle(picked);
        int use = (int) (Math.random() * (maxUse + 1 - minUse)) + minUse;
        Map<Integer, ScoredAnimeId> recommendations = new HashMap<>();
        for (int i = 0; i < use; ++i) {
            ScoredAnimeId likedItem = picked.get(i);
            Integer likedAnimeIndex = animeIndexMap.get(likedItem.animedbId);
            if (likedAnimeIndex == null)
                continue;
            for (int j = 0, index = likedAnimeIndex * similarAnimeCount; j < similarAnimeCount; ++j, ++index) {
                int animedbId = similarAnimeId[index];
                if (animedbId == 0)
                    continue;
                float score = similarAnimeScore[index] * likedItem.score;
                ScoredAnimeId recommendation = recommendations.computeIfAbsent(animedbId, id -> new ScoredAnimeId(id, 0.0f));
                recommendation.score += score;
            }
        }
        return new ArrayList<>(recommendations.values());
    }
}
