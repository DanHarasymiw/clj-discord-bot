(ns clj-discord-bot.core
  (:gen-class)
  (:require [clj-discord.core :as discord]
            [clj-discord-bot.bot-interactions.bot-discussion :as bot-talk]
            [clj-discord-bot.common :as common]
            [clj-discord-bot.database :as db]
            [clj-discord-bot.commands.evangelize :as evangelize]
            [clj-discord-bot.commands.game_summon :as summon]
            [clj-discord-bot.commands.img-search :as img-search]
            [clj-discord-bot.commands.misc :as misc]
            [clj-discord-bot.commands.roll :as roll]
            [clj-discord-bot.commands.sandbox :as sandbox]
            [clj-http.client :as http-client]))

(defonce discord-token (.trim (slurp "discord_token.txt")))

(defn help [type data]
  (discord/answer-command data
                          (get data "content")
                          (str "Commands"
                               (apply str (map #(str "\n" (:doc (meta %)))
                                               [#'img-search/find-img
                                                #'roll/d20
                                                #'summon/game-summon
                                                #'summon/game-add
                                                #'summon/game-remove
                                                #'summon/game-list
                                                #'summon/add-steam-games])))))

(defn game-update [type data]
  (let [server-id 0 ;server id is not returned in message data, so ignore for now ... (get-in data ["guild_id"])
        user-id (get-in data ["user" "id"])
        game-id (get-in data ["game" "name"])]
    (when (and (some? server-id)
               (some? user-id)
               (some? game-id))
      (db/game-insertion server-id
                         user-id
                         game-id))))

(defn command-mux [type data]
  (let [message (get data "content")]
    (try

      (if (and (.startsWith message "(")
               (.endsWith message ")"))
        (let [command (->> message
                           (next)
                           (butlast)
                           (apply str))
              command-data (assoc data "content" command)]
          (cond
            (.startsWith command "steamadd") (summon/add-steam-games type command-data)
            (.startsWith command "gamelist") (summon/game-list type command-data)
            (.startsWith command "gameadd") (summon/game-add type command-data)
            (.startsWith command "gameremove") (summon/game-remove type command-data)
            (.equals "help" command) (help type command-data)
            (.equals "d20" command) (roll/d20 type command-data)
            (.startsWith command "summon ") (summon/game-summon type command-data)
            (.startsWith command "img ") (img-search/find-img type command-data)))
        (cond
          (.startsWith message "```clj") (sandbox/run-code type data)
          (.startsWith message "```clojure") (sandbox/run-code type data)
          (.contains message "clojure") (evangelize/get-propaganda type data)
          (re-find #"(?i)ghandi" message) (misc/gandhi-spellcheck type data)
          (re-find #"(?i)link" message) (misc/links-mentioned type data)
          (re-find #"(?i)rewriting it in Rust\?" message) (bot-talk/rewrite-it-in-rust-response type data)))
      (catch Exception e
        (println (.getMessage e) e)))))

(defn log-event [type data]
  (println "\nReceived: " type " -> " data))

(defn -main [& args]
  (db/init-db)
  (discord/connect {:token discord-token
                    :functions {"MESSAGE_CREATE" [command-mux]
                                "PRESENCE_UPDATE" [game-update]
                                "ALL_OTHER" [log-event]}
                    :rate-limit 1}))
