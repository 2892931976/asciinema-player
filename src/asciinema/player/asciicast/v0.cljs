(ns asciinema.player.asciicast.v0
  (:require [schema.core :as s]
            [asciinema.vt.screen :as screen]
            [asciinema.player.frames :as frames]
            [asciinema.player.screen :as ps]))

(def Fragment screen/Fragment) ; TODO decouple from vt

(def Diff {(s/optional-key :cursor) {(s/optional-key :x) s/Num
                                     (s/optional-key :y) s/Num
                                     (s/optional-key :visible) s/Bool}
           (s/optional-key :lines) {(s/named s/Keyword "line number") [Fragment]}})

(def DiffFrame [(s/one s/Num "delay") (s/one Diff "diff")])

(def AsciicastV0 [DiffFrame])

(s/defrecord LegacyScreen
    [cursor :- {:x s/Num
                :y s/Num
                :visible s/Bool}
     lines :- {s/Num [Fragment]}])

(defn fix-line-diff-keys [line-diff]
  (into {} (map (fn [[k v]] [(js/parseInt (name k) 10) v]) line-diff)))

(defn reduce-screen [screen diff]
  (let [diff (update diff :lines fix-line-diff-keys)]
    (merge-with merge screen diff)))

(defn build-v0-frames [diffs]
  (let [screen (map->LegacyScreen {:lines (sorted-map)
                                   :cursor {:x 0 :y 0 :visible true}})]
    (sequence (comp (frames/absolute-time-xf)
                    (frames/data-reductions-xf reduce-screen screen))
              diffs)))

(s/defn initialize-asciicast
  [asciicast :- AsciicastV0]
  (let [frame-0-lines (-> asciicast first last :lines)
        asciicast-width (->> frame-0-lines vals first (map #(count (first %))) (reduce +))
        asciicast-height (count frame-0-lines)]
    {:version 0
     :width asciicast-width
     :height asciicast-height
     :duration (reduce #(+ %1 (first %2)) 0 asciicast)
     :frames (build-v0-frames asciicast)}))

(extend-protocol ps/Screen
  LegacyScreen
  (lines [this]
    (-> this :lines vals vec))
  (cursor [this]
    (:cursor this)))
