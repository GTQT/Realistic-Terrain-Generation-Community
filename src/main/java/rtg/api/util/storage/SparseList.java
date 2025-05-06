package rtg.api.util.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class SparseList<T> extends ArrayList<T> {
    public static <T> Collector<T, SparseList<T>, SparseList<T>> toSparseList() {
        return new Collector<T, SparseList<T>, SparseList<T>>() {
            @Override
            public Supplier<SparseList<T>> supplier() {
                return SparseList::new;
            }

            @Override
            public BiConsumer<SparseList<T>, T> accumulator() {
                return ArrayList::add;
            }

            @Override
            public BinaryOperator<SparseList<T>> combiner() {
                throw new IllegalStateException("Parallel operations not supported for SparseLists!");
            }

            @Override
            public Function<SparseList<T>, SparseList<T>> finisher() {
                return Function.identity();
            }

            @Override
            public Set<Characteristics> characteristics() {
                return new HashSet<Characteristics>() {{
                    add(Characteristics.IDENTITY_FINISH);
                }};
            }
        };
    }

    private void fillSpace(int upTo) {
        for (int i = size() - 1; i < upTo + 1; i++) {
            super.add(null);
        }
    }

    @Override
    public T set(final int i, final T t) {
        ensureCapacity(i + 1);
        final int size = size();
        if (i >= size) {
            fillSpace(i);
        }
        return super.set(i, t);
    }

    @Override
    public T get(int i) {
        if (i < size()) {
            return super.get(i);
        }
        return null;
    }

    public boolean containsKey(final int key) {
        if (key < size()) {
            return super.get(key) != null;
        }
        return false;
    }

    /**
     * 将键值对插入到稀疏列表中的指定位置。
     * 如果索引超出当前列表长度，则自动扩展列表以容纳该位置，并在中间填充 null。
     *
     * @param key   要插入的位置（索引）
     * @param value 要插入的值
     */
    public void put(int key, T value) {
        if (key < 0) {
            throw new IllegalArgumentException("Key must be non-negative");
        }

        // 如果 key 超出当前 size，则填充空间直到 key 位置
        if (key >= size()) {
            fillSpace(key);
        }

        // 设置指定位置的值
        super.set(key, value);
    }
}