package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

public class ArrayListDeque<T> extends AbstractList<T> implements ListAndDeque<T> {
    private static final int MIN_GROWTH = 1;
    private Object[] contents;
    private int head;
    private int size;

    public ArrayListDeque() {
        this(1);
    }

    public ArrayListDeque(int p_294771_) {
        this.contents = new Object[p_294771_];
        this.head = 0;
        this.size = 0;
    }

    @Override
    public int size() {
        return this.size;
    }

    @VisibleForTesting
    public int capacity() {
        return this.contents.length;
    }

    private int getIndex(int p_296293_) {
        return (p_296293_ + this.head) % this.contents.length;
    }

    @Override
    public T get(int p_296055_) {
        this.verifyIndexInRange(p_296055_);
        return this.getInner(this.getIndex(p_296055_));
    }

    private static void verifyIndexInRange(int p_295367_, int p_294503_) {
        if (p_295367_ < 0 || p_295367_ >= p_294503_) {
            throw new IndexOutOfBoundsException(p_295367_);
        }
    }

    private void verifyIndexInRange(int p_296349_) {
        verifyIndexInRange(p_296349_, this.size);
    }

    private T getInner(int p_295426_) {
        return (T)this.contents[p_295426_];
    }

    @Override
    public T set(int p_294350_, T p_296216_) {
        this.verifyIndexInRange(p_294350_);
        Objects.requireNonNull(p_296216_);
        int i = this.getIndex(p_294350_);
        T t = this.getInner(i);
        this.contents[i] = p_296216_;
        return t;
    }

    @Override
    public void add(int p_294490_, T p_294693_) {
        verifyIndexInRange(p_294490_, this.size + 1);
        Objects.requireNonNull(p_294693_);
        if (this.size == this.contents.length) {
            this.grow();
        }

        int i = this.getIndex(p_294490_);
        if (p_294490_ == this.size) {
            this.contents[i] = p_294693_;
        } else if (p_294490_ == 0) {
            this.head--;
            if (this.head < 0) {
                this.head = this.head + this.contents.length;
            }

            this.contents[this.getIndex(0)] = p_294693_;
        } else {
            for (int j = this.size - 1; j >= p_294490_; j--) {
                this.contents[this.getIndex(j + 1)] = this.contents[this.getIndex(j)];
            }

            this.contents[i] = p_294693_;
        }

        this.modCount++;
        this.size++;
    }

    private void grow() {
        int i = this.contents.length + Math.max(this.contents.length >> 1, 1);
        Object[] aobject = new Object[i];
        this.copyCount(aobject, this.size);
        this.head = 0;
        this.contents = aobject;
    }

    @Override
    public T remove(int p_295380_) {
        this.verifyIndexInRange(p_295380_);
        int i = this.getIndex(p_295380_);
        T t = this.getInner(i);
        if (p_295380_ == 0) {
            this.contents[i] = null;
            this.head++;
        } else if (p_295380_ == this.size - 1) {
            this.contents[i] = null;
        } else {
            for (int j = p_295380_ + 1; j < this.size; j++) {
                this.contents[this.getIndex(j - 1)] = this.get(j);
            }

            this.contents[this.getIndex(this.size - 1)] = null;
        }

        this.modCount++;
        this.size--;
        return t;
    }

    @Override
    public boolean removeIf(Predicate<? super T> p_296232_) {
        int i = 0;

        for (int j = 0; j < this.size; j++) {
            T t = this.get(j);
            if (p_296232_.test(t)) {
                i++;
            } else if (i != 0) {
                this.contents[this.getIndex(j - i)] = t;
                this.contents[this.getIndex(j)] = null;
            }
        }

        this.modCount += i;
        this.size -= i;
        return i != 0;
    }

    private void copyCount(Object[] p_294388_, int p_294959_) {
        for (int i = 0; i < p_294959_; i++) {
            p_294388_[i] = this.get(i);
        }
    }

    @Override
    public void replaceAll(UnaryOperator<T> p_295123_) {
        for (int i = 0; i < this.size; i++) {
            int j = this.getIndex(i);
            this.contents[j] = Objects.requireNonNull(p_295123_.apply(this.getInner(i)));
        }
    }

    @Override
    public void forEach(Consumer<? super T> p_296263_) {
        for (int i = 0; i < this.size; i++) {
            p_296263_.accept(this.get(i));
        }
    }

    @Override
    public void addFirst(T p_296384_) {
        this.add(0, p_296384_);
    }

    @Override
    public void addLast(T p_295130_) {
        this.add(this.size, p_295130_);
    }

    @Override
    public boolean offerFirst(T p_295887_) {
        this.addFirst(p_295887_);
        return true;
    }

    @Override
    public boolean offerLast(T p_296237_) {
        this.addLast(p_296237_);
        return true;
    }

    @Override
    public T removeFirst() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.remove(0);
        }
    }

    @Override
    public T removeLast() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.remove(this.size - 1);
        }
    }

    @Override
    public ListAndDeque<T> reversed() {
        return new ArrayListDeque.ReversedView(this);
    }

    @Nullable
    @Override
    public T pollFirst() {
        return this.size == 0 ? null : this.removeFirst();
    }

    @Nullable
    @Override
    public T pollLast() {
        return this.size == 0 ? null : this.removeLast();
    }

    @Override
    public T getFirst() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.get(0);
        }
    }

    @Override
    public T getLast() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.get(this.size - 1);
        }
    }

    @Nullable
    @Override
    public T peekFirst() {
        return this.size == 0 ? null : this.getFirst();
    }

    @Nullable
    @Override
    public T peekLast() {
        return this.size == 0 ? null : this.getLast();
    }

    @Override
    public boolean removeFirstOccurrence(Object p_294109_) {
        for (int i = 0; i < this.size; i++) {
            T t = this.get(i);
            if (Objects.equals(p_294109_, t)) {
                this.remove(i);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean removeLastOccurrence(Object p_295642_) {
        for (int i = this.size - 1; i >= 0; i--) {
            T t = this.get(i);
            if (Objects.equals(p_295642_, t)) {
                this.remove(i);
                return true;
            }
        }

        return false;
    }

    @Override
    public Iterator<T> descendingIterator() {
        return new ArrayListDeque.DescendingIterator();
    }

    class DescendingIterator implements Iterator<T> {
        private int index = ArrayListDeque.this.size() - 1;

        public DescendingIterator() {
        }

        @Override
        public boolean hasNext() {
            return this.index >= 0;
        }

        @Override
        public T next() {
            return ArrayListDeque.this.get(this.index--);
        }

        @Override
        public void remove() {
            ArrayListDeque.this.remove(this.index + 1);
        }
    }

    class ReversedView extends AbstractList<T> implements ListAndDeque<T> {
        private final ArrayListDeque<T> source;

        public ReversedView(ArrayListDeque<T> p_339677_) {
            this.source = p_339677_;
        }

        @Override
        public ListAndDeque<T> reversed() {
            return this.source;
        }

        @Override
        public T getFirst() {
            return this.source.getLast();
        }

        @Override
        public T getLast() {
            return this.source.getFirst();
        }

        @Override
        public void addFirst(T p_339593_) {
            this.source.addLast(p_339593_);
        }

        @Override
        public void addLast(T p_339649_) {
            this.source.addFirst(p_339649_);
        }

        @Override
        public boolean offerFirst(T p_339654_) {
            return this.source.offerLast(p_339654_);
        }

        @Override
        public boolean offerLast(T p_339690_) {
            return this.source.offerFirst(p_339690_);
        }

        @Override
        public T pollFirst() {
            return this.source.pollLast();
        }

        @Override
        public T pollLast() {
            return this.source.pollFirst();
        }

        @Override
        public T peekFirst() {
            return this.source.peekLast();
        }

        @Override
        public T peekLast() {
            return this.source.peekFirst();
        }

        @Override
        public T removeFirst() {
            return this.source.removeLast();
        }

        @Override
        public T removeLast() {
            return this.source.removeFirst();
        }

        @Override
        public boolean removeFirstOccurrence(Object p_339684_) {
            return this.source.removeLastOccurrence(p_339684_);
        }

        @Override
        public boolean removeLastOccurrence(Object p_339678_) {
            return this.source.removeFirstOccurrence(p_339678_);
        }

        @Override
        public Iterator<T> descendingIterator() {
            return this.source.iterator();
        }

        @Override
        public int size() {
            return this.source.size();
        }

        @Override
        public boolean isEmpty() {
            return this.source.isEmpty();
        }

        @Override
        public boolean contains(Object p_339624_) {
            return this.source.contains(p_339624_);
        }

        @Override
        public T get(int p_339634_) {
            return this.source.get(this.reverseIndex(p_339634_));
        }

        @Override
        public T set(int p_339669_, T p_339609_) {
            return this.source.set(this.reverseIndex(p_339669_), p_339609_);
        }

        @Override
        public void add(int p_339646_, T p_339625_) {
            this.source.add(this.reverseIndex(p_339646_) + 1, p_339625_);
        }

        @Override
        public T remove(int p_339650_) {
            return this.source.remove(this.reverseIndex(p_339650_));
        }

        @Override
        public int indexOf(Object p_339682_) {
            return this.reverseIndex(this.source.lastIndexOf(p_339682_));
        }

        @Override
        public int lastIndexOf(Object p_339602_) {
            return this.reverseIndex(this.source.indexOf(p_339602_));
        }

        @Override
        public List<T> subList(int p_339640_, int p_339642_) {
            return this.source.subList(this.reverseIndex(p_339642_) + 1, this.reverseIndex(p_339640_) + 1).reversed();
        }

        @Override
        public Iterator<T> iterator() {
            return this.source.descendingIterator();
        }

        @Override
        public void clear() {
            this.source.clear();
        }

        private int reverseIndex(int p_339612_) {
            return p_339612_ == -1 ? -1 : this.source.size() - 1 - p_339612_;
        }
    }
}
