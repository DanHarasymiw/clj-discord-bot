(ns clj-discord-bot.commands.sandbox
  (:use [clojail.core :only [sandbox]]
        [clojail.testers :only [secure-tester]])
  (:require [clj-discord.core :as discord]))

(def sb (sandbox secure-tester))

(defn result->string [object]
  (str "=> "
       (with-out-str (clojure.pprint/pprint object))))

(defn run-code
  "```clj <your clojure code> ``` - Runs your Clojure Code"
  [type data]
  (try
    (let [message (get data "content")
          code (-> (clojure.string/replace message #"```clojure|```clj" "")
                   (clojure.string/replace #"```" ""))
          writer (java.io.StringWriter.)
          result (sb (read-string code))]
      (discord/post-message (get data "channel_id")
                            (if (nil? result)
                              (do
                                (sb (read-string code) {#'*out* writer})
                                (result->string writer))
                              (result->string result))))
    (catch Exception e
      (discord/post-message (get data "channel_id")
                            (.getMessage e)))))
