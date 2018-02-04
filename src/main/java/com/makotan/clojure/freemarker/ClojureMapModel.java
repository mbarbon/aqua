package com.makotan.clojure.freemarker;

import clojure.lang.IPersistentMap;
import clojure.lang.MapEntry;
import freemarker.ext.beans.MapModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.util.List;
import java.util.Map;

/**
 * Created by makotan on 2014/03/27.
 */
public class ClojureMapModel extends MapModel {
    IPersistentMap map;
    ClojureWrapper wrapper;

    public ClojureMapModel(IPersistentMap map, ClojureWrapper wrapper) {
        super((Map)map , wrapper);
        this.map = map;
        this.wrapper = wrapper;
    }

    @Override
    public TemplateModel get(String key) throws TemplateModelException {
        String cljKey = key.replaceAll("_" , "-");
        clojure.lang.Keyword k = clojure.lang.Keyword.intern(cljKey);
        Object val = clojure.lang.RT.find(map , k);
        if (val == null) {
            val = map.valAt(key);
        }
        if (val instanceof MapEntry) {
            val = ((MapEntry)val).val();
        }
        return wrapper.wrap(val);
    }

    public boolean isEmpty() {
        return map.count() == 0;
    }

    public int size() {
        return map.count();
    }
    public Object exec(List arguments) throws TemplateModelException {
        String key = unwrap((TemplateModel)arguments.get(0)).toString();
        String cljKey = key.replaceAll("_", "-");
        clojure.lang.Keyword k = clojure.lang.Keyword.intern(cljKey);
        Object val = clojure.lang.RT.find(map, k);
        return val;
    }
}