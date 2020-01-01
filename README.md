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

#### License

Copyright © 2019 James Reeves, clyfe

Distributed under the MIT license.