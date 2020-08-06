package aqua.recommend;

import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntIntMap;
import java.util.Map;

public class HPPCUtils {
    public static IntIntMap convertMap(Map<Integer, Integer> map) {
        IntIntMap result = new IntIntHashMap(map.size());
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
