#!/usr/bin/env bb
(require '[babashka.curl :as curl])
(require '[cheshire.core :as json])
; pretty-printing
(require '[clojure.pprint :refer [pprint]])

; read .env.edn file
(def env
  (try
    (read-string (slurp ".env.edn"))
    (catch Exception _
      {})))

; get the API key from the SHS_API_KEY environment variable or .env file
(def api-key (or (:api-key env) (System/getenv "SHS_API_KEY")))

(defn get-artist
  "Get an artist from the SecondHandSongs API, using the URI of the artist."
  [uri]
  (let [response (curl/get uri {:headers {"Accept" "application/json"
                                          "X-API-Key" api-key}})]
    (json/parse-string (:body response) true)))

(defn get-works
  "Get the works of an artist from the SecondHandSongs API, using the URI of the artist."
  [uri]
  (let [response (curl/get uri {:headers {"Accept" "application/json"
                                          "X-API-Key" api-key}})]
    (json/parse-string (:body response) true)))

(defn get-work
  "Get a work from the SecondHandSongs API, using the URI of the work."
  [uri]
  (let [response (curl/get uri {:headers {"Accept" "application/json"
                                          "X-API-Key" api-key}})]
    (json/parse-string (:body response) true)))

(defn pprintpipe [x]
  (pprint x)
  x)

(defn get-work-variants
  "Get the variants of a work from the SecondHandSongs API, using the URI of the work. 
   Possible keys are :derivedWorks (adaptations) or :versions (for covers)."
  [uri key]
  ; get the work
  (let [work (get-work uri)
        variants (key work)]
    ; get the works of the work
    ; map each :uri to a get-work call
    ; return the result of the map
    (->> variants
         (filter #(= "song" (:entitySubType %)))
         (map #(get-work (:uri %)))
         (map pprintpipe))))

(defn wrap-array
  "Wrap a value in an array if it is not already an array, 
   this is equivalent to the built-in function vec, but it 
   does not throw an exception if the value is already an array."
  [x]
  (if (vector? x)
    x
    [x]))


(defn get-work-performances
  "Get the performances of a work from the SecondHandSongs API, using the URI of the work. 
   Performances can be albums, singles, or other recordings.
   Possible keys are :derivedWorks (adaptations) or :versions (for covers)."
  [uri key]
  (->> (get-work uri)
       key
       wrap-array
       (filter #(= "performance" (:entityType %)))
       (map #(get-work (:uri %)))))

(defn enrich-releases
  "Enrich the releases of a work by fetching the work of each release.
   This also adds a :decade key to each work, which is the first 4 characters 
   of the :date key."
  [uri key] (->> (get-work-performances uri key)
                 (map #(:releases %))
     ; flatten the list of lists
                 (apply concat)
     ; get the :uri
                 (map #(:uri %))
                 (map #(get-work %))
     ; take the :date like "1984-04" and add a :decade key
                 (map #(assoc % :decade (subs (:date %) 0 4)))))


(->>
 (get-work-performances "https://secondhandsongs.com/work/1000" :derivedWorks)
 wrap-array)

(->>
 (get-work-performances "https://secondhandsongs.com/work/1000" :versions)
 wrap-array)

; (pprint (:derivedWorks (get-work "https://secondhandsongs.com/work/27517")))

(pprint
 (->> (get-work-variants "https://secondhandsongs.com/work/27517" :derivedWorks)
      ; filter for french songs
      (filter #(= "French" (:language %)))
      (map #(:original %))
      ; keep only title and performer
      (map #(select-keys % [:title :performer]))))

;; (def artist (get-artist "https://secondhandsongs.com/artist/123"))
;; (def works (get-works (:creditedWorks artist)))

;; ; filter out works that are not songs, then map over the remaining works to get the work
;; (def songs (->> works
;;                 (filter #(= "song" (:entitySubType %)))
;;                 (map #(get-work (:uri %)))))
;; ; pretty-print the songs
;; (pprint songs)
