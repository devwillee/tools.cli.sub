# tools.cli.sub

[tools.cli](https://github.com/clojure/tools.cli) with sub-command processor.

By default, all commands include a "--help" option.
"--help" option works for the following three cases:

  - Input the "-h" option.
  - Arguments are empty. (command depth is root)
  - Subcommand exists but not input.

In addition, the "alias" which can abbreviate long commands, is supported.

See below examples.

## Examples

* specs

```clojure
(def ^:dynamic size-opt
  [nil "--size SIZE" "result size"])

(def commands
  ; The "root" command should be entered in ": __ root__".
  {:__root__ {:description {:arguments "[options] action"
                            :usage     "Main usage..."}}

   :file     {:options     []
              :description {:arguments "[sub command]"
                            :usage     "file commands"}
              :sub-command {:ls   {:options     [size-opt]
                                   :description {:arguments "[options...]"
                                                 :usage     "Show file list"}
                                   :handler     (fn [arguments options]
                                                  ;; Do something...
                                                  )}
                            :find {:options     [size-opt]
                                   :description {:arguments "[FILE NAME]"
                                                 :usage     "find matched files"}
                                   :handler     (fn [arguments options]
                                                  ;; Do something...
                                                  (cond
                                                    (empty? arguments) (throw (IllegalArgumentException. "File name is requried!"))
                                                    :else (println "Done."))
                                                  )}}}

   :find     {:alias [:file :find]}})
```

* test

```clojure
(cli-sub/parser commands "")
#_{:options     {:help true},
   :arguments   [],
   :summary     "  -h, --help",
   :errors      nil,
   :description {:arguments "[options] action", :usage "Main usage..."},
   :command     []}


;; sub-commands and arguments

(cli-sub/parser commands "file")
#_{:options {:help true},
   :arguments ["file"],
   :summary "  -h, --help",
   :errors nil,
   :command ["file"],
   :description {:arguments "[sub command]", :usage "file commands"}}

(cli-sub/parser commands "file" "find")
#_{:options     {},
   :arguments   [],
   :summary     "  -h, --help\n--size SIZE  result size",
   :errors      nil,
   :description {:arguments "[FILE NAME]", :usage "find matched files"},
   :handler     #object[tools.cli.sub.test$fn__2000 0x387b3d4b "tools.cli.sub.test$fn__2000@387b3d4b"],
   :command     ["file" "find"]}

(cli-sub/parser commands "file" "find" "hello_clojure.clj")
#_{:options {:size "10"},
   :arguments ["hello_clojure.clj"],
   :summary "  -h, --help\n--size SIZE  result size",
   :errors nil,
   :description {:arguments "[FILE NAME]", :usage "find matched files"},
   :handler #object[tools.cli.sub.test$fn__2000 0x387b3d4b "tools.cli.sub.test$fn__2000@387b3d4b"],
   :command ["file" "find"]}


;; They all output the same result.

(cli-sub/parser commands "find")
#_{:options {},
   :arguments [],
   :summary "  -h, --help\n--size SIZE  result size",
   :errors nil,
   :description {:arguments "[FILE NAME]", :usage "find matched files"},
   :handler #object[tools.cli.sub.test$fn__2000 0x387b3d4b "tools.cli.sub.test$fn__2000@387b3d4b"]}

(cli-sub/parser commands "find" "-h")
#_{:options {:help true},
   :arguments [],
   :summary "  -h, --help\n--size SIZE  result size",
   :errors nil,
   :description {:arguments "[FILE NAME]", :usage "find matched files"},
   :handler #object[tools.cli.sub.test$fn__2000 0x387b3d4b "tools.cli.sub.test$fn__2000@387b3d4b"]}
```

### with Supervisor
"supervisor" is a preprocessor that handles common tasks.
Using this effectively reduces the boiler-plate.

It evaluates the handler of each command if there is no "help" option and no error occurs.

```clojure
(-> (cli-sub/parser commands "file" "find")
    cli-sub/supervisor)
#_ CompilerException java.lang.IllegalArgumentException: File name is requried!, compiling:(D:/project/tools.cli.sub/test/tools/cli/sub/test.clj:54:1)

(-> (cli-sub/parser commands "file" "find" "hello_clojure.clj")
    cli-sub/supervisor)
#_ Done.
```

## License

Copyright © 2019 devwillee

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.