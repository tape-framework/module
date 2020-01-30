## README

STATUS: Pre-alpha, in design and prototyping phase.

#### About

`tape.module`

- is extracted from [Duct Core](https://github.com/duct-framework/core)
- see [this](https://github.com/duct-framework/core/issues/14) issue
- aims to support both Clojure & ClojureScript

This adds a layer of abstraction on top of [Integrant](https://github.com/weavejester/integrant).
In Integrant, a configuration map is initiated into a running system map:

```
┌────────┐   ┌────────┐
│ config ├──>│ system │
└────────┘   └────────┘
```

In `tape.module`, the configuration is initiated twice. The configuration is
first initiated into an intermediate configuration, which in turn is initiated
into the system:

```
┌──────────────┐ init ┌────────────────┐ fold ┌─────────────┐ init ┌────────┐
│ modules-conf ├─────>│ modules-system ├─────>│ system-conf ├─────>│ system │
└──────────────┘      └────────────────┘      └─────────────┘      └────────┘                  
```

Keys in a `tape.module` configuration are expected to initiate into functions
that transform a configuration map. There are two broad types: **profiles**,
which merge their value into the configuration, and **modules**, which provide
more complex manipulation.

#### Usage

Define a module:

```clojure
(ns my.module
  (:require [integrant.code :as ig]
            [tape.module :as module]))

(defmethod ig/init-key ::module [_ _]
  (fn [config]
    (module/merge-configs config {:comp/one x, :comp/two y, ...})))
```

Use a module in a modules config map:

```clojure
(ns my.core
  (:require [integrant.code :as ig]
            [tape.module :as module]))

(module/load-hierarchy)
(def config {:my.module/module nil, ... <other-modules>})
(def system (-> config module/prep-config ig/init))
```

A `tape_hierarchy.edn` file in the root of the `src` directory can be used to
define derivations for modules keys. Always use `(module/load-hierarchy)` as a
first form in your entry point to execute all the derivations.

#### License

Copyright © 2019 James Reeves, clyfe

Distributed under the MIT license.