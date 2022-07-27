(ns flight-control-tower.core
  (:import [java.util Date])
  (:require [clojure.instant :refer [read-instant-date]]
            [clojure.string]))

(defn read-raw-event
  "Parses a string containing event issued by crew, with this format:
  <plane-id> <plane-model> <origin> <destination> <event-type> <timestamp> <fuel-delta>
  with spaces between fields. Parses also removals, with this format:
  <plane-id> <timestamp>"
  [raw-event]
  (try
    (let [s (clojure.string/split raw-event #"\s+")]
      (if (> (count s) 2)
        (let [[plane-id
               plane-model
               origin
               destination
               event-type
               timestamp
               fuel-delta] s]
          {:plane-id plane-id
           :plane-model plane-model
           :origin origin
           :destination destination
           :event-type event-type
           :timestamp (read-instant-date timestamp)
           :fuel-delta (Integer/parseInt fuel-delta)}
          )
        (let [[plane-id timestamp] s]
          {:plane-id plane-id
           :timestamp (read-instant-date timestamp)})))
    (catch Exception _ nil)))

(defn read-raw-events
  "Read strings each containing one or more raw events (separated by
  newline characters)"
  [& raw-events]
  (->> (mapcat #(clojure.string/split % #"\n+") raw-events)
       (map clojure.string/trim)
       (map read-raw-event)
       (remove nil?)))

(defn to-flight-events
  "Converts raw events (containing also updates and removals) into
  real flight events (with no more corrections). Handles updates and
  removals."
  [raw-events]
  (->> raw-events
       (group-by (fn [e] [(:plane-id e) (:timestamp e)]))
       (map (fn [[[_] events]] (last events)))
       (filter #(not (nil? (:event-type %))))))

(defn handle-flight-event
  "Applies an event to the current state of the flight. It assumes
  that the event plane-id is equal to the status plane-id and that
  the event occurs in the appropriate flight state, so it doesn't
  perform validations such as Re-Fuel cannot occur if status is
  In-Flight"
  [{level :fuel-level status :flight-status}
   {plane-id :plane-id event-type :event-type fuel-delta :fuel-delta}]
  {:plane-id plane-id
   :flight-status (case event-type
                    "Re-Fuel" "Awaiting-Takeoff"
                    "Take-Off" "In-Flight"
                    "Land" "Landed"
                    status)
   :fuel-level (if fuel-delta (+ level fuel-delta) level)})

(defn- occurred-before-date?
  "Returns a predicate on an event which is true if the event
  occurs before or at the given date, false otherwise"
  [d]
  (fn [event] (not (pos? (compare (:timestamp event) d)))))

(defn flight-statuses-at
  "Returns flight statuses at a given point in time, by applying
  only the flight-events occurring before the given datetime. It assumes
  that the initial status is Awaiting-Takeoff with a fuel level of 0"
  [date flight-events]
  (let [initial-state {:flight-status "Awaiting-Takeoff"
                       :fuel-level 0}]
    (->> (group-by :plane-id flight-events)
         (map (fn [[_ events]] (->> (filter (occurred-before-date? date) events)
                                    (sort-by :timestamp)
                                    (reduce handle-flight-event initial-state)))))))

(defn flight-statuses
  "Returns current flight statuses"
  [flight-events]
  (flight-statuses-at (Date.) flight-events))

(defn write-status
  "Write status in the format <plane-id> <flight-status> <fuel-level>"
  [{id :plane-id
    fs :flight-status
    fl :fuel-level}]
  (str id " " fs " " fl))

;;; The application state is the collection of events
(defonce event-store (atom []))

(defn events
  "Takes a collection of strings containing events in raw format. Each
  string can in turn contain multiple events separated by newline characters"
  [& events]
  (swap! event-store #(concat % (mapcat read-raw-events events))))

(defn reset
  "Reset the state of the application by emptying the event store"
  []
  (swap! event-store (fn [_] [])))

(defn report-at
  "Calculates and outputs the flight statuses at a given point in time
  and in the requested format"
  [date]
  (println "----")
  (->> (to-flight-events @event-store)
       (flight-statuses-at date)
       (sort-by :plane-id)
       (map write-status)
       (clojure.string/join "\n")
       (println))
  (println "----"))

(defn report
  "Calculates and outputs the current flight statuses in the requested
  format"
  []
  (report-at (Date.)))

(comment  
  (events "F222 747 DUBLIN LONDON Re-Fuel 2021-03-29T10:00:00 200"
          "F551 747 PARIS LONDON Re-Fuel 2021-03-29T10:00:00 345"
          "F324 313 LONDON NEWYORK Take-Off 2021-03-29T12:00:00 0"
          "F123 747 LONDON CAIRO Re-Fuel 2021-03-29T10:00:00 428"
          "F123 747 LONDON CAIRO Take-Off 2021-03-29T12:00:00 0"
          "F551 747 PARIS LONDON Take-Off 2021-03-29T11:00:00 0"
          "F551 747 PARIS LONDON Land 2021-03-29T12:00:00 -120"
          "F123 747 LONDON CAIRO Land 2021-03-29T14:00:00 -324")

  (report-at #inst "2021-03-29T15:00:00")
  ;; (report)

  (events "F551 747 PARIS LONDON Land 2021-03-29T12:00:00 -300")

  (report)
  
  (events "F551 2021-03-29T12:00:00")

  (report)

  (reset)

  )
