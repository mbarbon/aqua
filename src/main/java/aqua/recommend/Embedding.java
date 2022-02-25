package aqua.recommend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import aqua.mal.data.Item;

public class Embedding {
    private final int rank;
    private final int[] animeIndices;
    private final float[] embedding;

    public Embedding(int rank, int[] animeIndices, float[] embedding) {
        this.rank = rank;
        this.animeIndices = animeIndices;
        this.embedding = embedding;
    }

    public ItemItemModel simpleItemItem(Map<Integer, Item> itemMap, int similarAnimeCount) {
        int[] similarAnimeId = new int[animeIndices.length * similarAnimeCount];
        float[] similarAnimeScore = new float[animeIndices.length * similarAnimeCount];

        fillSimilarAnime(itemMap, similarAnimeCount, similarAnimeId, similarAnimeScore);

        return new ItemItemModel(animeIndices, similarAnimeCount, similarAnimeId, similarAnimeScore);
    }

    private void fillSimilarAnime(Map<Integer, Item> itemMap, int similarAnimeCount, int[] similarAnimeId,
            float[] similarAnimeScore) {
        for (int i = 0, rowStart = 0; rowStart < embedding.length; ++i, rowStart += rank) {
            int animedbId = animeIndices[i];
            if (animedbId != 26 && animedbId != 13001 && animedbId != 20583 && animedbId != 5680) {
                continue;
            }
            List<ScoredAnimeId> similarAnme = new ArrayList<>();
            for (int j = 0, itemStart = 0; itemStart < embedding.length; ++j, itemStart += rank) {
                if (itemStart == rowStart)
                    continue;
                float score = 0, sumSq1 = 0, sumSq2 = 0;
                for (int z = 0; z < rank; ++z) {
                    float delta = embedding[rowStart + z] - embedding[itemStart + z];
                    score += delta * delta;
                    // score += embedding[rowStart + z] * embedding[itemStart + z];
                    // sumSq1 += embedding[rowStart + z] * embedding[rowStart + z];
                    // sumSq2 += embedding[itemStart + z] * embedding[itemStart + z];
                }
                // similarAnme.add(
                // new ScoredAnimeId(animeIndices[j], -(score / (float) (Math.sqrt(sumSq1) *
                // Math.sqrt(sumSq2)))));
                similarAnme.add(new ScoredAnimeId(animeIndices[j], -score));
            }
            similarAnme.sort(ScoredAnimeId.SORT_SCORE);

            Set<Integer> seenFranchises = new HashSet<>();
            addFranchise(itemMap, animedbId, seenFranchises);
            int index = i * similarAnimeCount;
            for (int j = 0; j < similarAnimeCount && j < similarAnme.size(); ++j) {
                ScoredAnimeId scoredAnimeId = similarAnme.get(j);
                if (addFranchise(itemMap, scoredAnimeId.animedbId, seenFranchises))
                    continue;
                similarAnimeId[index] = scoredAnimeId.animedbId;
                similarAnimeScore[index] = scoredAnimeId.score;
                ++index;
            }
        }
    }

    private boolean addFranchise(Map<Integer, Item> itemMap, int animedbId, Set<Integer> seenFranchises) {
        Item anime = itemMap.get(animedbId);
        if (anime != null && anime.franchise != null) {
            if (seenFranchises.contains(anime.franchise.franchiseId))
                return true;
            seenFranchises.add(anime.franchise.franchiseId);
        }
        return false;
    }
}