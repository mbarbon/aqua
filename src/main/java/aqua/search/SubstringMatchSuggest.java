package aqua.search;

import aqua.recommend.HPPCUtils;
import aqua.search.SearchUtils.PartialSuggestion;
import com.carrotsearch.hppc.IntIntMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SubstringMatchSuggest {
    private static class Entry {
        public final String string;
        public final int start;
        public final AnimeTitle[] animeTitles;

        public Entry(String string, int start, AnimeTitle[] animeTitles) {
            this.string = string;
            this.start = start;
            this.animeTitles = animeTitles;
        }

        public boolean matchesStringPrefix(String s) {
            int thisLen = string.length() - start;
            int sLen = s.length();

            for (int i = 0, max = Math.min(thisLen, sLen); i < max; ++i) {
                char thisChar = string.charAt(start + i);
                char sChar = s.charAt(i);

                if (thisChar != sChar)
                    return false;
            }

            return true;
        }

        public int compareString(String s) {
            int thisLen = string.length() - start;
            int sLen = s.length();

            for (int i = 0, max = Math.min(thisLen, sLen); i < max; ++i) {
                char thisChar = string.charAt(start + i);
                char sChar = s.charAt(i);

                if (thisChar != sChar)
                    return thisChar - sChar;
            }

            if (thisLen != sLen)
                return thisLen - sLen;
            return 0;
        }
    }

    private static class PrefixMatch implements PartialSuggestion {
        public int matches = 1;
        public int matchRank = Integer.MAX_VALUE;
        public final int animeRank;
        public final AnimeTitle animeTitle;

        public PrefixMatch(AnimeTitle animeTitle, int animeRank) {
            this.animeTitle = animeTitle;
            this.animeRank = animeRank;
        }

        @Override
        public AnimeTitle animeTitle() {
            return animeTitle;
        }
    }

    private final List<Entry> entries;
    private final IntIntMap animeRank;

    public SubstringMatchSuggest(List<AnimeTitle> animeTitles, Map<Integer, Integer> animeRank) {
        this.animeRank = HPPCUtils.convertMap(animeRank);
        this.entries = makeEntryList(animeTitles);
    }

    public Suggestion[] suggest(String query, int limit) {
        List<String> parts = SearchUtils.splitWords(query);
        Map<AnimeTitle, PrefixMatch> suggestionMap = new HashMap<>();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            Map<AnimeTitle, PrefixMatch> thisPart = new HashMap<>();
            int firstIndex = findFirst(part, 0, entries.size());

            for (; firstIndex < entries.size(); ++firstIndex) {
                Entry entry = entries.get(firstIndex);

                if (!entry.matchesStringPrefix(part))
                    break;
                int rank;

                if (entry.start != 0)
                    rank = entry.start * 2 - part.length() / 3 + (entry.string.length() - part.length()) * 3;
                else
                    rank = entry.string.length() - part.length();

                for (AnimeTitle animeTitle : entry.animeTitles) {
                    PrefixMatch suggestion = thisPart.computeIfAbsent(animeTitle, i -> new PrefixMatch(i, animeRank.getOrDefault(i.animedbId, Integer.MAX_VALUE)));

                    suggestion.matchRank = Math.min(rank, suggestion.matchRank);
                }
            }

            for (Map.Entry<AnimeTitle, PrefixMatch> entry : thisPart.entrySet()) {
                PrefixMatch suggestion = suggestionMap.get(entry.getKey());
                if (suggestion != null) {
                    suggestion.matches += 1;
                    suggestion.matchRank += entry.getValue().matchRank;
                } else {
                    suggestionMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return SearchUtils.selectTopK(suggestionMap.values(), SubstringMatchSuggest::sortSuggestions, limit);
    }

    private static List<Entry> makeEntryList(List<AnimeTitle> titles) {
        Map<String, Set<AnimeTitle>> searchMap = new HashMap<>();
        for (AnimeTitle animeTitle : titles) {
            for (String word : SearchUtils.splitWords(animeTitle.title)) {
                if (word.isEmpty()) {
                    continue;
                }
                Set<AnimeTitle> titlesForWord = searchMap.computeIfAbsent(word, w -> new HashSet<>());
                titlesForWord.add(animeTitle);
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Set<AnimeTitle>> entry : searchMap.entrySet())
            insertEntry(entries, entry.getKey(), entry.getValue());
        entries.sort(SubstringMatchSuggest::compareEntries);
        return entries;
    }

    private static void insertEntry(List<Entry> entries, String string, Set<AnimeTitle> animeTitles) {
        AnimeTitle[] animedbTitlesArray = animeTitles.toArray(SearchUtils.EMPTY_ANIME_TITLES);
        int prefix = string.length() > 10 ? string.length() / 2 + 2 :
                     string.length() > 5  ? string.length() - 3 :
                                            string.length() - 2;
        for (int i = 0, max = Math.max(1, prefix); i < max; ++i)
            entries.add(new Entry(string, i, animedbTitlesArray));
    }

    private int findFirst(String part, int start, int end) {
        while (start < end) {
            int mid = (start + end) / 2;
            Entry entry = entries.get(mid);
            int cmp = entry.compareString(part);

            if (cmp >= 0) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        return start;
    }

    private static int compareEntries(Entry a, Entry b) {
        String as = a.string, bs = b.string;
        int ai = a.start, bi = b.start;
        int am = as.length(), bm = bs.length();

        while (ai < am && bi < bm) {
            char ac = as.charAt(ai), bc = bs.charAt(bi);
            if (ac != bc)
                return ac - bc;
            ++ai;
            ++bi;
        }

        if (ai != am || bi != bm)
            return ai != am ? 1 : -1;
        if (a.start != b.start)
            return a.start - b.start;
        return 0;
    }

    private static int sortSuggestions(PrefixMatch a, PrefixMatch b) {
        if (a.matches != b.matches)
            return b.matches - a.matches;
        if (a.matchRank != b.matchRank)
            return a.matchRank - b.matchRank;
        if (a.animeRank != b.animeRank)
            return a.animeRank - b.animeRank;
        return 0;
    }
}
