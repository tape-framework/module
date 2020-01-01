(ns tape.module
  "Core functions required by a Tape application."
  (:refer-clojure :exclude [compile])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [tape.module.merge :as merge]
            [integrant.core :as ig]))

(defn- hierarchy-urls []
  (let [cl (.. Thread currentThread getContextClassLoader)]
    (enumeration-seq (.getResources cl "tape_hierarchy.edn"))))

(defmacro load-hierarchy
  "Search the base classpath for files named `tape_hierarchy.edn`, and use them
  to extend the global `derive` hierarchy. This allows a hierarchy to be
  constructed without needing to load every namespace.

  The `tape_hierarchy.edn` file should be an edn map that maps child keywords
  to vectors of parents. For example:

      {:example/child [:example/father :example/mother]}

  This is equivalent to writing:

      (derive :example/child :example/father)
      (derive :example/child :example/mother)

  This function should be called once when the application is started."
  []
  `(do ~@(for [url (hierarchy-urls)
               :let [hierarchy (edn/read-string (slurp url))]
               [tag parents] hierarchy
               parent parents]
           (list `derive tag parent))))

(defn- config-resource [path]
  (or (io/resource path)
      (io/resource (str path ".edn"))
      (io/resource (str path ".clj"))))

(declare merge-default-readers)

(defn- make-include [readers]
  (fn [path]
    (let [opts {:readers (merge-default-readers readers)}]
      (some->> path config-resource slurp (ig/read-string opts)))))

(defn- merge-default-readers [readers]
  (merge
    {'tape/include (make-include readers)
     'tape/displace merge/displace
     'tape/replace merge/replace}
    readers))

(defmacro read-config
  "Read an edn configuration from a slurpable source. An optional map of data
  readers may be supplied. By default the following readers are supported:

  #tape/include
  : substitute for a configuration on the classpath

  #tape/displace
  : equivalent to the metadata tag `^:displace`, but works on primative values

  #tape/replace
  : equivalent to the metadata tag `^:replace`, but works on primative values

  #ig/ref
  : an Integrant reference to another key

  #ig/refset
  : an Integrant reference to a set of keys"
  [source]
  (some->> source io/resource slurp
           (ig/read-string {:readers (merge-default-readers {})})))
