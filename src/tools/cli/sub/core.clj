(ns tools.cli.sub.core
  (:require [clojure.tools.cli :as cli]
            [clojure.set :as cset]
            [clojure.string :as str]))

(defn- options-with-help-option [options]
  (->>
    (map set [options [["-h" "--help"]]])
    (reduce cset/union)
    sort
    vec))

(defn- commands-expect-options [args]
  (loop [index 0
         cmds args]

    (let [cmd (first cmds)]
      (if (and cmd (str/starts-with? cmd "-"))
        (subvec args 0 index)
        (recur (inc index) (rest cmds))))))

(defn- sub-command-keys [commands-only]
  (butlast (reduce #(conj %1 (keyword (name %2)) :sub-command) [] commands-only)))

(defn- sub-command-parser [spec arguments]
  (loop [args arguments
         cmds [(first args)]]

    (let [sub-cmd-ks (sub-command-keys cmds)
          {:keys [alias handler] :as sub-command-form} (get-in spec sub-cmd-ks)
          rest-args (rest args)
          next-sub-command (first rest-args)]

      ;; Priority :  link > handler > sub-command
      (cond
        alias (-> (get-in spec (sub-command-keys alias))
                  (dissoc :sub-command)
                  (assoc :arguments rest-args))

        handler (merge sub-command-form
                       {:command   cmds
                        :arguments (vec rest-args)})

        sub-command-form (if next-sub-command
                           (recur rest-args (conj cmds next-sub-command))

                           ;; sub-command가 있는데, 입력하지 않은 경우 : "-h" 옵션과 동일 취급
                           (merge
                             {:command   cmds
                              :arguments (conj cmds "-h")}

                             (dissoc sub-command-form :sub-command)))

        ;; "sub-command-form" doesn't exist and contains options
        (some #(str/starts-with? % "-") cmds)
        (let [commands-only (commands-expect-options cmds)]
          (merge {:command   commands-only
                  :arguments cmds}
                 (-> (get-in spec (sub-command-keys commands-only))
                     (dissoc :sub-command))))

        :else {:command cmds
               :errors  ["Unknown command."]}
        ))))

(defn parser [spec & args]
  (if (or (empty? args) (some #{(first args)} ["help" "-h" "--help" ""]))
    (let [result (cli/parse-opts ["-h"] (options-with-help-option nil))
          root-key :__root__]
      (merge result
             (get spec root-key)
             {:command []}))

    (let [{:keys [options _description _handler _command arguments] :as command-form}
          (sub-command-parser spec args)

          result (cli/parse-opts arguments (options-with-help-option options))]

      (merge result (dissoc command-form
                            :options
                            :arguments)))))

(defn supervisor [{:keys [options arguments summary errors description handler command]
                          :as   parsed-command-form}]
  (let [help? (:help options)]

    (cond
      help? (select-keys parsed-command-form [:command :summary :description])
      errors (select-keys parsed-command-form [:command :errors])
      :else (handler arguments options))
    ))