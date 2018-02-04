package com.makotan.clojure.freemarker;

import clojure.lang.*;
import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.CollectionModel;
import freemarker.ext.beans.MapModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.util.Collection;
import java.util.Map;

/**
 * Created by makotan on 2014/03/27.
 */
public class ClojureWrapper extends BeansWrapper {
  @Override
  public TemplateModel wrap(Object obj) throws TemplateModelException {

    if (obj instanceof IPersistentMap) {
      return new ClojureMapModel((IPersistentMap)obj , this);
    }

    if (obj instanceof PersistentHashSet) {
      return new CollectionModel((Collection)obj , this);
    }
    if (obj instanceof PersistentList) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof PersistentQueue) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof PersistentTreeSet) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof PersistentVector) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof TransactionalHashMap) {
      return new MapModel((Map)obj , this);

    }
    if (obj instanceof APersistentSet) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof APersistentVector) {
      return new CollectionModel((Collection)obj , this);

    }

    if (obj instanceof IPersistentList) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof IPersistentSet) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof IPersistentVector) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof IPersistentStack) {
      return new CollectionModel((Collection)obj , this);

    }
    if (obj instanceof IPersistentCollection) {
      return new CollectionModel((Collection)obj , this);

    }

    return super.wrap(obj);
  }
}