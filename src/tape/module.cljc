(ns tape.module
  "Core functions required by a Tape application."
  (:refer-clojure :exclude [compile])
  (:require [clojure.walk :as walk]
            [integrant.core :as ig]
            [tape.module.merge :as merge]))

(defn- expand-ancestor-keys [config base]
  (reduce-kv
   (fn [m k v]
     (if-let [ks (seq (keys (ig/find-derived base k)))]
       (reduce #(assoc %1 %2 v) m ks)
       (assoc m k v)))
   {}
   config))

(defn- merge-configs* [a b]
  (merge/meta-merge (expand-ancestor-keys a b)
                    (expand-ancestor-keys b a)))

(defn merge-configs
  "Intelligently merge multiple configurations. Uses meta-merge and will merge
  configurations in order from left to right. Generic top-level keys are merged
  into more specific descendants, if the descendants exist."
  [& configs]
  (merge/unwrap-all (reduce merge-configs* {} configs)))

(defn fold-modules
  "Fold a system map of modules into an Integrant configuration. A module is a
  pure function that transforms a configuration map. The modules are traversed
  in dependency order and applied to iteratively to a blank map in order to
  build the final configuration."
  [system]
  (ig/fold system (fn [m _ f] (f m)) {}))

(defn- matches-name? [key profile-key]
  (letfn [(matches? [k] (= (name k) (name profile-key)))]
    (if (vector? key)
      (some matches? key)
      (matches? key))))

(defn- matches-profile? [key profile-key]
  (if (namespace profile-key)
    (ig/derived-from? key profile-key)
    (matches-name? key profile-key)))

(defn- keep-key? [profiles key]
  (or (not (ig/derived-from? key :tape/profile))
      (ig/derived-from? key :tape.profile/base)
      (some (partial matches-profile? key) profiles)))

(defn profile-keys
  "Return a collection of keys for a configuration that excludes any profile
  not present in the supplied colleciton of profiles. Profiles may be specified
  as namespaced keywords, or as un-namespaced keywords, in which case only the
  name will matched (e.g. `:dev` will match `:tape.profile/dev`). If the :all
  keyword is supplied instead of a profile collection, all keys are returned."
  [config profiles]
  (cond->> (keys config)
    (not= profiles :all) (filter (partial keep-key? profiles))))

(defn build-config
  "Build an Integrant configuration from a configuration of modules. A
  collection of profile keys may optionally be supplied that govern which
  profiles to use (see [[profile-keys]]). Omitting the profiles or using the
  :all keyword in their stead will result in all keys being used."
  ([config]
   (build-config config :all))
  ([config profiles]
   (let [keys (profile-keys config profiles)]
     (-> config ig/prep (ig/init keys) fold-modules))))

(defn prep-config
  "Load, build and prep a configuration of modules into an Integrant
  configuration that's ready to be initiated. This function loads in relevant
  namespaces based on key names, so is side-effectful (though idempotent)."
  ([config]
   (prep-config config :all))
  ([config profiles]
   (-> config
       (build-config profiles)
       (ig/prep))))

(defn exec-config
  "Build, prep and initiate a configuration of modules. By default it only runs
  profiles derived from `:tape.profile/prod` and keys derived from
  `:tape/main`.

  This function is designed to be called from `-main` when standalone operation
  is required."
  ([config]
   (exec-config config [:tape.profile/prod]))
  ([config profiles]
   (exec-config config profiles [:tape/main]))
  ([config profiles keys]
   (-> config (prep-config profiles) (ig/init keys))))

(defrecord InertRef    [key])
(defrecord InertRefSet [key])

(defn- deactivate-ref [x]
  (cond
    (ig/ref? x)    (->InertRef (:key x))
    (ig/refset? x) (->InertRefSet (:key x))
    :else x))

(defn- activate-ref [x]
  (cond
    (instance? InertRef x)    (ig/ref (:key x))
    (instance? InertRefSet x) (ig/refset (:key x))
    :else x))

(defmethod ig/prep-key :tape/module [_ profile]
  (assoc profile ::requires (ig/refset :tape/profile)))

(defmethod ig/init-key :tape/const [_ v] v)

(defmethod ig/prep-key :tape/profile [k profile]
  (-> (walk/postwalk deactivate-ref profile)
      (cond-> (not (isa? k :tape.profile/base))
        (assoc ::requires (ig/refset :tape.profile/base)))))

(defmethod ig/init-key :tape/profile [_ profile]
  (let [profile (walk/postwalk activate-ref (dissoc profile ::requires))]
    #(merge-configs % profile)))

(defmethod ig/prep-key :tape.profile/base [_ profile]
  (walk/postwalk deactivate-ref profile))

(defmethod ig/prep-key :tape.profile/dev [_ profile]
  (-> (ig/prep-key :tape/profile profile)
      (assoc ::environment :development)))

(defmethod ig/prep-key :tape.profile/test [_ profile]
  (-> (ig/prep-key :tape/profile profile)
      (assoc ::environment :test)))

(defmethod ig/prep-key :tape.profile/prod [_ profile]
  (-> (ig/prep-key :tape/profile profile)
      (assoc ::environment :production)))
