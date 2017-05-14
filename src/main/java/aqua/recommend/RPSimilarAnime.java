package aqua.recommend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RPSimilarAnime {
    public final Map<Integer, Integer> animeIndexMap;
    public final int[] animeRatedMap;
    public final int similarAnimeCount;
    public final int[] similarAnimeId;
    public final float[] similarAnimeScore;

    public RPSimilarAnime(Map<Integer, Integer> animeIndexMap, int similarAnimeCount, int[] similarAnimeId, float[] similarAnimeScore) {
        this.animeIndexMap = animeIndexMap;
        this.similarAnimeCount = similarAnimeCount;
        this.similarAnimeId = similarAnimeId;
        this.similarAnimeScore = similarAnimeScore;

        this.animeRatedMap = new int[animeIndexMap.size()];
        for (Map.Entry<Integer, Integer> entry : animeIndexMap.entrySet())
            animeRatedMap[entry.getValue()] = entry.getKey();
    }

    public RPSimilarAnime(int[] animeRatedMap, int similarAnimeCount, int[] similarAnimeId, float[] similarAnimeScore) {
        this.animeRatedMap = animeRatedMap;
        this.similarAnimeCount = similarAnimeCount;
        this.similarAnimeId = similarAnimeId;
        this.similarAnimeScore = similarAnimeScore;

        this.animeIndexMap = new HashMap<>();
        for (int i = 0; i < animeRatedMap.length; ++i)
            animeIndexMap.put(animeRatedMap[i], i);
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
}
