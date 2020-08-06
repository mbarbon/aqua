package aqua.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

class SearchUtils {
    interface PartialSuggestion {
        AnimeTitle animeTitle();
    }

    private static final Pattern SPLIT_NON_WORD = Pattern.compile("[^a-z0-9]+");

    static final AnimeTitle[] EMPTY_ANIME_TITLES = new AnimeTitle[0];
    static final Suggestion[] EMPTY_SUGGESTIONS = new Suggestion[0];

    static List<String> splitWords(String title) {
        return Arrays.asList(SPLIT_NON_WORD.split(title.toLowerCase()));
    }

    static <T extends PartialSuggestion> Suggestion[] selectTopK(Collection<T> intermediate, Comparator<T> comparator,
            int limit) {
        Set<Integer> seenAnime = new HashSet<>();
        List<T> suggestions = new ArrayList<>(intermediate);
        suggestions.sort(comparator);
        List<Suggestion> result = new ArrayList<>(limit);
        for (T suggestion : suggestions) {
            AnimeTitle animeTitle = suggestion.animeTitle();
            if (!seenAnime.add(animeTitle.animedbId))
                continue;
            result.add(new Suggestion(animeTitle.animedbId, animeTitle.title));
            if (result.size() >= limit)
                break;
        }
        return result.toArray(EMPTY_SUGGESTIONS);
    }
}
