package zzw.content.units.flame;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 按分数降序维护的定长数组 (移植自 flame.HierarchyArray)。
 *
 * <p>消沉 AI 用它保存 8 个优先目标：插入时按 score 从高到低排序，
 * 超过容量直接丢弃；迭代器支持遍历中删除（目标失效时使用）。</p>
 */
@SuppressWarnings("unchecked")
public class HierarchyArray<T> implements Iterable<T>{
    public T[] array;
    public float[] scores;
    public int size = 0;

    private HierarchyIterable<T> iterable;

    public HierarchyArray(int size){
        array = (T[])new Object[size];
        scores = new float[size];
    }

    public T get(int idx){
        if(idx >= size) return null;
        return array[idx];
    }

    public void add(T item, float score){
        if(size >= array.length) return;

        for(int i = 0; i < array.length; i++){
            T c = array[i];
            float s = scores[i];

            if(c == null){
                array[i] = item;
                scores[i] = score;
                size++;
                break;
            }else if(score > s){
                array[i] = item;
                scores[i] = score;

                item = c;
                score = s;
            }
        }
    }

    public void remove(T item){
        for(int i = 0; i < size; i++){
            if(array[i] == item){
                remove(i);
                break;
            }
        }
    }

    public void remove(int idx){
        for(int i = idx; i < size - 1; i++){
            array[i] = array[i + 1];
            scores[i] = scores[i + 1];
        }
        array[size - 1] = null;
        scores[size - 1] = 0f;
        size--;
    }

    public void clear(){
        Arrays.fill(array, null);
        Arrays.fill(scores, 0f);
        size = 0;
    }

    @Override
    public Iterator<T> iterator(){
        if(iterable == null) iterable = new HierarchyIterable<>(this);
        return iterable.iterator();
    }

    public static class HierarchyIterable<T> implements Iterable<T>{
        final HierarchyArray<T> array;
        private final HierarchyIterator iterator1 = new HierarchyIterator(), iterator2 = new HierarchyIterator();

        HierarchyIterable(HierarchyArray<T> ar){
            array = ar;
        }

        @Override
        public Iterator<T> iterator(){
            if(iterator1.done){
                iterator1.index = 0;
                iterator1.done = false;
                return iterator1;
            }
            if(iterator2.done){
                iterator2.index = 0;
                iterator2.done = false;
                return iterator2;
            }
            return new HierarchyIterator();
        }

        private class HierarchyIterator implements Iterator<T>{
            int index = 0;
            boolean done = true;

            @Override
            public boolean hasNext(){
                if(index >= array.size) done = true;
                return index < array.size;
            }

            @Override
            public T next(){
                if(index >= array.size) throw new NoSuchElementException(String.valueOf(index));
                return array.array[index++];
            }

            @Override
            public void remove(){
                index--;
                array.remove(index);
            }
        }
    }
}
