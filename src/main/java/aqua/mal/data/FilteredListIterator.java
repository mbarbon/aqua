package aqua.mal.data;

import java.lang.Iterable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class FilteredListIterator<T extends RatedBase> implements Iterable<T>, Iterator<T> {
    private final int mask;
    private final Iterator<T> parent;
    private T next;

    public FilteredListIterator(T[] animeList, int mask) {
        this.mask = mask;
        this.parent = Arrays.asList(animeList).iterator();
        this.next = findNext();
    }

    @Override
    public Iterator<T> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public T next() {
        if (next == null)
            throw new NoSuchElementException();
        T result = next;
        next = findNext();
        return result;
    }

    private T findNext() {
        while (parent.hasNext()) {
            T maybeNext = parent.next();
            if ((mask & (1 << maybeNext.status)) != 0)
                return maybeNext;
        }
        return null;
    }
}
