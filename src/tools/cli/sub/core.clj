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

                           ;; sub-command exists, but it not input
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
  (let [flatten-args (flatten args)]
    (if (or (empty? flatten-args) (some #{(first flatten-args)} ["help" "-h" "--help" ""]))
      (let [result (cli/parse-opts ["-h"] (options-with-help-option nil))
            root-key :__root__]
        (merge result
               (get spec root-key)
               {:command []}))

      (let [{:keys [options _description _handler _command arguments] :as command-form}
            (sub-command-parser spec flatten-args)

            result (cli/parse-opts arguments (options-with-help-option options))]

        (merge result (dissoc command-form
                              :options
                              :arguments))))))

(defn- command-usage [spec command]
  (let [root? (empty? command)
        alias (:alias (get-in spec (map keyword command)))
        {:keys [options description]} (if root?
                                        (:__root__ spec)
                                        (-> (if alias
                                              alias
                                              command)
                                            (sub-command-keys)
                                            (->> (get-in spec))))]
    (str "Usage: program "
         (when (seq command)
           (str (str/join " " command) " "))
         (:arguments description)
         (when (seq options)
           " [options] ")
         )))

(defn- sub-commands-description [spec command]
  (let [root? (empty? command)
        alias (:alias (get-in spec (map keyword command)))
        form (if root?
               (-> spec
                   (dissoc :__root__)
                   (->> (filter (fn [[_ {:keys [alias]}]] (not alias)))))

               (-> (if alias
                     alias
                     command)
                   (sub-command-keys)
                   vec
                   (conj :sub-command)
                   (->> (get-in spec))))]
    (->> form
         sort
         (map (fn [[sub-command {:keys [description]}]]
                (str (format "  %-18s" (name sub-command))
                     (:usage description))))
         (str/join \newline))))

(defn- usage-str [spec {:keys [command description summary] :as _command-form}]
  (let [{:keys [_arguments usage]} description]
    (->> [(command-usage spec command)
          ""
          (str "   " usage)
          (when-let [desc (str/trim (sub-commands-description spec command))]
            (when (seq desc)
              (let [prefix (if (seq command)
                             "Sub command"
                             "Command")]
                (str \newline prefix ":" \newline "  " desc \newline))))
          "Options"
          summary
          ""]
         (str/join \newline))))

(defn- supervisor [spec
                   {:keys [_help _errors] :as handlers}
                   {:keys [options arguments _summary errors description pass-if-no-arguments handler _command]
                    :as   parsed-command-form}]
  (let [help? (:help options)]

    (cond
      help? ((:help handlers) (usage-str spec parsed-command-form))
      errors ((:errors handlers) errors)

      (or (not pass-if-no-arguments)
          (and (:arguments description) (empty? arguments)))
      ((:errors handlers) (str "Arguments are required: " (:arguments description)))

      :else (handler arguments options))))

(defn parser-with-supervisor [spec {:keys [_help _errors] :as handlers} & args]
  (->> (parser spec (flatten args))
       (supervisor spec handlers)))