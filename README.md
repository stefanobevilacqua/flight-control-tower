# Flight Control Tower

This document describes the solution to the exercise described in [flight-control-tower.adoc](flight-control-tower.adoc) file

## Usage

The application can be used through the REPL, starting with:
```bash
clj
```

and requiring some functions:
```clojure
(require '[flight-control-tower.core :refer [events report report-at reset]])
```
Events can be issued with the `events` function
```clojure
(events "F222 747 DUBLIN LONDON Re-Fuel 2021-03-29T10:00:00 200"
        "F551 747 PARIS LONDON Re-Fuel 2021-03-29T10:00:00 345"
        "F324 313 LONDON NEWYORK Take-Off 2021-03-29T12:00:00 0"
        "F123 747 LONDON CAIRO Re-Fuel 2021-03-29T10:00:00 428"
        "F123 747 LONDON CAIRO Take-Off 2021-03-29T12:00:00 0"
        "F551 747 PARIS LONDON Take-Off 2021-03-29T11:00:00 0"
        "F551 747 PARIS LONDON Land 2021-03-29T12:00:00 -120"
        "F123 747 LONDON CAIRO Land 2021-03-29T14:00:00 -324")
```
The status of all flights in a particular point in time is printed with:
```clojure
(report-at #inst "2021-03-29T15:00:00")
```
or, for the most recent status:
```clojure
(report)
```
Events can be updated by issuing commands like this (as in the example in [flight-control-tower.adoc](flight-control-tower.adoc) file):
```clojure
(events "F551 747 PARIS LONDON Land 2021-03-29T12:00:00 -300")
(report)
```
And similarly for removals:
```clojure
(events "F551 2021-03-29T12:00:00")
(report)
```
To start over again (emptying the collection of events):
```clojure
(reset)
```

## Testing

Tests are in [core_test.clj](test/flight_control_tower/core_test.clj) and can be run from the commandline with:
```bash
clj -M:test
```
or from within the REPL with:
```clojure
(require '[flight-control-tower.core-test])
(clojure.test/run-tests 'flight-control-tower.core-test)
```
