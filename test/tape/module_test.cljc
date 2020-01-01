(ns tape.module-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer [deftest is are]])
            [tape.module :as module :include-macros true]
            [tape.module.merge :as merge]
            [integrant.core :as ig]))

(deftest test-load-hierarchy
  (module/load-hierarchy)
  (is (isa? :tape.profile/base :tape/profile))
  (is (isa? :tape.profile/dev :tape/profile)))

(derive ::aa ::a)
(derive ::ab ::a)
(derive ::ab ::b)

(deftest test-merge-configs
  (are [a b c] (= (module/merge-configs a b) c)
    {::a 1}                {::a 2}                       {::a 2}
    {::a {:x 1}}           {::a {:y 2}}                  {::a {:x 1 :y 2}}
    {::a {:x 1}}           {::a ^:displace {:x 2}}       {::a {:x 1}}
    {}                     {::a ^:displace {:y 2}}       {::a {:y 2}}
    {::aa 1}               {::a 2}                       {::aa 2}
    {::aa 1 ::ab 2}        {::a 3}                       {::aa 3 ::ab 3}
    {::aa {:x 1}}          {::a {:y 2}}                  {::aa {:x 1 :y 2}}
    {::a 1}                {::aa 2}                      {::aa 2}
    {::a {:x 1}}           {::aa {:y 2}}                 {::aa {:x 1 :y 2}}
    {::a {:x 1}}           {::aa {:y 2} ::ab {:z 3}}     {::aa {:x 1 :y 2} ::ab {:x 1 :z 3}}
    {::a 1}                {::a (merge/displace 2)}      {::a 1}
    {::a {:x 1}}           {::a {:x (merge/displace 2)}} {::a {:x 1}}
    {::a [:x :y]}          {::a [:y :z]}                 {::a [:x :y :y :z]}
    {::a [:x :y]}          {::a ^:distinct [:y :z]}      {::a [:x :y :z]}
    {::a {:x 1}}           {::a ^:demote {:x 2, :y 3}}   {::a {:x 1, :y 3}}
    {::a ^:promote {:x 1}} {::a {:x 2, :y 3}}            {::a {:x 1, :y 3}}
    {::a (ig/ref ::b)}     {::a {:x 1}}                  {::a {:x 1}}
    {::a {:x 1}}           {::a (ig/ref ::b)}            {::a (ig/ref ::b)}
    {::a (ig/refset ::b)}  {::a {:x 1}}                  {::a {:x 1}}
    {::a {:x 1}}           {::a (ig/refset ::b)}         {::a (ig/refset ::b)}))

(deftest test-read-config
  (is (= (module/read-config "tape/readers.edn")
         {:foo/b {:bar/a {:x 1}, :bar/b (ig/ref :bar/a) :bar/c {:baz/a {:x 1}}}
          :foo/c "tape/config.edn"
          :foo/d (ig/ref :foo/a)
          :foo/e (ig/refset :foo/b)
          :foo/f (merge/displace 1)
          :foo/g (merge/replace [1 2])})))

(deftest test-merge-readers
  (let [conf (module/read-config "tape/readers.edn")]
    (is (= (module/merge-configs {:foo/f 2 :foo/g [3 4]} conf)
           {:foo/b {:bar/a {:x 1}, :bar/b (ig/ref :bar/a) :bar/c {:baz/a {:x 1}}}
            :foo/c "tape/config.edn"
            :foo/d (ig/ref :foo/a)
            :foo/e (ig/refset :foo/b)
            :foo/f 2
            :foo/g [1 2]}))))

(defmethod ig/init-key ::foo [_ {:keys [x]}]
  #(update % ::x (fnil conj []) x))

(defmethod ig/init-key ::bar [_ {:keys [x]}]
  #(update % ::x (fnil conj []) x))

(deftest test-fold-modules
  (let [m {::foo {:x 1}, ::bar {:x 2, :r (ig/ref ::foo)}}]
    (is (= (module/fold-modules (ig/init m))
           {::x [1 2]}))))

(deftest test-profile-keys
  (let [m {:tape.module/foo    {::a 0}
           :tape.profile/base  {::a 1}
           [:tape/profile ::x] {::a 2}
           [:tape/profile ::y] {::a 3}
           [:tape/profile ::z] {::a 4}}]
    (is (= (set (module/profile-keys m [::x :y]))
           #{:tape.module/foo
             :tape.profile/base
             [:tape/profile ::x]
             [:tape/profile ::y]}))
    (is (= (set (module/profile-keys m :all))
           (set (keys m))))))

(deftest test-profile-keyword
  (module/load-hierarchy)
  (let [m {:tape.profile/base  {::a 1, ::b (ig/ref ::a)}
           [:tape/profile ::x] {::a 2, ::c (ig/refset ::b)}}
        p (ig/prep m)]
    (is (= p
           {:tape.profile/base  {::a 1, ::b (module/->InertRef ::a)}
            [:tape/profile ::x] {::a 2, ::c (module/->InertRefSet ::b)
                                 ::module/requires (ig/refset :tape.profile/base)}}))
    (is (= (module/fold-modules (ig/init p))
           {::a 2, ::b (ig/ref ::a), ::c (ig/refset ::b)}))))

(deftest test-build-config
  (module/load-hierarchy)
  (let [m {:tape.profile/base  {::a 1, ::b (ig/ref ::a)}
           [:tape/profile ::x] {::a 2, ::c (ig/refset ::b)}
           [:tape/profile ::y] {::d 3}}]
    (is (= (module/build-config m)
           {::a 2, ::b (ig/ref ::a), ::c (ig/refset ::b), ::d 3}))
    (is (= (module/build-config m [::x])
           {::a 2, ::b (ig/ref ::a), ::c (ig/refset ::b)}))
    (is (= (module/build-config m [:y])
           {::a 1, ::b (ig/ref ::a), ::d 3}))))

(defmethod ig/prep-key ::prep [_ v] (inc v))

(deftest test-prep-config
  (let [m {:tape.profile/base  {::prep 1, ::a (ig/ref ::prep)}
           [:tape/profile ::x] {::prep 2, ::b (ig/refset ::a)}
           [:tape/profile ::y] {::c 3}}]
    (is (= (module/prep-config m)
           {::prep 3, ::a (ig/ref ::prep), ::b (ig/refset ::a), ::c 3}))
    (is (= (module/prep-config m [::x])
           {::prep 3, ::a (ig/ref ::prep), ::b (ig/refset ::a)}))
    (is (= (module/prep-config (assoc m [:tape.profile/base ::z] {::c 1, ::d 4}))
           {::prep 3, ::a (ig/ref ::prep), ::b (ig/refset ::a), ::c 3, ::d 4}))))

(deftest test-environment-keyword
  (module/load-hierarchy)
  (let [m {::module/environment :development}]
    (is (= m (ig/init m)))))

(deftest test-project-ns-keyword
  (module/load-hierarchy)
  (let [m {::module/project-ns 'foo}]
    (is (= m (ig/init m)))))

(deftest test-profile-dev-keyword
  (module/load-hierarchy)
  (let [m {:tape.profile/base {::a 1, ::b (ig/ref ::a)}
           :tape.profile/dev  {::a 2, ::c (ig/refset ::b)}}
        p (ig/prep m)]
    (is (= p
           {:tape.profile/base {::a 1, ::b (module/->InertRef ::a)}
            :tape.profile/dev  {::a 2, ::c (module/->InertRefSet ::b)
                                ::module/requires (ig/refset :tape.profile/base)
                                ::module/environment :development}}))
    (is (= (module/fold-modules (ig/init p))
           {::a 2, ::b (ig/ref ::a), ::c (ig/refset ::b)
            ::module/environment :development}))))

(deftest test-profile-test-keyword
  (module/load-hierarchy)
  (let [m {:tape.profile/base {::a 1, ::b (ig/ref ::a)}
           :tape.profile/test {::a 2, ::c (ig/refset ::b)}}
        p (ig/prep m)]
    (is (= p
           {:tape.profile/base {::a 1, ::b (module/->InertRef ::a)}
            :tape.profile/test {::a 2, ::c (module/->InertRefSet ::b)
                                ::module/requires (ig/refset :tape.profile/base)
                                ::module/environment :test}}))
    (is (= (module/fold-modules (ig/init p))
           {::a 2, ::b (ig/ref ::a), ::c (ig/refset ::b)
            ::module/environment :test}))))

(deftest test-profile-prod-keyword
  (module/load-hierarchy)
  (let [m {:tape.profile/base {::a 1, ::b (ig/ref ::a)}
           :tape.profile/prod {::a 2, ::c (ig/refset ::b)}}
        p (ig/prep m)]
    (is (= p
           {:tape.profile/base {::a 1, ::b (module/->InertRef ::a)}
            :tape.profile/prod {::a 2, ::c (module/->InertRefSet ::b)
                                ::module/requires (ig/refset :tape.profile/base)
                                ::module/environment :production}}))
    (is (= (module/fold-modules (ig/init p))
           {::a 2, ::b (ig/ref ::a), ::c (ig/refset ::b)
            ::module/environment :production}))))
