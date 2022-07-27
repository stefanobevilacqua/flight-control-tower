(ns flight-control-tower.core-test
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [flight-control-tower.core :refer [read-raw-event read-raw-events
                                               to-flight-events
                                               handle-flight-event write-status
                                               flight-statuses-at]]))

(deftest read-raw-event-test
  (testing "Parse an event data from string"
    (is (= {:plane-id "F222"
            :plane-model "747"
            :origin "DUBLIN"
            :destination "LONDON"
            :event-type "Re-Fuel"
            :timestamp #inst "2021-03-29T10:00:00.000-00:00"
            :fuel-delta 200}
           (read-raw-event "F222 747 DUBLIN LONDON Re-Fuel 2021-03-29T10:00:00 200"))))
  (testing "Parse another event data from string"
    (is (= {:plane-id "F324"
            :plane-model "313"
            :origin "LONDON"
            :destination "NEWYORK"
            :event-type "Take-Off"
            :timestamp #inst "2021-03-29T12:00:00.000-00:00"
            :fuel-delta 0}(read-raw-event "F324 313 LONDON NEWYORK Take-Off 2021-03-29T12:00:00 0"))))
  (testing "Wrong event format is parsed as nil (to be discarded)"
    (is (= nil (read-raw-event "asdsdflsksk")))))

(deftest read-raw-events-test
  (testing "Reads multiple events and discard malformed ones"
    (is (= [{:plane-id "F222"
             :plane-model "747"
             :origin "DUBLIN"
             :destination "LONDON"
             :event-type "Re-Fuel"
             :timestamp #inst "2021-03-29T10:00:00.000-00:00"
             :fuel-delta 200}
            {:plane-id "F324"
            :plane-model "313"
            :origin "LONDON"
            :destination "NEWYORK"
            :event-type "Take-Off"
            :timestamp #inst "2021-03-29T12:00:00.000-00:00"
            :fuel-delta 0}]
           (read-raw-events "F222 747 DUBLIN LONDON Re-Fuel 2021-03-29T10:00:00 200
                                F324 313 LONDON NEWYORK Take-Off 2021-03-29T12:00:00 0"
                               "asdsdgge")))))

(deftest to-flight-events-test
  (testing "Updates flight event for the same plane-id and timestamp"
    (is (= [{:plane-id "F324"
             :plane-model "737"
             :origin "PARIS"
             :destination "CAIRO"
             :event-type "Land"
             :timestamp #inst "2021-03-29T12:00:00.000-00:00"
             :fuel-delta 200}]
           (to-flight-events [{:plane-id "F324"
                               :plane-model "313"
                               :origin "LONDON"
                               :destination "NEWYORK"
                               :event-type "Take-Off"
                               :timestamp #inst "2021-03-29T12:00:00.000-00:00"
                               :fuel-delta 0}
                              {:plane-id "F324"
                               :plane-model "737"
                               :origin "PARIS"
                               :destination "CAIRO"
                               :event-type "Land"
                               :timestamp #inst "2021-03-29T12:00:00.000-00:00"
                               :fuel-delta 200}]))))
  (testing "Removes flight event for the same plane-id and timestamp"
    (is (= []
           (to-flight-events [{:plane-id "F324"
                                 :plane-model "313"
                                 :origin "LONDON"
                                 :destination "NEWYORK"
                                 :event-type "Take-Off"
                                 :timestamp #inst "2021-03-29T12:00:00.000-00:00"
                                 :fuel-delta 0}
                                {:plane-id "F324"
                                 :timestamp #inst "2021-03-29T12:00:00.000-00:00"}])))))

(deftest handle-flight-event-test
  (testing "If event is Re-Fuel then flight-status should be Awaiting-Takeoff"
    (is (= "Awaiting-Takeoff"
           (:flight-status (handle-flight-event {} {:event-type "Re-Fuel"})))))
  (testing "If event is Take-Off then flight-status should be In-Flight"
    (is (= "In-Flight"
           (:flight-status (handle-flight-event {} {:event-type "Take-Off"})))))
  (testing "If event is Land then flight-status should be Landed"
    (is (= "Landed"
           (:flight-status (handle-flight-event {} {:event-type "Land"})))))
  (testing "If event is Unknown then flight-status remain the same"
    (is (= "Landed"
           (:flight-status (handle-flight-event {:flight-status "Landed"} {:event-type "UNKNOWN"})))))
  (testing "Fuel delta should be added to the fuel level"
    (is (= 210
           (:fuel-level (handle-flight-event {:fuel-level 333} {:event-type "Land" :fuel-delta -123}))))))

(deftest write-status-test
  (testing "Write <plane-id> <flight-status> <fuel-level> separated by single space"
    (is (= ["F123 Landed 0"
            "A444 Anything 987"]
           (map write-status [{:plane-id "F123" :flight-status "Landed" :fuel-level 0}
                              {:plane-id "A444" :flight-status "Anything" :fuel-level 987}])))))

(deftest flight-statuses-test
  (testing "Should only apply events occurring before or at given date"
    (is (= 80
           (:fuel-level (first (flight-statuses-at #inst "2021-03-15T10:00:00"
                                                   [{:plane-id "F123" :timestamp #inst "2021-03-15T09:00:00" :fuel-delta 100}
                                                    {:plane-id "F123" :timestamp #inst "2021-03-15T09:30:00" :fuel-delta -10}
                                                    {:plane-id "F123" :timestamp #inst "2021-03-15T10:00:00" :fuel-delta -10}
                                                    {:plane-id "F123" :timestamp #inst "2021-03-15T10:30:00" :fuel-delta -10}]))))))
  (testing "Should apply events in timestamp order"
    (is (= "Landed"
           (:flight-status (first (flight-statuses-at #inst "2021-03-15T10:00:00"
                                                      [{:plane-id "F123" :timestamp #inst "2021-03-15T10:00:00" :event-type "Land"}
                                                       {:plane-id "F123" :timestamp #inst "2021-03-15T09:00:00" :event-type "Re-Fuel"}
                                                       {:plane-id "F123" :timestamp #inst "2021-03-15T09:30:00" :event-type "Take-Off"}]))))))
  (testing "Should apply events for multiple planes"
    (is (= #{{:plane-id "G555" :flight-status "In-Flight"}
             {:plane-id "F123" :flight-status "Landed"}}
           (set (map #(select-keys % [:plane-id :flight-status])
                     (flight-statuses-at #inst "2021-03-15T10:00:00"
                                         [{:plane-id "G555" :timestamp #inst "2021-03-15T09:00:00" :event-type "Re-Fuel"}
                                          {:plane-id "F123" :timestamp #inst "2021-03-15T09:30:00" :event-type "Take-Off"}
                                          {:plane-id "G555" :timestamp #inst "2021-03-15T09:30:00" :event-type "Take-Off"}
                                          {:plane-id "F123" :timestamp #inst "2021-03-15T10:00:00" :event-type "Land"}])))))))

(defn -main []
  (run-tests 'flight-control-tower.core-test)
  )
